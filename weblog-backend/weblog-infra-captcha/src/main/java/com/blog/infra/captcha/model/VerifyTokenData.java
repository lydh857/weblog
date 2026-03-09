package com.blog.infra.captcha.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis 中存储的验证令牌数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyTokenData {
    private String clientIp;
    private long createTime;
}
