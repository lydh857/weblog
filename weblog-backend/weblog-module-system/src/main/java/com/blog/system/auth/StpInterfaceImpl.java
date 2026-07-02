package com.blog.system.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.blog.system.entity.User;
import com.blog.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限接口实现
 * 返回用户的角色和权限列表
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserMapper userMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 本项目使用角色控制，不使用细粒度权限
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = userMapper.selectById(Long.parseLong(loginId.toString()));
        if (user == null) {
            return Collections.emptyList();
        }
        return List.of(user.getRole());
    }
}
