package com.blog.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.PageParamUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.infra.redis.RedisService;
import com.blog.infra.security.audit.SecurityEventAuditService;
import com.blog.system.entity.BlacklistEntry;
import com.blog.system.entity.User;
import com.blog.system.mapper.BlacklistMapper;
import com.blog.system.mapper.RememberTokenMapper;
import com.blog.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 安全风控处置服务（IP 封禁）
 */
@Service
@RequiredArgsConstructor
public class SecurityRiskControlService {

    private static final String IP_BLOCK_KEY_PREFIX = "security:risk:ip:block:";
    private static final String USER_BLOCK_KEY_PREFIX = "security:risk:user:block:";
    private static final String BLOCK_TYPE_IP = "IP";
    private static final String BLOCK_TYPE_USER = "USER";
    private static final int DEFAULT_BLOCK_MINUTES = 24 * 60;
    private static final int MIN_BLOCK_MINUTES = 1;
    private static final int MAX_BLOCK_MINUTES = 30 * 24 * 60;

    private final RedisService redisService;
    private final SecurityEventAuditService securityEventAuditService;
    private final BlacklistMapper blacklistMapper;
    private final UserMapper userMapper;
    private final RememberTokenMapper rememberTokenMapper;

    public IpBlockStatus blockIp(String rawIp, Integer durationMinutes, String reason, boolean overwrite) {
        Integer safeDurationMinutes = durationMinutes == null ? DEFAULT_BLOCK_MINUTES : normalizeDurationMinutes(durationMinutes);
        return blockIp(rawIp, safeDurationMinutes, false, reason, overwrite);
    }

    public IpBlockStatus blockIp(String rawIp,
                                 Integer durationMinutes,
                                 boolean permanent,
                                 String reason,
                                 boolean overwrite) {
        String ip = normalizeIpOrThrow(rawIp);
        Integer safeDurationMinutes = permanent ? null : normalizeDurationMinutes(durationMinutes);

        BlacklistEntry active = findActiveEntry(BLOCK_TYPE_IP, ip);
        if (!overwrite && active != null) {
            return toIpStatus(active);
        }

        BlacklistEntry entry = active != null ? active : findLatestEntry(BLOCK_TYPE_IP, ip);
        if (entry == null) {
            entry = new BlacklistEntry();
            entry.setBlockType(BLOCK_TYPE_IP);
            entry.setTargetValue(ip);
            entry.setIpAddress(ip);
        }

        LocalDateTime now = LocalDateTime.now();
        entry.setReason(normalizeReason(reason));
        entry.setSubject(ip);
        entry.setExpireTime(toExpireTime(safeDurationMinutes));
        if (entry.getCreateTime() == null) {
            entry.setCreateTime(now);
        }
        entry.setUpdateTime(now);

        saveEntry(entry);
        syncRedisForEntry(entry);
        return toIpStatus(entry);
    }

    public UserBlockStatus blockUser(Long userId,
                                     Integer durationMinutes,
                                     boolean permanent,
                                     String reason,
                                     boolean overwrite) {
        Long safeUserId = normalizeUserId(userId);
        Integer safeDurationMinutes = permanent ? null : normalizeDurationMinutes(durationMinutes);
        String targetValue = String.valueOf(safeUserId);

        BlacklistEntry active = findActiveEntry(BLOCK_TYPE_USER, targetValue);
        if (!overwrite && active != null) {
            return toUserStatus(active);
        }

        User user = userMapper.selectById(safeUserId);
        String subject = user == null
                ? "UID:" + safeUserId
                : (StringUtils.hasText(user.getEmail()) ? user.getEmail() : "UID:" + safeUserId);

        BlacklistEntry entry = active != null ? active : findLatestEntry(BLOCK_TYPE_USER, targetValue);
        if (entry == null) {
            entry = new BlacklistEntry();
            entry.setBlockType(BLOCK_TYPE_USER);
            entry.setTargetValue(targetValue);
            entry.setUserId(safeUserId);
            entry.setIpAddress("USER#" + safeUserId);
        }

        LocalDateTime now = LocalDateTime.now();
        entry.setReason(normalizeReason(reason));
        entry.setSubject(subject);
        if (!StringUtils.hasText(entry.getIpAddress())) {
            entry.setIpAddress("USER#" + safeUserId);
        }
        entry.setExpireTime(toExpireTime(safeDurationMinutes));
        if (entry.getCreateTime() == null) {
            entry.setCreateTime(now);
        }
        entry.setUpdateTime(now);

        saveEntry(entry);
        syncRedisForEntry(entry);
        revokeUserSessions(safeUserId);
        return toUserStatus(entry);
    }

