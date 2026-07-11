package com.blog.system.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录成功返回数据（内部使用）
 */
@Data
@Builder
public class LoginDTO {
    private Long userId;
    private String email;
    private String nickname;
    private String avatar;
    private String role;
    private String token;
}
