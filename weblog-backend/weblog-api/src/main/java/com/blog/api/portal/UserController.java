package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.api.service.ReviewMailActionService;
import com.blog.api.security.UploadGuardService;
import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.api.security.UploadValidationService;
import com.blog.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.common.util.IpUtil;
import com.blog.common.util.ValidateUtil;
import com.blog.content.service.OssResourceService;
import com.blog.infra.oss.StorageFacade;
import com.blog.infra.security.audit.AuditLog;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.system.dto.UpdateProfileRequest;
import com.blog.system.dto.UserProfileVO;
import com.blog.system.entity.UserProfileReview;
import com.blog.system.service.EmailCodeService;
import com.blog.system.service.UserProfileReviewService;
import com.blog.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * 用户端 - 个人中心接口
 */
@Tag(name = "用户端-个人中心", description = "个人资料查询、更新、头像上传")
@Slf4j
@RestController
@RequestMapping("/api/portal/user")
public class UserController {

    private static final String AVATAR_USAGE = "avatar";
    private static final String AVATAR_PENDING_FILE_PREFIX = "avatar_pending__";
    private static final int MAX_EMAIL_LENGTH = 100;
    private static final int MAX_CODE_LENGTH = 12;
    private static final int MAX_PASSWORD_LENGTH = 128;

private final UserService userService;
    private final UserProfileReviewService userProfileReviewService;
    private final ReviewMailActionService reviewMailActionService;

    public UserController(UserService userService,
                           UserProfileReviewService userProfileReviewService,
                           ReviewMailActionService reviewMailActionService) {
        this.userService = userService;
        this.userProfileReviewService = userProfileReviewService;
        this.reviewMailActionService = reviewMailActionService;
    }

    @Autowired
    private StorageFacade storageFacade;

    @Autowired(required = false)
    private OssResourceService ossResourceService;

    @Autowired
    private EmailCodeService emailCodeService;

    @Autowired
    private UploadGuardService uploadGuardService;

    @Autowired
    private UploadValidationService uploadValidationService;

    @Autowired
    private DynamicRateLimitPolicyService dynamicRateLimitPolicyService;

    private static final String UPLOAD_RATE_LIMIT_KEY = "upload_rate_limit";
    private static final String USER_BIND_EMAIL_RATE_LIMIT_KEY = "user_bind_email_rate_limit";
    private static final String USER_CHANGE_EMAIL_RATE_LIMIT_KEY = "user_change_email_rate_limit";
    private static final String USER_SET_PASSWORD_RATE_LIMIT_KEY = "user_set_password_rate_limit";
    private static final String USER_RESET_PASSWORD_RATE_LIMIT_KEY = "user_reset_password_rate_limit";

    @Operation(summary = "获取个人资料")
    @GetMapping("/profile")
    @RateLimit(key = "user-profile-get", capacity = 60, seconds = 60)
    public Result<UserProfileVO> getProfile() {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(userService.getProfile(userId));
    }

    @Operation(summary = "提交个人信息审核")
    @PutMapping("/profile")
    @RateLimit(key = "user-profile-update", capacity = 20, seconds = 60)
    @AuditLog(module = "个人中心", operation = "UPDATE", description = "提交个人信息审核")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        UserProfileReview review = userProfileReviewService.submitReview(userId, req);
        if (review != null) {
            reviewMailActionService.sendPendingProfileReviewEmail(review.getId());
        }
        return Result.success();
    }

