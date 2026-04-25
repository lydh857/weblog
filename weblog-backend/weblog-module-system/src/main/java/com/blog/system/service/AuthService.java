package com.blog.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.DesensitizeUtil;
import com.blog.common.util.PasswordUtil;
import com.blog.common.util.ValidateUtil;
import com.blog.infra.security.audit.SecurityEventAuditService;
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
import java.util.concurrent.ThreadLocalRandom;

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
    private final SecurityEventAuditService securityEventAuditService;
    private final SecurityRiskControlService securityRiskControlService;
    private final LoginSecurityPolicyService loginSecurityPolicyService;

    /** 用于等时密码校验的固定 bcrypt 哈希（明文: DummyPass123） */
    private static final String DUMMY_BCRYPT_HASH = "$2a$10$qEk8cM7W5iYvY2qu7hSD3uTyW7.u/HYxvHdscVnHEivNclDGhA0Qq";
    /** 失败响应最小延迟（毫秒） */
    private static final int FAILED_DELAY_MIN_MS = 160;
    /** 失败响应最大延迟（毫秒） */
    private static final int FAILED_DELAY_MAX_MS = 280;

    /**
     * 用户注册
     */
    @Transactional(rollbackFor = Exception.class)
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
        log.info("用户注册成功: email={}", DesensitizeUtil.email(req.getEmail()));
    }

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest req, String clientIp, String userAgent) {
        return loginWithType(req, clientIp, userAgent, "user");
    }

    private LoginResponse loginWithType(LoginRequest req, String clientIp, String userAgent, String loginType) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()));

        if (user == null) {
            // 执行一次固定哈希校验，降低通过响应时间探测账号存在性的风险
            verifyAgainstDummyHash(req.getPassword());
            recordAuthFailure(null, req.getEmail(), loginType, "用户不存在", clientIp, userAgent);
            applyFailureDelay();
            throw new BusinessException(ResultCode.UNAUTHORIZED, "邮箱或密码错误");
        }

        securityRiskControlService.assertUserAllowed(user.getId(), "login-" + loginType, clientIp, userAgent);

        // 检查账户锁定
        try {
            checkAccountLock(user);
        } catch (BusinessException ex) {
            if (ex.getCode() == 40103) {
                recordAuthBlocked(user.getId(), user.getEmail(), loginType, "账号锁定中", clientIp, userAgent, ex.getCode());
            }
            throw ex;
        }

        // 直接使用明文密码（由HTTPS传输）
        String decryptedPassword = req.getPassword();

        // 校验密码
        if (!PasswordUtil.matches(decryptedPassword, user.getPassword())) {
            boolean lockedNow = handleFailedLogin(user);
            String reason = lockedNow ? "密码错误（触发锁定）" : "密码错误";
            recordAuthFailure(user.getId(), user.getEmail(), loginType, reason, clientIp, userAgent);
            applyFailureDelay();
            throw new BusinessException(ResultCode.UNAUTHORIZED, "邮箱或密码错误");
        }

        // 检查账户状态
        if ("disabled".equals(user.getStatus())) {
            recordAuthBlocked(user.getId(), user.getEmail(), loginType, "账号已禁用", clientIp, userAgent, 403);
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
        loginLogService.recordLogin(user.getId(), user.getEmail(), loginType, "success", null, clientIp, userAgent);

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
            recordAuthFailure(null, email, "user", "验证码错误", clientIp, userAgent);
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
            log.info("验证码登录自动注册: email={}", DesensitizeUtil.email(email));
            emailService.sendInitialPasswordAsync(email, rawPassword);
        }

        securityRiskControlService.assertUserAllowed(user.getId(), "login-code-user", clientIp, userAgent);

        if ("disabled".equals(user.getStatus())) {
            recordAuthBlocked(user.getId(), user.getEmail(), "user", "账号已禁用", clientIp, userAgent, 403);
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        if ("locked".equals(user.getStatus())) {
            try {
                checkAccountLock(user);
            } catch (BusinessException ex) {
                if (ex.getCode() == 40103) {
                    recordAuthBlocked(user.getId(), user.getEmail(), "user", "账号锁定中", clientIp, userAgent, ex.getCode());
                }
                throw ex;
            }
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
    @Transactional(rollbackFor = Exception.class)
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
        log.info("忘记密码重置成功: email={}", DesensitizeUtil.email(email));
    }

    /**
     * 管理员登录
     */
    public LoginResponse adminLogin(LoginRequest req, String clientIp, String userAgent) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()));
        if (user == null) {
            // 执行一次固定哈希校验，降低通过响应时间探测账号存在性的风险
            verifyAgainstDummyHash(req.getPassword());
            recordAuthFailure(null, req.getEmail(), "admin", "用户不存在", clientIp, userAgent);
            applyFailureDelay();
            throw new BusinessException(ResultCode.UNAUTHORIZED, "邮箱或密码错误");
        }
        if (!"admin".equals(user.getRole())) {
            recordAuthBlocked(user.getId(), user.getEmail(), "admin", "非管理员", clientIp, userAgent, 401);
            applyFailureDelay();
            throw new BusinessException(ResultCode.UNAUTHORIZED, "邮箱或密码错误");
        }
        return loginWithType(req, clientIp, userAgent, "admin");
    }

    private void verifyAgainstDummyHash(String password) {
        String raw = password == null ? "" : password;
        PasswordUtil.matches(raw, DUMMY_BCRYPT_HASH);
    }

    private void applyFailureDelay() {
        int delay = ThreadLocalRandom.current().nextInt(FAILED_DELAY_MIN_MS, FAILED_DELAY_MAX_MS + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    private boolean handleFailedLogin(User user) {
        int maxAttempts = loginSecurityPolicyService.getMaxFailedAttempts();
        int lockMinutes = loginSecurityPolicyService.getLockMinutes();
        LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(lockMinutes);
        userMapper.incrementFailedAttemptsAndLock(user.getId(), maxAttempts, lockUntil);

        User latest = userMapper.selectById(user.getId());
        if (latest != null && "locked".equals(latest.getStatus())) {
            Integer attempts = latest.getFailedLoginAttempts() == null ? 0 : latest.getFailedLoginAttempts();
            log.warn("账户锁定: email={}, 连续失败{}次", DesensitizeUtil.email(user.getEmail()), attempts);
            return true;
        }
        return false;
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

    private void recordAuthFailure(Long userId,
                                   String email,
                                   String loginType,
                                   String reason,
                                   String clientIp,
                                   String userAgent) {
        loginLogService.recordLogin(userId, email, loginType, "failed", reason, clientIp, userAgent);
        String description = String.format(
                "登录失败: type=%s, reason=%s, email=%s",
                loginType,
                reason,
                DesensitizeUtil.email(email)
        );
        securityEventAuditService.recordEvent(
                "认证安全",
                "LOGIN_FAIL",
                description,
                userId,
                null,
                clientIp,
                userAgent,
                401,
                null,
                null
        );
    }

    private void recordAuthBlocked(Long userId,
                                   String email,
                                   String loginType,
                                   String reason,
                                   String clientIp,
                                   String userAgent,
                                   int responseCode) {
        loginLogService.recordLogin(userId, email, loginType, "failed", reason, clientIp, userAgent);
        String description = String.format(
                "登录拦截: type=%s, reason=%s, email=%s",
                loginType,
                reason,
                DesensitizeUtil.email(email)
        );
        securityEventAuditService.recordEvent(
                "认证安全",
                "LOGIN_BLOCKED",
                description,
                userId,
                null,
                clientIp,
                userAgent,
                responseCode,
                null,
                null
        );
    }
}
