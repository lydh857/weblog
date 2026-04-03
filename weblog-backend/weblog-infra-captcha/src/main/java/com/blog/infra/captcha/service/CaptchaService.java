package com.blog.infra.captcha.service;

import com.blog.infra.captcha.model.CaptchaGenerateVO;
import com.blog.infra.captcha.model.CaptchaRiskContext;
import com.blog.infra.captcha.model.CaptchaVerifyRequest;
import com.blog.infra.captcha.model.CaptchaVerifyVO;

/**
 * 验证码服务接口
 */
public interface CaptchaService {

    /**
     * 生成验证码（含黑名单检查、图片生成、Redis 存储）
     */
    CaptchaGenerateVO generateCaptcha(CaptchaRiskContext context);

    /**
     * 验证滑块（数据获取删除、IP 校验、轨迹分析、位置校验、生成 verifyToken）
     */
    CaptchaVerifyVO verifyCaptcha(CaptchaVerifyRequest request, CaptchaRiskContext context);

    /**
     * 刷新验证码（删除旧数据、生成新验证码）
     */
    CaptchaGenerateVO refreshCaptcha(String oldToken, CaptchaRiskContext context);

    /**
     * 校验 Verify_Token（签名校验、IP 校验、一次性删除）
     *
     * @return true 表示有效
     */
    boolean validateVerifyToken(String verifyToken, String clientIp);

    /**
     * 校验 Verify_Token，无效时抛出 BusinessException
     */
    void validateVerifyTokenOrThrow(String verifyToken, String clientIp);
}
