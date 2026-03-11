package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.content.service.OssResourceService;
import com.blog.infra.oss.LocalFileService;
import com.blog.infra.oss.OssService;
import com.blog.infra.security.audit.AuditLog;
import com.blog.system.dto.UpdateProfileRequest;
import com.blog.system.dto.UserProfileVO;
import com.blog.system.service.EmailCodeService;
import com.blog.system.service.UserProfileReviewService;
import com.blog.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 用户端 - 个人中心接口
 */
@Tag(name = "用户端-个人中心", description = "个人资料查询、更新、头像上传")
@RestController
@RequestMapping("/api/portal/user")
public class UserController {

    private static final String AVATAR_USAGE = "avatar";
    private static final String AVATAR_PENDING_FILE_PREFIX = "avatar_pending__";

    private final UserService userService;
    private final UserProfileReviewService userProfileReviewService;

    public UserController(UserService userService,
                          UserProfileReviewService userProfileReviewService) {
        this.userService = userService;
        this.userProfileReviewService = userProfileReviewService;
    }

    @Autowired(required = false)
    private OssService ossService;

    @Autowired(required = false)
    private LocalFileService localFileService;

    @Autowired(required = false)
    private OssResourceService ossResourceService;

    @Autowired
    private EmailCodeService emailCodeService;

    @Operation(summary = "获取个人资料")
    @GetMapping("/profile")
    public Result<UserProfileVO> getProfile() {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(userService.getProfile(userId));
    }

    @Operation(summary = "提交个人信息审核")
    @PutMapping("/profile")
    @AuditLog(module = "个人中心", operation = "UPDATE", description = "提交个人信息审核")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        userProfileReviewService.submitReview(userId, req);
        return Result.success();
    }

    @Operation(summary = "上传头像（提交审核）")
    @PostMapping("/avatar")
    @AuditLog(module = "个人中心", operation = "UPDATE", description = "提交头像审核")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();

        if (ossService == null && localFileService == null) {
            throw new BusinessException(ResultCode.FAIL, "文件上传服务未启用");
        }

        String url;
        String objectKey;

        if (ossService != null) {
            // OSS 模式
            url = ossService.upload(file.getInputStream(),
                    file.getOriginalFilename(), file.getSize());
            objectKey = url.contains("/images/")
                    ? url.substring(url.indexOf("/images/") + 1)
                    : ossService.extractObjectKey(url);
        } else {
            // 本地存储模式
            url = localFileService.upload(file.getInputStream(),
                    file.getOriginalFilename(), file.getSize());
            objectKey = localFileService.extractObjectKey(url);
        }

        // 资源服务未启用时，降级为直接更新头像
        if (ossResourceService == null) {
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
            } catch (Exception ignored) {}
        }

        // 记录待审核头像资源
        {
            String ext = "";
            if (file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")) {
                ext = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.') + 1);
            }
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

    @Operation(summary = "绑定邮箱（无邮箱用户）")
    @PostMapping("/bind-email")
    @AuditLog(module = "个人中心", operation = "UPDATE", description = "绑定邮箱")
    public Result<Void> bindEmail(@RequestBody Map<String, String> body) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        String email = body.get("email");
        String code = body.get("code");
        if (email == null || code == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "邮箱和验证码不能为空");
        }
        if (!emailCodeService.verifyCode(email, "bind", code)) {
            throw new BusinessException(ResultCode.VERIFY_CODE_INVALID);
        }
        userService.bindEmail(userId, email);
        return Result.success();
    }

    @Operation(summary = "换绑邮箱")
    @PostMapping("/change-email")
    @AuditLog(module = "个人中心", operation = "UPDATE", description = "换绑邮箱")
    public Result<Void> changeEmail(@RequestBody Map<String, String> body) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        String newEmail = body.get("email");
        String code = body.get("code");
        if (newEmail == null || code == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "邮箱和验证码不能为空");
        }
        if (!emailCodeService.verifyCode(newEmail, "change-email", code)) {
            throw new BusinessException(ResultCode.VERIFY_CODE_INVALID);
        }
        userService.changeEmail(userId, newEmail);
        return Result.success();
    }

    @Operation(summary = "设置密码（无密码用户）- 需要邮箱验证码二次验证")
    @PostMapping("/set-password")
    @AuditLog(module = "个人中心", operation = "UPDATE", description = "设置密码")
    public Result<Void> setPassword(@RequestBody Map<String, String> body) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();

        // 获取当前用户邮箱来验证验证码
        var profile = userService.getProfile(userId);
        if (profile.getEmail() == null || profile.getEmail().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先绑定邮箱");
        }

        String code = body.get("code");
        String password = body.get("password");

        if (code == null || password == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "验证码和密码不能为空");
        }

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
    @AuditLog(module = "个人中心", operation = "UPDATE", description = "重置密码")
    public Result<Void> resetPassword(@RequestBody Map<String, String> body) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        String code = body.get("code");
        String newPassword = body.get("password");
        if (code == null || newPassword == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "验证码和新密码不能为空");
        }
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
}
