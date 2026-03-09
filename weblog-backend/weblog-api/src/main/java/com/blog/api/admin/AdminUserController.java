package com.blog.api.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.common.util.PasswordUtil;
import com.blog.infra.security.audit.AuditLog;
import com.blog.system.dto.UserVO;
import com.blog.system.entity.User;
import com.blog.system.mapper.UserMapper;
import com.blog.system.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.blog.common.constant.CommonConstant.MAX_BATCH_SIZE;
import static com.blog.common.constant.CommonConstant.MAX_PAGE_SIZE;

/**
 * 管理端 - 用户管理
 */
@Slf4j
@Tag(name = "管理端-用户管理", description = "用户查询、状态管理")
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserMapper userMapper;
    private final EmailService emailService;

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
                "avatar", user.getAvatar() != null ? user.getAvatar() : "",
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

        pageSize = (int) Math.min(pageSize, MAX_PAGE_SIZE);
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

        IPage<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 转为 VO，隐藏 lastLoginIp、failedLoginAttempts 等敏感字段
        List<UserVO> records = page.getRecords().stream().map(u -> {
            UserVO vo = new UserVO();
            vo.setId(u.getId());
            vo.setEmail(u.getEmail());
            vo.setNickname(u.getNickname());
            vo.setAvatar(u.getAvatar());
            vo.setBio(u.getBio());
            vo.setRole(u.getRole());
            vo.setStatus(u.getStatus());
            vo.setLastLoginTime(u.getLastLoginTime());
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
    @AuditLog(module = "用户管理", operation = "UPDATE", description = "更新用户状态")
    public Result<Void> updateStatus(@PathVariable Long userId,
                                     @RequestParam String status) {
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

    @Operation(summary = "重置用户密码（随机生成并邮件通知）")
    @PostMapping("/{userId}/reset-password")
    @AuditLog(module = "用户管理", operation = "UPDATE", description = "重置用户密码")
    public Result<Void> resetPassword(@PathVariable Long userId) {
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
    @AuditLog(module = "用户管理", operation = "UPDATE", description = "解锁用户")
    public Result<Void> unlockUser(@PathVariable Long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStatus, "enabled")
                .set(User::getFailedLoginAttempts, 0)
                .set(User::getLockUntil, null));
        log.info("管理员解锁用户: userId={}", userId);
        return Result.success();
    }

    @Operation(summary = "批量更新用户状态")
    @PutMapping("/batch/status")
    @AuditLog(module = "用户管理", operation = "UPDATE", description = "批量更新用户状态")
    public Result<Void> batchUpdateStatus(@RequestBody java.util.Map<String, Object> body) {
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

}
