package com.blog.infra.captcha.service;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.DesensitizeUtil;
import com.blog.infra.captcha.CaptchaProperties;
import com.blog.infra.captcha.enums.PuzzleShape;
import com.blog.infra.captcha.model.*;
import com.blog.infra.redis.RedisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.image.BufferedImage;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.blog.infra.captcha.constant.CaptchaConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    private final CaptchaImageService imageService;
    private final TrackAnalyzer trackAnalyzer;
    private final TokenGenerator tokenGenerator;
    private final CaptchaProperties captchaProperties;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    // ===== 统一模糊错误信息 =====
    private static final String MSG_VERIFY_FAIL = "验证失败，请重试";
    private static final String MSG_CAPTCHA_EXPIRED = "验证码已过期，请刷新";
    private static final String MSG_BLACKLISTED = "请求过于频繁，请稍后重试";

    /** 本地开发 IP 白名单，跳过黑名单和失败计数 */
    private static final java.util.Set<String> LOCAL_IP_WHITELIST = java.util.Set.of(
            "127.0.0.1", "0:0:0:0:0:0:0:1", "::1", "localhost"
    );

    @Value("${blog.security.dev-bypass-enabled:false}")
    private boolean devBypassEnabled;

    // ===== 生成验证码 =====

    @Override
    public CaptchaGenerateVO generateCaptcha(String scene, CaptchaRiskContext context) {
        String clientIp = safeClientIp(context);
        String normalizedScene = normalizeScene(scene);
        // 黑名单检查
        checkBlacklist(context);

        String captchaToken = UUID.randomUUID().toString();
        PuzzleShape shape = PuzzleShape.random();
        BufferedImage background = imageService.getRandomBackground();

        // 随机拼图位置
        int minX = PUZZLE_WIDTH + 10;
        int maxX = IMAGE_WIDTH - PUZZLE_WIDTH - 10;
        int targetX = ThreadLocalRandom.current().nextInt(minX, maxX + 1);

        int minY = 10;
        int maxY = IMAGE_HEIGHT - PUZZLE_HEIGHT - 10;
        int puzzleY = ThreadLocalRandom.current().nextInt(minY, maxY + 1);

        // 绘制 2 个干扰缺口
        drawDecoyCutouts(background, targetX, puzzleY, shape);

        // 生成拼图块（同时在背景图上绘制正确缺口）
        String puzzleImageBase64 = imageService.generatePuzzleImage(background, targetX, puzzleY, shape);

        // 添加噪点
        imageService.addNoise(background);

        // 背景图编码为 JPEG（减小体积）
        String backgroundImageBase64 = imageService.imageToBase64Jpeg(background);

        // 存储到 Redis
        CaptchaData data = CaptchaData.builder()
                .targetPosition(targetX)
                .puzzleY(puzzleY)
                .puzzleShape(shape.name())
                .clientIp(clientIp)
                .scene(normalizedScene)
                .createTime(System.currentTimeMillis())
                .build();
        saveToRedis(CAPTCHA_DATA_PREFIX + captchaToken, data, CAPTCHA_EXPIRE_SECONDS);

        log.info("生成验证码，IP: {}", DesensitizeUtil.ip(clientIp));

        return CaptchaGenerateVO.builder()
                .captchaToken(captchaToken)
                .backgroundImage(backgroundImageBase64)
                .puzzleImage(puzzleImageBase64)
                .puzzleY(puzzleY)
                .puzzleWidth(PUZZLE_WIDTH)
                .puzzleHeight(PUZZLE_HEIGHT)
                .build();
    }

    // ===== 验证滑块 =====

    @Override
    public CaptchaVerifyVO verifyCaptcha(CaptchaVerifyRequest request, CaptchaRiskContext context) {
        String clientIp = safeClientIp(context);
        // 黑名单检查
        if (isBlacklisted(context)) {
            return CaptchaVerifyVO.builder().success(false).message(MSG_BLACKLISTED).build();
        }

        String key = CAPTCHA_DATA_PREFIX + request.getCaptchaToken();
        String json = redisService.get(key);

        if (!StringUtils.hasText(json)) {
            Long ttlSeconds = redisService.getTtl(key, TimeUnit.SECONDS);
            log.warn("验证码数据不存在或已过期，token: {}, scene: {}, IP: {}, ttl: {}",
                    maskDimension(request.getCaptchaToken()), normalizeScene(request.getScene()), DesensitizeUtil.ip(clientIp), ttlSeconds);
            return CaptchaVerifyVO.builder().success(false).message(MSG_CAPTCHA_EXPIRED).build();
        }

        CaptchaData data = readJson(json, CaptchaData.class);
        if (data == null) {
            redisService.delete(key);
            return CaptchaVerifyVO.builder().success(false).message(MSG_VERIFY_FAIL).build();
        }

        String requestScene = normalizeScene(request.getScene());
        if (!requestScene.equals(data.getScene())) {
            log.warn("验证码场景不匹配，token: {}, scene: {}", maskDimension(request.getCaptchaToken()), requestScene);
            recordFailure(context);
            redisService.delete(key);
            return CaptchaVerifyVO.builder().success(false).message(MSG_VERIFY_FAIL).build();
        }

        if (!checkSolveDuration(data)) {
            log.warn("验证提交过快，token: {}, IP: {}", maskDimension(request.getCaptchaToken()), DesensitizeUtil.ip(clientIp));
            recordFailure(context);
            redisService.delete(key);
            return CaptchaVerifyVO.builder().success(false).message(MSG_VERIFY_FAIL).build();
        }

        if (!checkTrackAndSliderConsistency(request)) {
            log.warn("轨迹与滑块位置不一致，token: {}, IP: {}", maskDimension(request.getCaptchaToken()), DesensitizeUtil.ip(clientIp));
            recordFailure(context);
            redisService.delete(key);
            return CaptchaVerifyVO.builder().success(false).message(MSG_VERIFY_FAIL).build();
        }

        // IP 校验
        if (!clientIp.equals(data.getClientIp())) {
            log.warn("验证码 IP 不匹配，存储: {}, 请求: {}", DesensitizeUtil.ip(data.getClientIp()), DesensitizeUtil.ip(clientIp));
            recordFailure(context);
            redisService.delete(key);
            return CaptchaVerifyVO.builder().success(false).message(MSG_VERIFY_FAIL).build();
        }

        // 轨迹分析
        if (!trackAnalyzer.validate(request.getSlideTrack())) {
            log.warn("轨迹异常，token: {}, IP: {}, 轨迹点数: {}", maskDimension(request.getCaptchaToken()), DesensitizeUtil.ip(clientIp),
                    request.getSlideTrack() != null ? request.getSlideTrack().size() : 0);
            recordFailure(context);
            redisService.delete(key);
            return CaptchaVerifyVO.builder().success(false).message(MSG_VERIFY_FAIL).build();
        }

        // 位置校验
        int offset = Math.abs(request.getSliderPosition() - data.getTargetPosition());
        if (offset > ACCURACY_THRESHOLD) {
            log.warn("位置偏差过大: {}px, token: {}", offset, maskDimension(request.getCaptchaToken()));
            recordFailure(context);
            redisService.delete(key);
            return CaptchaVerifyVO.builder().success(false).message(MSG_VERIFY_FAIL).build();
        }

        if (!redisService.deleteIfValueEquals(key, json)) {
            Long ttlSeconds = redisService.getTtl(key, TimeUnit.SECONDS);
            log.warn("验证码数据消费失败，token: {}, scene: {}, IP: {}, ttl: {}",
                    maskDimension(request.getCaptchaToken()), requestScene, DesensitizeUtil.ip(clientIp), ttlSeconds);
            return CaptchaVerifyVO.builder().success(false).message(MSG_CAPTCHA_EXPIRED).build();
        }

        // 验证成功 → 生成 verifyToken
        long createTime = System.currentTimeMillis();
        TokenGenerator.TokenResult tokenResult = tokenGenerator.generateToken(clientIp, requestScene, createTime);

        VerifyTokenData tokenData = VerifyTokenData.builder()
                .clientIp(clientIp)
                .scene(requestScene)
                .createTime(createTime)
                .build();
        saveToRedis(VERIFY_TOKEN_PREFIX + tokenResult.tokenId(), tokenData, getVerifyTokenExpireSeconds());

        clearFailureCounters(context);

        log.info("验证码校验成功，IP: {}", DesensitizeUtil.ip(clientIp));

        return CaptchaVerifyVO.builder()
                .success(true)
                .verifyToken(tokenResult.fullToken())
                .message("验证通过")
                .build();
    }

    // ===== 刷新验证码 =====

    @Override
    public CaptchaGenerateVO refreshCaptcha(String oldToken, String scene, CaptchaRiskContext context) {
        if (StringUtils.hasText(oldToken)) {
            redisService.delete(CAPTCHA_DATA_PREFIX + oldToken);
        }
        return generateCaptcha(scene, context);
    }

    // ===== 校验 Verify_Token =====

    @Override
    public boolean validateVerifyToken(String verifyToken, String clientIp, String scene) {
        if (!StringUtils.hasText(verifyToken)) {
            return false;
        }
        String normalizedScene = normalizeScene(scene);

        // 校验 HMAC 签名
        String tokenId = tokenGenerator.extractTokenId(verifyToken);
        if (tokenId == null) {
            return false;
        }

        String key = VERIFY_TOKEN_PREFIX + tokenId;
        String json = redisService.getAndDelete(key);
        if (!StringUtils.hasText(json)) {
            return false;
        }

        VerifyTokenData tokenData = readJson(json, VerifyTokenData.class);
        if (tokenData == null) {
            return false;
        }

        // 校验签名完整性
        if (!tokenGenerator.validateSignature(verifyToken, tokenData.getClientIp(), tokenData.getScene(), tokenData.getCreateTime())) {
            log.warn("Verify_Token 签名校验失败");
            return false;
        }

        // 校验 IP 一致性
        if (!clientIp.equals(tokenData.getClientIp())) {
            log.warn("Verify_Token IP 不匹配，存储: {}, 请求: {}", DesensitizeUtil.ip(tokenData.getClientIp()), DesensitizeUtil.ip(clientIp));
            return false;
        }

        if (!normalizedScene.equals(tokenData.getScene())) {
            log.warn("Verify_Token 场景不匹配，存储: {}, 请求: {}", tokenData.getScene(), normalizedScene);
            return false;
        }

        return true;
    }

    @Override
    public void validateVerifyTokenOrThrow(String verifyToken, String clientIp, String scene) {
        if (!validateVerifyToken(verifyToken, clientIp, scene)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "验证令牌无效或已过期");
        }
    }

    // ===== 黑名单 =====

    private void checkBlacklist(CaptchaRiskContext context) {
        if (isBlacklisted(context)) {
            throw new BusinessException(ResultCode.RATE_LIMIT, MSG_BLACKLISTED);
        }
    }

    private boolean isBlacklisted(CaptchaRiskContext context) {
        String clientIp = safeClientIp(context);
        if (shouldBypassForDev(clientIp)) {
            return false;
        }

        if (Boolean.TRUE.equals(redisService.hasKey(BLACKLIST_PREFIX + clientIp))
                || Boolean.TRUE.equals(redisService.hasKey(BLACKLIST_IP_PREFIX + clientIp))) {
            return true;
        }

        String deviceFingerprint = normalizeDimensionValue(context != null ? context.getDeviceFingerprint() : null);
        if (deviceFingerprint != null && Boolean.TRUE.equals(redisService.hasKey(BLACKLIST_DEVICE_PREFIX + deviceFingerprint))) {
            return true;
        }

        String sessionId = normalizeDimensionValue(context != null ? context.getSessionId() : null);
        return sessionId != null && Boolean.TRUE.equals(redisService.hasKey(BLACKLIST_SESSION_PREFIX + sessionId));
    }

    private void recordFailure(CaptchaRiskContext context) {
        String clientIp = safeClientIp(context);
        if (shouldBypassForDev(clientIp)) {
            return;
        }

        increaseAndMaybeBlacklist(
                FAIL_COUNT_PREFIX + clientIp,
                BLACKLIST_PREFIX + clientIp,
                BLACKLIST_THRESHOLD,
                "legacy-ip",
                clientIp
        );

        increaseAndMaybeBlacklist(
                FAIL_COUNT_IP_PREFIX + clientIp,
                BLACKLIST_IP_PREFIX + clientIp,
                BLACKLIST_THRESHOLD,
                "ip",
                clientIp
        );

        String deviceFingerprint = normalizeDimensionValue(context != null ? context.getDeviceFingerprint() : null);
        if (deviceFingerprint != null) {
            increaseAndMaybeBlacklist(
                    FAIL_COUNT_DEVICE_PREFIX + deviceFingerprint,
                    BLACKLIST_DEVICE_PREFIX + deviceFingerprint,
                    BLACKLIST_DEVICE_THRESHOLD,
                    "device",
                    maskDimension(deviceFingerprint)
            );
        }

        String sessionId = normalizeDimensionValue(context != null ? context.getSessionId() : null);
        if (sessionId != null) {
            increaseAndMaybeBlacklist(
                    FAIL_COUNT_SESSION_PREFIX + sessionId,
                    BLACKLIST_SESSION_PREFIX + sessionId,
                    BLACKLIST_SESSION_THRESHOLD,
                    "session",
                    maskDimension(sessionId)
            );
        }
    }

    private void clearFailureCounters(CaptchaRiskContext context) {
        String clientIp = safeClientIp(context);
        redisService.delete(FAIL_COUNT_PREFIX + clientIp);
        redisService.delete(FAIL_COUNT_IP_PREFIX + clientIp);

        String deviceFingerprint = normalizeDimensionValue(context != null ? context.getDeviceFingerprint() : null);
        if (deviceFingerprint != null) {
            redisService.delete(FAIL_COUNT_DEVICE_PREFIX + deviceFingerprint);
        }

        String sessionId = normalizeDimensionValue(context != null ? context.getSessionId() : null);
        if (sessionId != null) {
            redisService.delete(FAIL_COUNT_SESSION_PREFIX + sessionId);
        }
    }

    private String safeClientIp(CaptchaRiskContext context) {
        if (context == null || !StringUtils.hasText(context.getClientIp())) {
            return "unknown";
        }
        return context.getClientIp();
    }

    private String normalizeScene(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "default";
        }
        return raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private int getVerifyTokenExpireSeconds() {
        int configured = captchaProperties.getVerifyTokenExpireSeconds();
        return configured > 0 ? configured : VERIFY_TOKEN_EXPIRE_SECONDS;
    }

    private String normalizeDimensionValue(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String maskDimension(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        String normalized = value.trim();
        if (normalized.length() <= 8) {
            return normalized;
        }
        return normalized.substring(0, 8);
    }

    private void increaseAndMaybeBlacklist(String failKey,
                                           String blacklistKey,
                                           int threshold,
                                           String dimensionType,
                                           String dimensionValue) {
        long count = redisService.incrementWithExpire(failKey, BLACKLIST_WINDOW_SECONDS);
        if (count >= threshold) {
            String reason = "验证码验证失败次数过多（" + dimensionType + "=" + dimensionValue + "，" + count + "次）";
            redisService.set(blacklistKey, reason, BLACKLIST_DURATION_SECONDS, TimeUnit.SECONDS);
            redisService.delete(failKey);
            log.warn("验证码黑名单触发: type={}, value={}, threshold={}, duration={}s",
                    dimensionType, dimensionValue, threshold, BLACKLIST_DURATION_SECONDS);
        }
    }

    private boolean shouldBypassForDev(String clientIp) {
        if (!devBypassEnabled || !LOCAL_IP_WHITELIST.contains(clientIp)) {
            return false;
        }

        String[] profiles = environment.getActiveProfiles();
        if (profiles == null || profiles.length == 0) {
            return false;
        }

        for (String profile : profiles) {
            if ("dev".equalsIgnoreCase(profile)
                    || "test".equalsIgnoreCase(profile)
                    || "local".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    // ===== 干扰缺口 =====

    private void drawDecoyCutouts(BufferedImage bg, int targetX, int targetY, PuzzleShape correctShape) {
        int minDist = PUZZLE_WIDTH + 20;
        for (int i = 0; i < 3; i++) {
            PuzzleShape decoyShape = PuzzleShape.randomExcept(correctShape);
            int dx, dy;
            int attempts = 0;
            do {
                dx = ThreadLocalRandom.current().nextInt(10, IMAGE_WIDTH - PUZZLE_WIDTH - 10);
                dy = ThreadLocalRandom.current().nextInt(10, IMAGE_HEIGHT - PUZZLE_HEIGHT - 10);
                attempts++;
            } while ((Math.abs(dx - targetX) < minDist || Math.abs(dy - targetY) < minDist) && attempts < 50);
            imageService.drawDecoyCutout(bg, dx, dy, decoyShape);
        }
    }

    private boolean checkSolveDuration(CaptchaData data) {
        long now = System.currentTimeMillis();
        long duration = now - data.getCreateTime();
        return duration >= MIN_SOLVE_TIME_MS;
    }

    private boolean checkTrackAndSliderConsistency(CaptchaVerifyRequest request) {
        if (request.getSlideTrack() == null || request.getSlideTrack().isEmpty() || request.getSliderPosition() == null) {
            return false;
        }

        TrackPoint first = request.getSlideTrack().get(0);
        TrackPoint last = request.getSlideTrack().get(request.getSlideTrack().size() - 1);
        if (Math.abs(first.getX()) > TRACK_START_X_TOLERANCE) {
            return false;
        }

        return Math.abs(last.getX() - request.getSliderPosition()) <= TRACK_END_POSITION_TOLERANCE;
    }

    // ===== JSON 工具 =====

    private <T> void saveToRedis(String key, T data, int expireSeconds) {
        try {
            redisService.set(key, objectMapper.writeValueAsString(data), expireSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    private <T> T readJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON 反序列化失败", e);
            return null;
        }
    }
}
