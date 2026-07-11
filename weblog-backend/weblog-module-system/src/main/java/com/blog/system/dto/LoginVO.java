package com.blog.system.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录视图对象（用于自动登录）
 */
@Data
@Builder
public class LoginVO {
    private Long userId;
    private String email;
    private String nickname;
    private String avatar;
    private String role;
    private String token;
    private String rememberToken;
}
