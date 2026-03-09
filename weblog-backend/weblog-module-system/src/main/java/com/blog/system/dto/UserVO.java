package com.blog.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户列表 VO — 仅暴露安全字段，隐藏 lastLoginIp、failedLoginAttempts 等
 */
@Data
public class UserVO {
  private Long id;
  private String email;
  private String nickname;
  private String avatar;
  private String bio;
  private String role;
  private String status;
  private LocalDateTime lastLoginTime;
  private LocalDateTime createTime;
}
