package com.blog.system.service;

import com.blog.common.constant.CommonConstant;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.DesensitizeUtil;
import com.blog.infra.redis.RedisService;
import com.blog.system.entity.User;
import com.blog.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码服务
 * - 验证码存储在 Redis，带过期时间
 * - 冷却时间防止频繁发送（也存 Redis，防清缓存绕过）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailCodeService {

    private final RedisService redisService;
    private final EmailService emailService;
    private final SystemConfigService configService;
    private final UserMapper userMapper;

    /**
     * 发送验证码
     * @param email 目标邮箱
     * @param scene 场景标识（login / register），用于隔离不同场景的冷却
     */
    public void sendCode(String email, String scene) {
        int cooldownSeconds = configService.getIntValue("mail_code_cooldown_seconds", 60);
        int expireMinutes = configService.getIntValue("mail_code_expire_minutes", 5);

        // 忘记密码场景：检查账户是否存在
        if ("forgot-password".equals(scene)) {
            Long count = userMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getEmail, email));
            if (count == 0) {
                throw new BusinessException(ResultCode.USER_NOT_FOUND, "该邮箱未注册");
            }
        }

        // 注册场景：检查邮箱是否已被注册
        if ("register".equals(scene)) {
            Long count = userMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getEmail, email));
            if (count > 0) {
                throw new BusinessException(ResultCode.EMAIL_EXISTS, "该邮箱已注册");
            }
        }

        // 绑定/换绑邮箱场景：检查邮箱是否已被其他账号使用
        if ("bind".equals(scene) || "change-email".equals(scene)) {
            Long count = userMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getEmail, email));
            if (count > 0) {
                throw new BusinessException(ResultCode.EMAIL_EXISTS, "该邮箱已被其他账号使用");
            }
        }

        // 检查冷却时间（Redis key 带 scene，登录和注册独立冷却）
        String cooldownKey = CommonConstant.REDIS_EMAIL_CODE_COOLDOWN + scene + ":" + email;
        if (Boolean.TRUE.equals(redisService.hasKey(cooldownKey))) {
            throw new BusinessException(ResultCode.RATE_LIMIT, "验证码发送过于频繁，请稍后再试");
        }

        // 生成6位数字验证码
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999));

        // 存入 Redis（key 带 scene 隔离）
        String codeKey = CommonConstant.REDIS_EMAIL_CODE + scene + ":" + email;
        redisService.set(codeKey, code, expireMinutes, TimeUnit.MINUTES);

        // 发送新验证码时，重置失败次数
        String attemptsKey = CommonConstant.REDIS_EMAIL_CODE_ATTEMPTS + scene + ":" + email;
        redisService.delete(attemptsKey);

        // 设置冷却时间
        redisService.set(cooldownKey, "1", cooldownSeconds, TimeUnit.SECONDS);

        // 发送邮件
        emailService.sendVerifyCode(email, code);

        log.info("验证码已发送: email={}, scene={}", DesensitizeUtil.email(email), scene);
    }

    /**
     * 校验验证码
     * @return true 验证通过
     */
    public boolean verifyCode(String email, String scene, String code) {
        String codeKey = CommonConstant.REDIS_EMAIL_CODE + scene + ":" + email;
        String stored = redisService.get(codeKey);
        String attemptsKey = CommonConstant.REDIS_EMAIL_CODE_ATTEMPTS + scene + ":" + email;

        if (stored == null) {
            return false;
        }

        if (!stored.equals(code)) {
            int maxAttempts = configService.getIntValue("mail_code_max_attempts", 10);
            int expireMinutes = configService.getIntValue("mail_code_expire_minutes", 5);
            long attempts = redisService.incrementWithExpire(attemptsKey, Math.max(60L, expireMinutes * 60L));

            if (attempts >= maxAttempts) {
                redisService.delete(codeKey);
                log.warn("验证码连续校验失败已达上限，验证码失效: email={}, scene={}, attempts={}",
                        DesensitizeUtil.email(email), scene, attempts);
            }
            return false;
        }

        // 验证成功后删除，防止重复使用
        redisService.delete(codeKey);
        redisService.delete(attemptsKey);
        return true;
    }

    /**
     * 获取剩余冷却秒数（供前端同步倒计时）
     */
    public long getCooldownRemaining(String email, String scene) {
        String cooldownKey = CommonConstant.REDIS_EMAIL_CODE_COOLDOWN + scene + ":" + email;
        if (Boolean.TRUE.equals(redisService.hasKey(cooldownKey))) {
            // Redis TTL
            Long ttl = redisService.getTtl(cooldownKey, TimeUnit.SECONDS);
            return ttl != null && ttl > 0 ? ttl : 0;
        }
        return 0;
    }
}
