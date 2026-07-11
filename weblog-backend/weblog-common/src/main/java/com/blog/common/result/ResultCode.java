package com.blog.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // 通用
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),

    // 认证相关 401xx
    UNAUTHORIZED(401, "未登录或Token已过期"),
    TOKEN_INVALID(40101, "Token无效"),
    TOKEN_EXPIRED(40102, "Token已过期"),
    ACCOUNT_LOCKED(40103, "账号已锁定，请稍后重试"),

    // 权限相关 403xx
    FORBIDDEN(403, "无权限访问"),
    ROLE_DENIED(40301, "角色权限不足"),

    // 参数相关 400xx
    BAD_REQUEST(400, "请求参数错误"),
    PARAM_MISSING(40001, "缺少必要参数"),
    PARAM_INVALID(40002, "参数格式不正确"),
    EMAIL_INVALID(40003, "邮箱格式不正确"),
    PASSWORD_WEAK(40004, "密码强度不足"),

    // 资源相关 404xx
    NOT_FOUND(404, "资源不存在"),
    USER_NOT_FOUND(40401, "用户不存在"),
    POST_NOT_FOUND(40402, "文章不存在"),
    CATEGORY_NOT_FOUND(40403, "分类不存在"),

    // 冲突 409xx
    DUPLICATE(409, "数据已存在"),
    EMAIL_EXISTS(40901, "邮箱已注册"),
    NAME_EXISTS(40902, "名称已存在"),
    SLUG_EXISTS(40903, "别名已存在"),

    // 限流 429
    RATE_LIMIT(429, "请求过于频繁，请稍后重试"),

    // 验证码相关 42xxx
    VERIFY_CODE_INVALID(42001, "验证码错误或已过期"),
    VERIFY_CODE_SEND_FAIL(42002, "验证码发送失败"),
    MAIL_NOT_CONFIGURED(42003, "邮件服务未配置"),

    // 文件相关 415xx
    FILE_TYPE_NOT_ALLOWED(41501, "不支持的文件类型"),
    FILE_TOO_LARGE(41502, "文件大小超出限制"),
    FILE_MAGIC_MISMATCH(41503, "文件类型验证失败"),

    // AI 相关 50xxx
    AI_DISABLED(50001, "AI 功能已关闭"),
    AI_FEATURE_DISABLED(50002, "该 AI 功能已关闭"),
    AI_OVER_LIMIT(50003, "本月 AI 用量已达上限"),
    AI_TIMEOUT(50004, "AI 响应超时"),
    AI_API_ERROR(50005, "AI 服务暂时不可用"),
    AI_CONTENT_TOO_SHORT(50006, "文章内容过短，请先完善内容");

    private final int code;
    private final String message;
}
