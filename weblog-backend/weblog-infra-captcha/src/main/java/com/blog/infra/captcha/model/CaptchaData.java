package com.blog.infra.captcha.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis 中存储的验证码数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaData {
    private int targetPosition;
    private int puzzleY;
    private String puzzleShape;
    private String clientIp;
    private long createTime;
}
