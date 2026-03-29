package com.blog.infra.captcha.service;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
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
    public CaptchaGenerateVO generateCaptcha(String clientIp) {
        // 黑名单检查
        checkBlacklist(clientIp);

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
                .createTime(System.currentTimeMillis())
                .build();
        saveToRedis(CAPTCHA_DATA_PREFIX + captchaToken, data, CAPTCHA_EXPIRE_SECONDS);

        log.info("生成验证码，IP: {}", clientIp);

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
    public CaptchaVerifyVO verifyCaptcha(CaptchaVerifyRequest request, String clientIp) {
        // 黑名单检查
        if (isBlacklisted(clientIp)) {
            return CaptchaVerifyVO.builder().success(false).message(MSG_BLACKLISTED).build();
        }

        String key = CAPTCHA_DATA_PREFIX + request.getCaptchaToken();
        String json = redisService.get(key);

        if (!StringUtils.hasText(json)) {
            return CaptchaVerifyVO.builder().success(false).message(MSG_CAPTCHA_EXPIRED).build();
        }

        // 获取后立即删除（一次性使用）
        redisService.delete(key);

        CaptchaData data = readJson(json, CaptchaData.class);
        if (data == null) {
            return CaptchaVerifyVO.builder().success(false).message(MSG_VERIFY_FAIL).build();
        }

        // IP 校验
        if (!clientIp.equals(data.getClientIp())) {
            log.warn("验证码 IP 不匹配，存储: {}, 请求: {}", data.getClientIp(), clientIp);
            recordFailure(clientIp);
            return CaptchaVerifyVO.builder().success(false).message(MSG_VERIFY_FAIL).build();
        }

        // 轨迹分析
        if (!trackAnalyzer.validate(request.getSlideTrack())) {
            log.warn("轨迹异常，token: {}, IP: {}, 轨迹点数: {}", request.getCaptchaToken(), clientIp,
                    request.getSlideTrack() != null ? request.getSlideTrack().size() : 0);
            recordFailure(clientIp);
            return CaptchaVerifyVO.builder().success(false).message(MSG_VERIFY_FAIL).build();
        }

        // 位置校验
        int offset = Math.abs(request.getSliderPosition() - data.getTargetPosition());
        if (offset > ACCURACY_THRESHOLD) {
            log.warn("位置偏差过大: {}px, token: {}", offset, request.getCaptchaToken());
            recordFailure(clientIp);
            return CaptchaVerifyVO.builder().success(false).message(MSG_VERIFY_FAIL).build();
        }

        // 验证成功 → 生成 verifyToken
        long createTime = System.currentTimeMillis();
        TokenGenerator.TokenResult tokenResult = tokenGenerator.generateToken(clientIp, createTime);

        VerifyTokenData tokenData = VerifyTokenData.builder()
                .clientIp(clientIp)
                .createTime(createTime)
                .build();
        saveToRedis(VERIFY_TOKEN_PREFIX + tokenResult.tokenId(), tokenData, VERIFY_TOKEN_EXPIRE_SECONDS);

        log.info("验证码校验成功，IP: {}", clientIp);

        return CaptchaVerifyVO.builder()
                .success(true)
                .verifyToken(tokenResult.fullToken())
                .message("验证通过")
                .build();
    }

    // ===== 刷新验证码 =====

    @Override
    public CaptchaGenerateVO refreshCaptcha(String oldToken, String clientIp) {
        if (StringUtils.hasText(oldToken)) {
            redisService.delete(CAPTCHA_DATA_PREFIX + oldToken);
        }
        return generateCaptcha(clientIp);
    }

    // ===== 校验 Verify_Token =====

    @Override
    public boolean validateVerifyToken(String verifyToken, String clientIp) {
        if (!StringUtils.hasText(verifyToken)) {
            return false;
        }

        // 校验 HMAC 签名
        String tokenId = tokenGenerator.extractTokenId(verifyToken);
        if (tokenId == null) {
            return false;
        }

        String key = VERIFY_TOKEN_PREFIX + tokenId;
        String json = redisService.get(key);
        if (!StringUtils.hasText(json)) {
            return false;
        }

        // 一次性使用，立即删除
        redisService.delete(key);

        VerifyTokenData tokenData = readJson(json, VerifyTokenData.class);
        if (tokenData == null) {
            return false;
        }

        // 校验签名完整性
        if (!tokenGenerator.validateSignature(verifyToken, tokenData.getClientIp(), tokenData.getCreateTime())) {
            log.warn("Verify_Token 签名校验失败");
            return false;
        }

        // 校验 IP 一致性
        if (!clientIp.equals(tokenData.getClientIp())) {
            log.warn("Verify_Token IP 不匹配，存储: {}, 请求: {}", tokenData.getClientIp(), clientIp);
            return false;
        }

        return true;
    }

    @Override
    public void validateVerifyTokenOrThrow(String verifyToken, String clientIp) {
        if (!validateVerifyToken(verifyToken, clientIp)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "验证令牌无效或已过期");
        }
    }

    // ===== 黑名单 =====

    private void checkBlacklist(String clientIp) {
        if (isBlacklisted(clientIp)) {
            throw new BusinessException(ResultCode.RATE_LIMIT, MSG_BLACKLISTED);
        }
    }

    private boolean isBlacklisted(String clientIp) {
        if (shouldBypassForDev(clientIp)) {
            return false;
        }
        return Boolean.TRUE.equals(redisService.hasKey(BLACKLIST_PREFIX + clientIp));
    }

    private void recordFailure(String clientIp) {
        if (shouldBypassForDev(clientIp)) {
            return;
        }
        long count = redisService.incrementWithExpire(FAIL_COUNT_PREFIX + clientIp, BLACKLIST_WINDOW_SECONDS);
        if (count >= BLACKLIST_THRESHOLD) {
            String reason = "验证码验证失败次数过多（" + count + "次）";
            redisService.set(BLACKLIST_PREFIX + clientIp, reason, BLACKLIST_DURATION_SECONDS, TimeUnit.SECONDS);
            redisService.delete(FAIL_COUNT_PREFIX + clientIp);
            log.warn("IP {} 已加入黑名单 {}s", clientIp, BLACKLIST_DURATION_SECONDS);
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
        for (int i = 0; i < 2; i++) {
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
