package com.blog.infra.captcha.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CaptchaRiskContext {
    private String clientIp;
    private String deviceFingerprint;
    private String sessionId;
}
