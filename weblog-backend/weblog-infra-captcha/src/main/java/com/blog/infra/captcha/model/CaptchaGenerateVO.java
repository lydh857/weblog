package com.blog.infra.captcha.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证码生成响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaGenerateVO {
    private String captchaToken;
    private String backgroundImage;
    private String puzzleImage;
    private int puzzleY;
    private int puzzleWidth;
    private int puzzleHeight;
}
