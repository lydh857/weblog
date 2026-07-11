package com.blog.api.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.common.util.IpUtil;
import com.blog.common.util.PageParamUtil;
import com.blog.common.util.PasswordUtil;
import com.blog.infra.security.audit.AuditLog;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.system.dto.UserVO;
import com.blog.system.entity.User;
import com.blog.system.mapper.UserMapper;
import com.blog.system.service.EmailService;
import com.blog.system.service.SecurityRiskControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.blog.common.constant.CommonConstant.MAX_BATCH_SIZE;

/**
 * 管理端 - 用户管理
 */
@Slf4j
@Tag(name = "管理端-用户管理", description = "用户查询、状态管理")
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private static final String LEGACY_LOCAL_UPLOAD_PREFIX = "http://localhost:9091/uploads";

    private final UserMapper userMapper;
    private final EmailService emailService;
    private final DynamicRateLimitPolicyService dynamicRateLimitPolicyService;
    private final SecurityRiskControlService securityRiskControlService;

    private static final String ADMIN_USER_STATUS_UPDATE_RATE_LIMIT_KEY = "admin_user_status_update_rate_limit";
    private static final String ADMIN_USER_RESET_PASSWORD_RATE_LIMIT_KEY = "admin_user_reset_password_rate_limit";

    @Value("${blog.upload.base-url:http://localhost:9091/uploads}")
    private String uploadBaseUrl;

    @Operation(summary = "获取当前登录管理员信息")
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.success(Map.of());
        }
        return Result.success(Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "nickname", user.getNickname() != null ? user.getNickname() : "",
                "avatar", user.getAvatar() != null ? normalizeLegacyUploadUrl(user.getAvatar()) : "",
                "role", user.getRole() != null ? user.getRole() : ""
        ));
    }

    @Operation(summary = "用户列表（分页）")
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {

        PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(User::getEmail, keyword)
                    .or().like(User::getNickname, keyword));
        }
        if (role != null && !role.isEmpty()) {
            wrapper.eq(User::getRole, role);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreateTime);

        IPage<User> page = userMapper.selectPage(new Page<>(pageParams.pageNum(), pageParams.pageSize()), wrapper);
        Map<Long, SecurityRiskControlService.UserBlockStatus> blockStatusMap = securityRiskControlService.getActiveUserBlockStatusMap(
                page.getRecords().stream().map(User::getId).toList()
        );

        // 转为 VO，隐藏 lastLoginIp、failedLoginAttempts 等敏感字段
        List<UserVO> records = page.getRecords().stream().map(u -> {
            UserVO vo = new UserVO();
            vo.setId(u.getId());
            vo.setEmail(u.getEmail());
            vo.setNickname(u.getNickname());
            vo.setAvatar(normalizeLegacyUploadUrl(u.getAvatar()));
            vo.setBio(u.getBio());
            vo.setRole(u.getRole());
            vo.setStatus(u.getStatus());
            vo.setLastLoginTime(u.getLastLoginTime());
            vo.setLastLoginIp(u.getLastLoginIp());
            vo.setFailedLoginAttempts(u.getFailedLoginAttempts());
            vo.setLockUntil(u.getLockUntil());
            vo.setMutedPermanent(Boolean.TRUE.equals(u.getMutedPermanent()));
            vo.setMutedUntil(u.getMutedUntil());
            vo.setMutedReason(u.getMutedReason());
            SecurityRiskControlService.UserBlockStatus blockStatus = blockStatusMap.getOrDefault(
                    u.getId(),
                    new SecurityRiskControlService.UserBlockStatus(u.getId(), false, null, 0, false, null)
            );
            vo.setUserBlocked(blockStatus.blocked());
            vo.setUserBlockPermanent(blockStatus.permanent());
            vo.setUserBlockRemainingSeconds(blockStatus.remainingSeconds());
            vo.setUserBlockReason(blockStatus.reason());
            vo.setCreateTime(u.getCreateTime());
            return vo;
        }).collect(Collectors.toList());

        return Result.success(Map.of(
                "records", records,
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "pages", page.getPages()));
    }

    private static final Set<String> VALID_USER_STATUSES = Set.of("enabled", "disabled");

    @Operation(summary = "更新用户状态（启用/禁用）")
    @PutMapping("/{userId}/status")
    @RateLimit(key = "admin-user-status-update", capacity = 120, seconds = 60)
    @AuditLog(module = "用户管理", operation = "UPDATE", description = "更新用户状态")
    public Result<Void> updateStatus(@PathVariable Long userId,
                                     @RequestParam String status,
                                     HttpServletRequest request) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-user-status-update",
                ADMIN_USER_STATUS_UPDATE_RATE_LIMIT_KEY,
                20,
                1,
                120,
                60,
                IpUtil.getClientIp(request),
                "更新用户状态过于频繁，请稍后再试"
        );
        if (!VALID_USER_STATUSES.contains(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的状态值，仅支持: enabled, disabled");
        }
        // 防止管理员禁用自己
        long currentUserId = StpUtil.getLoginIdAsLong();
        if (userId.equals(currentUserId) && "disabled".equals(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能禁用当前登录的管理员账号");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStatus, status));
        return Result.success();
    }

    private String normalizeLegacyUploadUrl(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return rawValue;
        }
        if (!rawValue.contains(LEGACY_LOCAL_UPLOAD_PREFIX)) {
            return rawValue;
        }
        String normalizedBase = normalizeUploadBaseUrl(uploadBaseUrl);
        return rawValue.replace(LEGACY_LOCAL_UPLOAD_PREFIX, normalizedBase);
    }

    private String normalizeUploadBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            return LEGACY_LOCAL_UPLOAD_PREFIX;
        }
        String normalized = rawBaseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @Operation(summary = "重置用户密码（随机生成并邮件通知）")
    @PostMapping("/{userId}/reset-password")
    @RateLimit(key = "admin-user-reset-password", capacity = 120, seconds = 60)
    @AuditLog(module = "用户管理", operation = "UPDATE", description = "重置用户密码")
    public Result<Void> resetPassword(@PathVariable Long userId, HttpServletRequest request) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-user-reset-password",
                ADMIN_USER_RESET_PASSWORD_RATE_LIMIT_KEY,
                10,
                1,
                120,
                60,
                IpUtil.getClientIp(request),
                "重置用户密码过于频繁，请稍后再试"
        );
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }
        String newPassword = PasswordUtil.generateRandomPassword();
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPassword, PasswordUtil.encode(newPassword)));
        // 异步发送邮件
        emailService.sendResetPasswordAsync(user.getEmail(), newPassword);
        log.info("管理员重置用户密码: userId={}", userId);
        return Result.success();
    }

    @Operation(summary = "解锁用户")
    @PostMapping("/{userId}/unlock")
    @RateLimit(key = "admin-user-status-update", capacity = 120, seconds = 60)
    @AuditLog(module = "用户管理", operation = "UPDATE", description = "解锁用户")
    public Result<Void> unlockUser(@PathVariable Long userId, HttpServletRequest request) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-user-status-update",
                ADMIN_USER_STATUS_UPDATE_RATE_LIMIT_KEY,
                20,
                1,
                120,
                60,
                IpUtil.getClientIp(request),
                "解锁用户过于频繁，请稍后再试"
        );
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStatus, "enabled")
                .set(User::getFailedLoginAttempts, 0)
                .set(User::getLockUntil, null));
        log.info("管理员解锁用户: userId={}", userId);
        return Result.success();
    }

    @Operation(summary = "禁言用户")
    @PostMapping("/{userId}/mute")
    @RateLimit(key = "admin-user-status-update", capacity = 120, seconds = 60)
    @AuditLog(module = "用户管理", operation = "UPDATE", description = "禁言用户")
    public Result<Void> muteUser(@PathVariable Long userId,
                                  @RequestBody MuteUserRequest body,
                                  HttpServletRequest request) {
        enforceUserStatusUpdateLimit(request, "禁言用户过于频繁，请稍后再试");
        MuteParams params = validateMuteRequest(body);
        securityRiskControlService.muteUser(userId, params.minutes(), params.permanent(), params.reason(), true);
        log.info("管理员禁言用户: userId={}, permanent={}, minutes={}", userId, params.permanent(), params.minutes());
        return Result.success();
    }

    @Operation(summary = "按邮箱禁言用户")
    @PostMapping("/mute-by-email")
    @RateLimit(key = "admin-user-status-update", capacity = 120, seconds = 60)
    @AuditLog(module = "用户管理", operation = "UPDATE", description = "按邮箱禁言用户")
    public Result<Void> muteUserByEmail(@RequestBody MuteUserByEmailRequest body,
                                        HttpServletRequest request) {
        enforceUserStatusUpdateLimit(request, "禁言用户过于频繁，请稍后再试");
        String email = body == null ? null : body.getEmail();
        if (email == null || email.trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邮箱不能为空");
        }
        MuteParams params = validateMuteRequest(body);
        securityRiskControlService.muteUserByEmail(email, params.minutes(), params.permanent(), params.reason(), true);
        log.info("管理员按邮箱禁言用户: email={}, permanent={}, minutes={}", email.trim().toLowerCase(), params.permanent(), params.minutes());
        return Result.success();
    }

    @Operation(summary = "解除用户禁言")
    @PostMapping("/{userId}/unmute")
    @RateLimit(key = "admin-user-status-update", capacity = 120, seconds = 60)
    @AuditLog(module = "用户管理", operation = "UPDATE", description = "解除用户禁言")
    public Result<Void> unmuteUser(@PathVariable Long userId, HttpServletRequest request) {
        enforceUserStatusUpdateLimit(request, "解除禁言过于频繁，请稍后再试");
        securityRiskControlService.unmuteUser(userId);
        log.info("管理员解除用户禁言: userId={}", userId);
        return Result.success();
    }

    @Operation(summary = "按邮箱解除用户禁言")
    @PostMapping("/unmute-by-email")
    @RateLimit(key = "admin-user-status-update", capacity = 120, seconds = 60)
    @AuditLog(module = "用户管理", operation = "UPDATE", description = "按邮箱解除用户禁言")
    public Result<Void> unmuteUserByEmail(@RequestBody UnmuteUserByEmailRequest body, HttpServletRequest request) {
        enforceUserStatusUpdateLimit(request, "解除禁言过于频繁，请稍后再试");
        String email = body == null ? null : body.getEmail();
        if (email == null || email.trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邮箱不能为空");
        }
        securityRiskControlService.unmuteUserByEmail(email);
        log.info("管理员按邮箱解除用户禁言: email={}", email.trim().toLowerCase());
        return Result.success();
    }

    @Operation(summary = "批量更新用户状态")
    @PutMapping("/batch/status")
    @RateLimit(key = "admin-user-status-update", capacity = 120, seconds = 60)
    @AuditLog(module = "用户管理", operation = "UPDATE", description = "批量更新用户状态")
    public Result<Void> batchUpdateStatus(@RequestBody java.util.Map<String, Object> body,
                                          HttpServletRequest request) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-user-status-update",
                ADMIN_USER_STATUS_UPDATE_RATE_LIMIT_KEY,
                20,
                1,
                120,
                60,
                IpUtil.getClientIp(request),
                "批量更新用户状态过于频繁，请稍后再试"
        );
        @SuppressWarnings("unchecked")
        java.util.List<Number> ids = (java.util.List<Number>) body.get("ids");
        String status = (String) body.get("status");
        if (status != null && !VALID_USER_STATUSES.contains(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的状态值，仅支持: enabled, disabled");
        }
        if (ids != null && !ids.isEmpty() && status != null) {
            if (ids.size() > MAX_BATCH_SIZE) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "批量操作数量不能超过 " + MAX_BATCH_SIZE);
            }
            java.util.List<Long> longIds = ids.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());
            // 排除管理员
            LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<User>()
                    .in(User::getId, longIds)
                    .ne(User::getRole, "admin")
                    .set(User::getStatus, status);
            if ("enabled".equals(status)) {
                // 启用时同时清除锁定信息
                wrapper.set(User::getFailedLoginAttempts, 0)
                       .set(User::getLockUntil, null);
            }
            userMapper.update(null, wrapper);
        }
        return Result.success();
    }

    public static class MuteUserRequest {
        private Boolean permanent;
        private Integer minutes;
        private String reason;

        public Boolean getPermanent() {
            return permanent;
        }

        public void setPermanent(Boolean permanent) {
            this.permanent = permanent;
        }

        public Integer getMinutes() {
            return minutes;
        }

        public void setMinutes(Integer minutes) {
            this.minutes = minutes;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class MuteUserByEmailRequest extends MuteUserRequest {
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class UnmuteUserByEmailRequest {
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    private void enforceUserStatusUpdateLimit(HttpServletRequest request, String message) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-user-status-update",
                ADMIN_USER_STATUS_UPDATE_RATE_LIMIT_KEY,
                20,
                1,
                120,
                60,
                IpUtil.getClientIp(request),
                message
        );
    }

    private MuteParams validateMuteRequest(MuteUserRequest body) {
        boolean permanent = body != null && Boolean.TRUE.equals(body.getPermanent());
        Integer minutes = body != null ? body.getMinutes() : null;
        String reason = body != null && body.getReason() != null ? body.getReason().trim() : "";
        if (reason.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "禁言原因不能为空");
        }
        if (reason.length() > 200) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "禁言原因不能超过200字");
        }
        if (!permanent && (minutes == null || minutes < 1 || minutes > 43200)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "限时禁言时长必须在1分钟到30天之间");
        }
        return new MuteParams(permanent, minutes, reason);
    }

    private record MuteParams(boolean permanent, Integer minutes, String reason) {
    }

}