    public boolean unblockById(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "黑名单ID无效");
        }
        BlacklistEntry entry = blacklistMapper.selectById(id);
        if (entry == null) {
            return false;
        }
        int deleted = blacklistMapper.deleteById(id);
        if (deleted > 0) {
            deleteRedisForEntry(entry);
            return true;
        }
        return false;
    }

    public boolean unblockUser(Long userId) {
        Long safeUserId = normalizeUserId(userId);
        String targetValue = String.valueOf(safeUserId);
        int deleted = blacklistMapper.delete(new LambdaQueryWrapper<BlacklistEntry>()
                .eq(BlacklistEntry::getBlockType, BLOCK_TYPE_USER)
                .eq(BlacklistEntry::getTargetValue, targetValue));
        redisService.delete(userBlockKey(safeUserId));
        return deleted > 0;
    }

    public IPage<BlacklistItemVO> pageBlacklist(int pageNum,
                                                int pageSize,
                                                String blockType,
                                                String keyword,
                                                String status) {
        PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
        Page<BlacklistEntry> page = new Page<>(pageParams.pageNum(), pageParams.pageSize());
        LocalDateTime now = LocalDateTime.now();

        LambdaQueryWrapper<BlacklistEntry> wrapper = new LambdaQueryWrapper<BlacklistEntry>()
                .eq(StringUtils.hasText(blockType), BlacklistEntry::getBlockType, blockType == null ? null : blockType.trim().toUpperCase())
                .and(StringUtils.hasText(keyword), q -> q
                        .like(BlacklistEntry::getTargetValue, keyword.trim())
                        .or()
                        .like(BlacklistEntry::getSubject, keyword.trim()))
                .orderByDesc(BlacklistEntry::getUpdateTime)
                .orderByDesc(BlacklistEntry::getCreateTime);

        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toLowerCase() : "all";
        if ("active".equals(normalizedStatus)) {
            wrapper.and(q -> q.isNull(BlacklistEntry::getExpireTime).or().gt(BlacklistEntry::getExpireTime, now));
        } else if ("expired".equals(normalizedStatus)) {
            wrapper.isNotNull(BlacklistEntry::getExpireTime).le(BlacklistEntry::getExpireTime, now);
        }

        IPage<BlacklistEntry> result = blacklistMapper.selectPage(page, wrapper);
        Page<BlacklistItemVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toBlacklistItem).toList());
        return voPage;
    }

    public boolean unblockIp(String rawIp) {
        String ip = normalizeIpOrThrow(rawIp);
        int deleted = blacklistMapper.delete(new LambdaQueryWrapper<BlacklistEntry>()
                .eq(BlacklistEntry::getBlockType, BLOCK_TYPE_IP)
                .eq(BlacklistEntry::getTargetValue, ip));
        redisService.delete(ipBlockKey(ip));
        return deleted > 0;
    }

    public IpBlockStatus getIpBlockStatus(String rawIp) {
        String ip = normalizeIpOrThrow(rawIp);

        BlacklistEntry active = findActiveEntry(BLOCK_TYPE_IP, ip);
        if (active != null) {
            syncRedisForEntry(active);
            return toIpStatus(active);
        }

        String key = ipBlockKey(ip);
        boolean blockedInRedis = Boolean.TRUE.equals(redisService.hasKey(key));
        if (!blockedInRedis) {
            return new IpBlockStatus(ip, false, null, 0, false);
        }

        String reason = redisService.get(key);
        Long ttl = redisService.getTtl(key, TimeUnit.SECONDS);
        boolean permanent = ttl != null && ttl < 0;
        long remainingSeconds = permanent ? -1 : (ttl == null ? 0 : Math.max(ttl, 0));
        return new IpBlockStatus(ip, true, reason, remainingSeconds, permanent);
    }

    public UserBlockStatus getUserBlockStatus(Long userId) {
        Long safeUserId = normalizeUserId(userId);
        String targetValue = String.valueOf(safeUserId);

        BlacklistEntry active = findActiveEntry(BLOCK_TYPE_USER, targetValue);
        if (active != null) {
            syncRedisForEntry(active);
            return toUserStatus(active);
        }

        String key = userBlockKey(safeUserId);
        boolean blockedInRedis = Boolean.TRUE.equals(redisService.hasKey(key));
        if (!blockedInRedis) {
            return new UserBlockStatus(safeUserId, false, null, 0, false, null);
        }

        String reason = redisService.get(key);
        Long ttl = redisService.getTtl(key, TimeUnit.SECONDS);
        boolean permanent = ttl != null && ttl < 0;
        long remainingSeconds = permanent ? -1 : (ttl == null ? 0 : Math.max(ttl, 0));
        return new UserBlockStatus(safeUserId, true, reason, remainingSeconds, permanent, null);
    }

    public void assertIpAllowed(String rawIp, String scene, String userAgent) {
        String ip = normalizeIp(rawIp);
        if (!StringUtils.hasText(ip)) {
            return;
        }

        IpBlockStatus status = getIpBlockStatus(ip);
        if (!status.blocked()) {
            return;
        }

        String description = String.format(
                "IP 黑名单命中：scene=%s, ip=%s, remain=%ds, reason=%s",
                StringUtils.hasText(scene) ? scene : "unknown",
                ip,
                status.remainingSeconds(),
                StringUtils.hasText(status.reason()) ? status.reason() : "manual_block"
        );
        securityEventAuditService.recordEvent(
                "认证安全",
                "IP_BLOCK_HIT",
                description,
                null,
                null,
                ip,
                userAgent,
                429,
                null,
                null
        );

        String message = status.permanent()
                ? "访问受限：IP 已被永久封禁"
                : "访问受限，请稍后再试";
        throw new BusinessException(ResultCode.RATE_LIMIT, message);
    }

    public void assertUserAllowed(Long userId, String scene, String clientIp, String userAgent) {
        if (userId == null || userId <= 0) {
            return;
        }
        UserBlockStatus status = getUserBlockStatus(userId);
        if (!status.blocked()) {
            return;
        }

        String description = String.format(
                "用户黑名单命中：scene=%s, userId=%d, remain=%ds, reason=%s, ip=%s",
                StringUtils.hasText(scene) ? scene : "unknown",
                userId,
                status.remainingSeconds(),
                StringUtils.hasText(status.reason()) ? status.reason() : "manual_block",
                StringUtils.hasText(clientIp) ? clientIp : "unknown"
        );
        securityEventAuditService.recordEvent(
                "认证安全",
                "USER_BLOCK_HIT",
                description,
                userId,
                null,
                clientIp,
                userAgent,
                403,
                null,
                null
        );

        String message = status.permanent()
                ? "账号已被永久封禁"
                : "账号处于封禁中，请稍后再试";
        throw new BusinessException(ResultCode.FORBIDDEN, message);
    }

    private int normalizeDurationMinutes(Integer durationMinutes) {
        if (durationMinutes == null) {
            return DEFAULT_BLOCK_MINUTES;
        }
        int minutes = durationMinutes;
        return Math.max(MIN_BLOCK_MINUTES, Math.min(minutes, MAX_BLOCK_MINUTES));
    }

    private String normalizeReason(String reason) {
        String safeReason = StringUtils.hasText(reason) ? reason.trim() : "manual_block";
        if (safeReason.length() > 200) {
            return safeReason.substring(0, 200);
        }
        return safeReason;
    }

    private String normalizeIpOrThrow(String rawIp) {
        String ip = normalizeIp(rawIp);
        if (!StringUtils.hasText(ip)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "IP 不能为空");
        }
        if (!ip.matches("^[0-9a-fA-F:.]{2,64}$")) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "IP 格式不正确");
        }
        return ip;
    }

    private Long normalizeUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "用户ID无效");
        }
        return userId;
    }

    private LocalDateTime toExpireTime(Integer durationMinutes) {
        if (durationMinutes == null) {
            return null;
        }
        return LocalDateTime.now().plusMinutes(durationMinutes);
    }

    private BlacklistEntry findActiveEntry(String blockType, String targetValue) {
        LocalDateTime now = LocalDateTime.now();
        return blacklistMapper.selectOne(new LambdaQueryWrapper<BlacklistEntry>()
                .eq(BlacklistEntry::getBlockType, blockType)
                .eq(BlacklistEntry::getTargetValue, targetValue)
                .orderByDesc(BlacklistEntry::getUpdateTime)
                .orderByDesc(BlacklistEntry::getId)
                .and(q -> q.isNull(BlacklistEntry::getExpireTime).or().gt(BlacklistEntry::getExpireTime, now))
                .last("LIMIT 1"));
    }

    private BlacklistEntry findLatestEntry(String blockType, String targetValue) {
        return blacklistMapper.selectOne(new LambdaQueryWrapper<BlacklistEntry>()
                .eq(BlacklistEntry::getBlockType, blockType)
                .eq(BlacklistEntry::getTargetValue, targetValue)
                .orderByDesc(BlacklistEntry::getUpdateTime)
                .orderByDesc(BlacklistEntry::getId)
                .last("LIMIT 1"));
    }

    private void saveEntry(BlacklistEntry entry) {
        if (entry.getId() == null) {
            blacklistMapper.insert(entry);
        } else {
            blacklistMapper.updateById(entry);
        }
    }

    private void syncRedisForEntry(BlacklistEntry entry) {
        String key = redisKey(entry.getBlockType(), entry.getTargetValue());
        if (key == null) {
            return;
        }
        String value = normalizeReason(entry.getReason());
        if (entry.getExpireTime() == null) {
            redisService.set(key, value);
            return;
        }

        long ttlSeconds = Duration.between(LocalDateTime.now(), entry.getExpireTime()).getSeconds();
        if (ttlSeconds <= 0) {
            redisService.delete(key);
            return;
        }
        redisService.set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    private void deleteRedisForEntry(BlacklistEntry entry) {
        String key = redisKey(entry.getBlockType(), entry.getTargetValue());
        if (key != null) {
            redisService.delete(key);
        }
    }

    private String redisKey(String blockType, String targetValue) {
        if (BLOCK_TYPE_IP.equals(blockType)) {
            return ipBlockKey(targetValue);
        }
        if (BLOCK_TYPE_USER.equals(blockType)) {
            try {
                long userId = Long.parseLong(targetValue);
                return userBlockKey(userId);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private void revokeUserSessions(Long userId) {
        rememberTokenMapper.invalidateAllUserTokens(userId);
        StpUtil.logout(userId);
    }

    private IpBlockStatus toIpStatus(BlacklistEntry entry) {
        boolean permanent = entry.getExpireTime() == null;
        long remainingSeconds = permanent ? -1 : Math.max(Duration.between(LocalDateTime.now(), entry.getExpireTime()).getSeconds(), 0);
        return new IpBlockStatus(entry.getTargetValue(), true, entry.getReason(), remainingSeconds, permanent);
    }

    private UserBlockStatus toUserStatus(BlacklistEntry entry) {
        boolean permanent = entry.getExpireTime() == null;
        long remainingSeconds = permanent ? -1 : Math.max(Duration.between(LocalDateTime.now(), entry.getExpireTime()).getSeconds(), 0);
        return new UserBlockStatus(entry.getUserId(), true, entry.getReason(), remainingSeconds, permanent, entry.getSubject());
    }

    private BlacklistItemVO toBlacklistItem(BlacklistEntry entry) {
        boolean permanent = entry.getExpireTime() == null;
        long remainingSeconds = permanent ? -1 : Math.max(Duration.between(LocalDateTime.now(), entry.getExpireTime()).getSeconds(), 0);
        String subject = StringUtils.hasText(entry.getSubject()) ? entry.getSubject() : entry.getTargetValue();
        if (BLOCK_TYPE_USER.equals(entry.getBlockType()) && !StringUtils.hasText(entry.getSubject())) {
            subject = entry.getUserId() == null ? entry.getTargetValue() : ("UID:" + entry.getUserId());
        }
        return new BlacklistItemVO(
                entry.getId(),
                entry.getBlockType(),
                entry.getTargetValue(),
                entry.getUserId(),
                subject,
                entry.getReason(),
                entry.getExpireTime(),
                remainingSeconds,
                permanent,
                entry.getCreateTime(),
                entry.getUpdateTime()
        );
    }

    private String normalizeIp(String rawIp) {
        if (!StringUtils.hasText(rawIp)) {
            return null;
        }
        return rawIp.trim();
    }

    private String ipBlockKey(String ip) {
        return IP_BLOCK_KEY_PREFIX + ip;
    }

    private String userBlockKey(Long userId) {
        return USER_BLOCK_KEY_PREFIX + userId;
    }

    public record IpBlockStatus(String ip,
                                boolean blocked,
                                String reason,
                                long remainingSeconds,
                                boolean permanent) {
    }

    public record UserBlockStatus(Long userId,
                                  boolean blocked,
                                  String reason,
                                  long remainingSeconds,
                                  boolean permanent,
                                  String subject) {
    }

    public record BlacklistItemVO(Long id,
                                  String blockType,
                                  String targetValue,
                                  Long userId,
                                  String subject,
                                  String reason,
                                  LocalDateTime expireTime,
                                  long remainingSeconds,
                                  boolean permanent,
                                  LocalDateTime createTime,
                                  LocalDateTime updateTime) {
    }
}
