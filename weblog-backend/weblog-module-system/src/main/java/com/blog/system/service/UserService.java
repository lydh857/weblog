package com.blog.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.PasswordUtil;
import com.blog.common.util.ValidateUtil;
import com.blog.system.dto.UpdateProfileRequest;
import com.blog.system.dto.UserProfileVO;
import com.blog.system.entity.User;
import com.blog.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    /**
     * 查询个人资料
     */
    public UserProfileVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return UserProfileVO.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .bio(user.getBio())
                .role(user.getRole())
                .hasPassword(user.getPassword() != null && !user.getPassword().isBlank())
                .needBindEmail(user.getEmail() == null || user.getEmail().isBlank())
                .createTime(user.getCreateTime())
                .build();
    }

    /**
     * 更新个人资料（昵称、简介）
     */
    public void updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId);

        boolean hasUpdate = false;
        if (req.getNickname() != null) {
            wrapper.set(User::getNickname, req.getNickname());
            hasUpdate = true;
        }
        if (req.getBio() != null) {
            wrapper.set(User::getBio, req.getBio());
            hasUpdate = true;
        }

        if (hasUpdate) {
            userMapper.update(null, wrapper);
            log.info("用户资料更新: userId={}", userId);
        }
    }

    /**
     * 更新头像URL
     */
    public void updateAvatar(Long userId, String avatarUrl) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getAvatar, avatarUrl));
        log.info("用户头像更新: userId={}", userId);
    }

    /**
     * 绑定邮箱（仅限当前无邮箱的用户，如 GitHub 登录用户）
     */
    public void bindEmail(Long userId, String email) {
        if (!ValidateUtil.isValidEmail(email)) {
            throw new BusinessException(ResultCode.EMAIL_INVALID);
        }
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已绑定邮箱，请使用换绑功能");
        }
        // 检查邮箱是否已被其他账号使用
        checkEmailNotUsed(email, userId);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId).set(User::getEmail, email));
        log.info("用户绑定邮箱: userId={}, email={}", userId, email);
    }

    /**
     * 换绑邮箱（已有邮箱的用户，需验证新邮箱验证码）
     * 如果该邮箱已被另一个 GitHub 关联账号占用，需要合并处理
     */
    public void changeEmail(Long userId, String newEmail) {
        if (!ValidateUtil.isValidEmail(newEmail)) {
            throw new BusinessException(ResultCode.EMAIL_INVALID);
        }
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (newEmail.equals(user.getEmail())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新邮箱与当前邮箱相同");
        }
        // 检查新邮箱是否已被其他账号使用
        checkEmailNotUsed(newEmail, userId);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId).set(User::getEmail, newEmail));
        log.info("用户换绑邮箱: userId={}, newEmail={}", userId, newEmail);
    }

    /**
     * 设置密码（仅限当前无密码的用户）
     */
    public void setPassword(Long userId, String password) {
        validatePasswordStrength(password);
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已设置密码，请使用重置密码功能");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId).set(User::getPassword, PasswordUtil.encode(password)));
        log.info("用户设置密码: userId={}", userId);
    }

    /**
     * 重置密码（通过邮箱验证码）
     */
    public void resetPassword(Long userId, String newPassword) {
        validatePasswordStrength(newPassword);
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId).set(User::getPassword, PasswordUtil.encode(newPassword)));
        log.info("用户重置密码: userId={}", userId);
    }

    private void checkEmailNotUsed(String email, Long excludeUserId) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email).ne(User::getId, excludeUserId));
        if (count > 0) {
            throw new BusinessException(ResultCode.EMAIL_EXISTS, "该邮箱已被其他账号使用");
        }
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8)
            throw new BusinessException(ResultCode.PASSWORD_WEAK, "密码至少8位");
        if (!password.matches(".*[A-Z].*"))
            throw new BusinessException(ResultCode.PASSWORD_WEAK, "密码需包含大写字母");
        if (!password.matches(".*[a-z].*"))
            throw new BusinessException(ResultCode.PASSWORD_WEAK, "密码需包含小写字母");
        if (!password.matches(".*\\d.*"))
            throw new BusinessException(ResultCode.PASSWORD_WEAK, "密码需包含数字");
    }
}
