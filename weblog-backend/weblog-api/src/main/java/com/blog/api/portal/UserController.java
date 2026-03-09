package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.content.service.OssResourceService;
import com.blog.infra.oss.OssService;
import com.blog.infra.security.audit.AuditLog;
import com.blog.system.dto.UpdateProfileRequest;
import com.blog.system.dto.UserProfileVO;
import com.blog.system.service.EmailCodeService;
import com.blog.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Autowired(required = false)
    private OssService ossService;

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

    @Operation(summary = "更新个人资料")
    @PutMapping("/profile")
    @AuditLog(module = "个人中心", operation = "UPDATE", description = "更新个人资料")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        userService.updateProfile(userId, req);
        return Result.success();
    }

    @Operation(summary = "上传头像")
    @PostMapping("/avatar")
    @AuditLog(module = "个人中心", operation = "UPDATE", description = "上传头像")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();

        if (ossService == null) {
            throw new BusinessException(ResultCode.FAIL, "OSS 未启用，暂不支持头像上传");
        }

        // 上传到 OSS（OssService 内部做魔数验证、大小限制等）
        String url = ossService.upload(file.getInputStream(),
                file.getOriginalFilename(), file.getSize());

        // 记录 OSS 资源
        if (ossResourceService != null) {
            String objectKey = url.contains("/images/")
                    ? url.substring(url.indexOf("/images/") + 1) : url;
            String ext = file.getOriginalFilename() != null
                    ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.') + 1)
                    : "";
            ossResourceService.record(
                    file.getOriginalFilename(), objectKey, file.getSize(),
                    ext, file.getContentType(), url, userId, "avatar");
        }

        // 更新用户头像字段
        userService.updateAvatar(userId, url);

        return Result.success(url);
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