@Operation(summary = "上传头像（提交审核）")
    @PostMapping("/avatar")
    @RateLimit(key = "user-avatar-upload", capacity = 300, seconds = 300)
    @AuditLog(module = "个人中心", operation = "UPDATE", description = "提交头像审核")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file,
                                       HttpServletRequest request) throws IOException {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        String clientIp = IpUtil.getClientIp(request);
        dynamicRateLimitPolicyService.enforcePerIp(
                "user-avatar-upload",
                UPLOAD_RATE_LIMIT_KEY,
                20,
                1,
                300,
                300,
                clientIp,
                "上传过于频繁，请稍后再试"
        );

        if (!storageFacade.isStorageEnabled()) {
            throw new BusinessException(ResultCode.FAIL, "文件上传服务未启用");
        }
        uploadValidationService.validateImage(file);

        uploadGuardService.consumeUpload(
                UploadGuardService.UploadScene.AVATAR_IMAGE,
                userId,
                clientIp,
                file.getSize());

        String url;
        String objectKey;
        url = storageFacade.upload(file.getInputStream(), file.getOriginalFilename(), file.getSize());
        objectKey = storageFacade.extractObjectKey(url);
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ResultCode.FAIL, "上传成功但无法解析文件路径");
        }

        // 资源服务未启用时，降级为直接更新头像
        if (ossResourceService == null) {
            userService.updateAvatar(userId, url);
            return Result.success(url);
        }

        String ext = extractExtFromObjectKey(objectKey);
        validateStoredExtension(ext, objectKey);

        if (userService.isAdmin(userId)) {
            ossResourceService.record(
                    file.getOriginalFilename(), objectKey, file.getSize(),
                    ext, file.getContentType(), url, userId, AVATAR_USAGE);
            userService.updateAvatar(userId, url);
            return Result.success(url);
        }

        // 清理该用户此前未审核的头像（只保留最新提交）
        var avatarPage = ossResourceService.page(1, 50, userId, AVATAR_USAGE);
        for (var old : avatarPage.getRecords()) {
            if (!isPendingAvatarRecord(old.getFileName())) {
                continue;
            }
            try {
                ossResourceService.delete(old.getId(), userId, false);
            } catch (Exception e) {
                log.warn("密码校验失败", e);
            }
        }

        // 记录待审核头像资源
        {
            ossResourceService.record(
                    buildPendingFileName(file.getOriginalFilename()), objectKey, file.getSize(),
                    ext, file.getContentType(), url, userId, AVATAR_USAGE);
        }

        return Result.success(url);
    }

    private boolean isPendingAvatarRecord(String fileName) {
        return fileName != null && fileName.startsWith(AVATAR_PENDING_FILE_PREFIX);
    }

    private String buildPendingFileName(String originalFileName) {
        String fileName = originalFileName == null ? "avatar.webp" : originalFileName;
        if (isPendingAvatarRecord(fileName)) {
            return fileName;
        }
        return AVATAR_PENDING_FILE_PREFIX + fileName;
    }

    private String extractExtFromObjectKey(String objectKey) {
        if (objectKey == null) {
            return "";
        }
        int index = objectKey.lastIndexOf('.');
        if (index < 0 || index >= objectKey.length() - 1) {
            return "";
        }
        return objectKey.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private void validateStoredExtension(String ext, String objectKey) {
        try {
            uploadValidationService.validateImageExtension(ext);
        } catch (BusinessException ex) {
            storageFacade.delete(objectKey);
            throw ex;
        }
    }

    @Operation(summary = "绑定邮箱（无邮箱用户）")
    @PostMapping("/bind-email")
    @RateLimit(key = "user-bind-email", capacity = 8, seconds = 300)
    @AuditLog(module = "个人中心", operation = "UPDATE", description = "绑定邮箱")
    public Result<Void> bindEmail(@RequestBody Map<String, String> body, HttpServletRequest request) {
        StpUtil.checkLogin();
        dynamicRateLimitPolicyService.enforcePerIp(
                "user-bind-email",
                USER_BIND_EMAIL_RATE_LIMIT_KEY,
                8,
                1,
                60,
                300,
                IpUtil.getClientIp(request),
                "绑定邮箱操作过于频繁，请稍后再试"
        );
        Long userId = StpUtil.getLoginIdAsLong();
        String email = normalizeEmailField(body, "email");
        String code = normalizeCodeField(body, "code");
        if (!emailCodeService.verifyCode(email, "bind", code)) {
            throw new BusinessException(ResultCode.VERIFY_CODE_INVALID);
        }
        userService.bindEmail(userId, email);
        return Result.success();
    }

    @Operation(summary = "换绑邮箱")
    @PostMapping("/change-email")
    @RateLimit(key = "user-change-email", capacity = 8, seconds = 300)
    @AuditLog(module = "个人中心", operation = "UPDATE", description = "换绑邮箱")
    public Result<Void> changeEmail(@RequestBody Map<String, String> body, HttpServletRequest request) {
        StpUtil.checkLogin();
        dynamicRateLimitPolicyService.enforcePerIp(
                "user-change-email",
                USER_CHANGE_EMAIL_RATE_LIMIT_KEY,
                8,
                1,
                60,
                300,
                IpUtil.getClientIp(request),
                "换绑邮箱操作过于频繁，请稍后再试"
        );
        Long userId = StpUtil.getLoginIdAsLong();
        String newEmail = normalizeEmailField(body, "email");
        String code = normalizeCodeField(body, "code");
        if (!emailCodeService.verifyCode(newEmail, "change-email", code)) {
            throw new BusinessException(ResultCode.VERIFY_CODE_INVALID);
        }
        userService.changeEmail(userId, newEmail);
        return Result.success();
    }

    @Operation(summary = "设置密码（无密码用户）- 需要邮箱验证码二次验证")
    @PostMapping("/set-password")
    @RateLimit(key = "user-set-password", capacity = 8, seconds = 300)
    @AuditLog(module = "个人中心", operation = "UPDATE", description = "设置密码")
    public Result<Void> setPassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        StpUtil.checkLogin();
        dynamicRateLimitPolicyService.enforcePerIp(
                "user-set-password",
                USER_SET_PASSWORD_RATE_LIMIT_KEY,
                8,
                1,
                60,
                300,
                IpUtil.getClientIp(request),
                "设置密码操作过于频繁，请稍后再试"
        );
        Long userId = StpUtil.getLoginIdAsLong();

        // 获取当前用户邮箱来验证验证码
        var profile = userService.getProfile(userId);
        if (profile.getEmail() == null || profile.getEmail().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先绑定邮箱");
        }

        String code = normalizeCodeField(body, "code");
        String password = getRequiredPassword(body, "password");

        // 验证邮箱验证码
        if (!emailCodeService.verifyCode(profile.getEmail(), "reset-pwd", code)) {
            throw new BusinessException(ResultCode.VERIFY_CODE_INVALID);
        }

        // 直接使用明文密码
        String decryptedPassword = password;
        userService.setPassword(userId, decryptedPassword);
        return Result.success();
    }

    @Operation(summary = "重置密码（通过邮箱验证码）")
    @PostMapping("/reset-password")
    @RateLimit(key = "user-reset-password", capacity = 8, seconds = 300)
    @AuditLog(module = "个人中心", operation = "UPDATE", description = "重置密码")
    public Result<Void> resetPassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        StpUtil.checkLogin();
        dynamicRateLimitPolicyService.enforcePerIp(
                "user-reset-password",
                USER_RESET_PASSWORD_RATE_LIMIT_KEY,
                8,
                1,
                60,
                300,
                IpUtil.getClientIp(request),
                "重置密码操作过于频繁，请稍后再试"
        );
        Long userId = StpUtil.getLoginIdAsLong();
        String code = normalizeCodeField(body, "code");
        String newPassword = getRequiredPassword(body, "password");
        // 获取当前用户邮箱来验证验证码
        var profile = userService.getProfile(userId);
        if (profile.getEmail() == null || profile.getEmail().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先绑定邮箱");
        }
        if (!emailCodeService.verifyCode(profile.getEmail(), "reset-pwd", code)) {
            throw new BusinessException(ResultCode.VERIFY_CODE_INVALID);
        }
        // 直接使用明文密码
        String decryptedPassword = newPassword;
        userService.resetPassword(userId, decryptedPassword);
        return Result.success();
    }

    private String normalizeEmailField(Map<String, String> body, String field) {
        String email = getRequiredText(body, field, "邮箱不能为空");
        if (email.length() > MAX_EMAIL_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "邮箱长度不能超过" + MAX_EMAIL_LENGTH + "个字符");
        }
        if (!ValidateUtil.isValidEmail(email)) {
            throw new BusinessException(ResultCode.EMAIL_INVALID);
        }
        return email.toLowerCase(Locale.ROOT);
    }

    private String normalizeCodeField(Map<String, String> body, String field) {
        String code = getRequiredText(body, field, "验证码不能为空");
        if (code.length() > MAX_CODE_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "验证码格式不正确");
        }
        return code;
    }

    private String getRequiredPassword(Map<String, String> body, String field) {
        if (body == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "请求参数不能为空");
        }
        String value = body.get(field);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "密码不能为空");
        }
        if (value.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "密码长度不能超过" + MAX_PASSWORD_LENGTH + "个字符");
        }
        return value;
    }

    private String getRequiredText(Map<String, String> body, String field, String message) {
        if (body == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "请求参数不能为空");
        }
        String value = body.get(field);
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, message);
        }
        return value.trim();
    }
}
