package com.blog.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.PasswordUtil;
import com.blog.common.util.ValidateUtil;
import com.blog.system.dto.LoginRequest;
import com.blog.system.dto.LoginResponse;
import com.blog.system.dto.RegisterRequest;
import com.blog.system.entity.User;
import com.blog.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final EmailCodeService emailCodeService;
    private final EmailService emailService;
    private final LoginLogService loginLogService;
    private final RememberTokenService rememberTokenService;

    /** 连续失败锁定阈值 */
    private static final int MAX_FAILED_ATTEMPTS = 5;
    /** 锁定时长（分钟） */
    private static final int LOCK_DURATION_MINUTES = 30;

    /**
     * 用户注册
     */
    @Transactional
    public void register(RegisterRequest req) {
        if (!ValidateUtil.isValidEmail(req.getEmail())) {
            throw new BusinessException(ResultCode.EMAIL_INVALID);
        }

        String decryptedPassword = req.getPassword();
        validatePasswordStrength(decryptedPassword);

        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()));
        if (count > 0) {
            throw new BusinessException(ResultCode.EMAIL_EXISTS);
        }

        User user = new User();
        user.setEmail(req.getEmail());
        user.setPassword(PasswordUtil.encode(decryptedPassword));
        user.setNickname(req.getNickname() != null ? req.getNickname() : "用户" + System.currentTimeMillis() % 100000);
        user.setRole("user");
        user.setStatus("enabled");
        user.setFailedLoginAttempts(0);

        userMapper.insert(user);
        log.info("用户注册成功: email={}", req.getEmail());
    }

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest req, String clientIp, String userAgent) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()));

        if (user == null) {
            // 记录失败登录
            loginLogService.recordLogin(null, req.getEmail(), "user", "failed", "用户不存在", clientIp, userAgent);
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "邮箱或密码错误");
        }

        // 检查账户锁定
        checkAccountLock(user);

        // 直接使用明文密码（由HTTPS传输）
        String decryptedPassword = req.getPassword();

        // 校验密码
        if (!PasswordUtil.matches(decryptedPassword, user.getPassword())) {
            handleFailedLogin(user);
            // 记录失败登录
            loginLogService.recordLogin(user.getId(), user.getEmail(), "user", "failed", "密码错误", clientIp, userAgent);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "邮箱或密码错误");
        }

        // 检查账户状态
        if ("disabled".equals(user.getStatus())) {
            loginLogService.recordLogin(user.getId(), user.getEmail(), "user", "failed", "账号已禁用", clientIp, userAgent);
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        // 登录成功，重置失败次数
        resetFailedAttempts(user.getId());

        // 更新登录信息
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getLastLoginTime, LocalDateTime.now())
                .set(User::getLastLoginIp, clientIp));

        // Sa-Token 登录
        StpUtil.login(user.getId());

        // 记录成功登录
        loginLogService.recordLogin(user.getId(), user.getEmail(), "user", "success", null, clientIp, userAgent);

        // 生成 Remember Token（如果请求记住我）
        String rememberToken = null;
        if (req.isRememberMe()) {
            rememberToken = rememberTokenService.generateToken(user, userAgent, clientIp);
            log.info("生成 Remember Token: userId={}", user.getId());
        }

        return LoginResponse.builder()
                .success(true)
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .needBindEmail(user.getEmail() == null || user.getEmail().isBlank())
                .hasPassword(user.getPassword() != null && !user.getPassword().isBlank())
                .rememberToken(rememberToken)
                .build();
    }

    /**
     * 验证码登录（首次自动注册）
     */
    public LoginResponse loginByCode(String email, String code, String clientIp, String userAgent) {
        if (!ValidateUtil.isValidEmail(email)) {
            throw new BusinessException(ResultCode.EMAIL_INVALID);
        }

        if (!emailCodeService.verifyCode(email, "login", code)) {
            loginLogService.recordLogin(null, email, "user", "failed", "验证码错误", clientIp, userAgent);
            throw new BusinessException(ResultCode.VERIFY_CODE_INVALID);
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));

        if (user == null) {
            String rawPassword = PasswordUtil.generateRandomPassword();
            user = new User();
            user.setEmail(email);
            user.setPassword(PasswordUtil.encode(rawPassword));
            user.setNickname("用户" + System.currentTimeMillis() % 100000);
            user.setRole("user");
            user.setStatus("enabled");
            user.setFailedLoginAttempts(0);
            userMapper.insert(user);
            log.info("验证码登录自动注册: email={}", email);
            emailService.sendInitialPasswordAsync(email, rawPassword);
        }

        if ("disabled".equals(user.getStatus())) {
            loginLogService.recordLogin(user.getId(), user.getEmail(), "user", "failed", "账号已禁用", clientIp, userAgent);
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        if ("locked".equals(user.getStatus())) {
            checkAccountLock(user);
            user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        }

        resetFailedAttempts(user.getId());

        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getLastLoginTime, LocalDateTime.now())
                .set(User::getLastLoginIp, clientIp));

        StpUtil.login(user.getId());

        // 记录成功登录
        loginLogService.recordLogin(user.getId(), user.getEmail(), "user", "success", null, clientIp, userAgent);

        return LoginResponse.builder()
                .success(true)
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .needBindEmail(false)
                .hasPassword(user.getPassword() != null && !user.getPassword().isBlank())
                .build();
    }

    /**
     * 注册（带验证码校验）
     */
    @Transactional
    public void registerWithCode(RegisterRequest req, String code) {
        if (!emailCodeService.verifyCode(req.getEmail(), "register", code)) {
            throw new BusinessException(ResultCode.VERIFY_CODE_INVALID);
        }
        register(req);
    }

    /**
     * 登出
     */
    public void logout() {
        if (StpUtil.isLogin()) {
            Long userId = StpUtil.getLoginIdAsLong();
            rememberTokenService.invalidateAllUserTokens(userId);
        }
        StpUtil.logout();
    }

    /**
     * 忘记密码
     */
    public void forgotPassword(String email, String code, String newPassword) {
        if (!ValidateUtil.isValidEmail(email)) {
            throw new BusinessException(ResultCode.EMAIL_INVALID);
        }
        if (!emailCodeService.verifyCode(email, "forgot-password", code)) {
            throw new BusinessException(ResultCode.VERIFY_CODE_INVALID);
        }

        String decryptedPassword = newPassword;
        validatePasswordStrength(decryptedPassword);

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "该邮箱未注册");
        }

        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getPassword, PasswordUtil.encode(decryptedPassword)));
        rememberTokenService.invalidateAllUserTokens(user.getId());
        log.info("忘记密码重置成功: email={}", email);
    }

    /**
     * 管理员登录
     */
    public LoginResponse adminLogin(LoginRequest req, String clientIp, String userAgent) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "邮箱或密码错误");
        }
        if (!"admin".equals(user.getRole())) {
            loginLogService.recordLogin(user.getId(), user.getEmail(), "admin", "failed", "非管理员", clientIp, userAgent);
            throw new BusinessException(ResultCode.ROLE_DENIED, "非管理员账号");
        }
        return login(req, clientIp, userAgent);
    }

    private void checkAccountLock(User user) {
        if ("locked".equals(user.getStatus())) {
            if (user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now())) {
                long remainMinutes = java.time.Duration.between(LocalDateTime.now(), user.getLockUntil()).toMinutes() + 1;
                throw new BusinessException(ResultCode.ACCOUNT_LOCKED,
                        "账号已锁定，请" + remainMinutes + "分钟后再试");
            }
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .set(User::getStatus, "enabled")
                    .set(User::getFailedLoginAttempts, 0)
                    .set(User::getLockUntil, null));
        }
    }

    private void handleFailedLogin(User user) {
        LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES);
        userMapper.incrementFailedAttemptsAndLock(user.getId(), MAX_FAILED_ATTEMPTS, lockUntil);

        User latest = userMapper.selectById(user.getId());
        if (latest != null && "locked".equals(latest.getStatus())) {
            Integer attempts = latest.getFailedLoginAttempts() == null ? 0 : latest.getFailedLoginAttempts();
            log.warn("账户锁定: email={}, 连续失败{}次", user.getEmail(), attempts);
        }
    }

    private void resetFailedAttempts(Long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getFailedLoginAttempts, 0)
                .set(User::getStatus, "enabled")
                .set(User::getLockUntil, null));
    }

    private void validatePasswordStrength(String password) {
        if (password.length() < 8) {
            throw new BusinessException(ResultCode.PASSWORD_WEAK, "密码至少8位");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new BusinessException(ResultCode.PASSWORD_WEAK, "密码需包含大写字母");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new BusinessException(ResultCode.PASSWORD_WEAK, "密码需包含小写字母");
        }
        if (!password.matches(".*\\d.*")) {
            throw new BusinessException(ResultCode.PASSWORD_WEAK, "密码需包含数字");
        }
    }
}
