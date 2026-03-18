package com.blog.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.system.dto.LoginVO;
import com.blog.system.entity.RememberToken;
import com.blog.system.entity.User;
import com.blog.system.mapper.RememberTokenMapper;
import com.blog.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Remember Token 服务
 * 实现"记住我"自动登录功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RememberTokenService {

    private final RememberTokenMapper rememberTokenMapper;
    private final UserMapper userMapper;

    /**
     * Token 有效期（天）
     */
    private static final int TOKEN_EXPIRE_DAYS = 30;

    /**
     * 生成 Remember Token
     *
     * @param user 用户信息
     * @param deviceInfo 设备信息
     * @param ip 登录 IP
     * @return Remember Token
     */
    @Transactional
    public String generateToken(User user, String deviceInfo, String ip) {
        // 生成 UUID Token
        String rawToken = UUID.randomUUID().toString().replace("-", "");
        String tokenHash = hashToken(rawToken);

        RememberToken rememberToken = new RememberToken();
        rememberToken.setUserId(user.getId());
        rememberToken.setToken(tokenHash);
        rememberToken.setDeviceInfo(deviceInfo);
        rememberToken.setLastLoginIp(ip);
        rememberToken.setIsValid(true);
        rememberToken.setExpireTime(LocalDateTime.now().plusDays(TOKEN_EXPIRE_DAYS));
        rememberToken.setCreateTime(LocalDateTime.now());
        rememberToken.setUpdateTime(LocalDateTime.now());

        rememberTokenMapper.insert(rememberToken);

        log.info("生成 Remember Token: userId={}, expireTime={}",
                user.getId(), rememberToken.getExpireTime());

        return rawToken;
    }

    /**
     * 验证 Remember Token
     *
     * @param token Token
     * @return 验证成功返回用户信息，失败返回 null
     */
    @Transactional
    public User verifyToken(String token, String deviceInfo, String ip) {
        String tokenHash = hashToken(token);
        RememberToken rememberToken = rememberTokenMapper.selectByTokenHash(tokenHash);

        if (rememberToken == null) {
            log.warn("Remember Token 不存在");
            return null;
        }

        // 检查是否有效
        if (!rememberToken.getIsValid()) {
            log.warn("Remember Token 已失效");
            return null;
        }

        // 检查是否过期
        if (rememberToken.getExpireTime().isBefore(LocalDateTime.now())) {
            log.warn("Remember Token 已过期：expireTime={}", rememberToken.getExpireTime());
            invalidateToken(token);
            return null;
        }

        // 查询用户信息
        User user = userMapper.selectById(rememberToken.getUserId());
        if (user == null) {
            log.error("Remember Token 关联的用户不存在：userId={}", rememberToken.getUserId());
            invalidateToken(token);
            return null;
        }

        // 设备绑定校验（最高安全策略：设备信息不一致直接拒绝）
        if (rememberToken.getDeviceInfo() != null && !rememberToken.getDeviceInfo().isBlank()
                && deviceInfo != null && !deviceInfo.isBlank()) {
            if (!MessageDigest.isEqual(
                    rememberToken.getDeviceInfo().getBytes(StandardCharsets.UTF_8),
                    deviceInfo.getBytes(StandardCharsets.UTF_8))) {
                log.warn("Remember Token 设备不匹配：userId={}", user.getId());
                invalidateToken(token);
                return null;
            }
        }

        if (!"enabled".equals(user.getStatus())) {
            log.warn("Remember Token 用户状态不可用：userId={}, status={}", user.getId(), user.getStatus());
            invalidateToken(token);
            return null;
        }

        // 更新最后登录时间和 IP
        rememberToken.setLastLoginIp(ip);
        rememberToken.setUpdateTime(LocalDateTime.now());
        rememberTokenMapper.updateById(rememberToken);

        log.info("Remember Token 验证成功：userId={}", user.getId());

        return user;
    }

    /**
     * 使 Token 失效（用户登出时使用）
     */
    @Transactional
    public void invalidateToken(String token) {
        String tokenHash = hashToken(token);
        int affected = rememberTokenMapper.invalidateTokenByHash(tokenHash);
        log.info("Remember Token 已失效：affected={}", affected);
    }

    /**
     * 使用户的所有 Token 失效（修改密码时使用）
     */
    @Transactional
    public void invalidateAllUserTokens(Long userId) {
        rememberTokenMapper.invalidateAllUserTokens(userId);
        log.info("用户所有 Remember Token 已失效：userId={}", userId);
    }

    /**
     * 清理过期 Token（定时任务）
     * 每天凌晨 3 点执行，删除所有已过期的 Remember Token 记录
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public int cleanupExpiredTokens() {
        int count = rememberTokenMapper.deleteExpired(LocalDateTime.now());
        log.info("清理过期 Remember Token：count={}", count);
        return count;
    }

    /**
     * 根据 Remember Token 自动登录
     *
     * @param token Remember Token
     * @return 登录成功返回 LoginVO，失败返回 null
     */
    @Transactional
    public LoginVO autoLogin(String token, String deviceInfo, String ip) {
        User user = verifyToken(token, deviceInfo, ip);
        if (user == null) {
            return null;
        }

        // 一次性 token：自动登录成功后轮换 token，降低重放风险
        invalidateToken(token);
        String newRememberToken = generateToken(user, deviceInfo, ip);

        // 生成 Sa-Token
        StpUtil.login(user.getId());
        String newToken = StpUtil.getTokenValue();

        return LoginVO.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .token(newToken)
                .rememberToken(newRememberToken)
                .build();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Token hash failed", e);
        }
    }
}
