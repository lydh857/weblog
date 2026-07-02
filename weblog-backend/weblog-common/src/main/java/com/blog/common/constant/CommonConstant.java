package com.blog.common.constant;

/**
 * 通用常量
 */
public final class CommonConstant {

    private CommonConstant() {}

    // ========== 用户角色 ==========
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_USER = "user";

    // ========== 文章状态 ==========
    public static final String POST_STATUS_DRAFT = "draft";
    public static final String POST_STATUS_PUBLISHED = "published";
    public static final String POST_STATUS_SCHEDULED = "scheduled";

    // ========== 评论状态 ==========
    public static final String COMMENT_PENDING = "pending";
    public static final String COMMENT_APPROVED = "approved";
    public static final String COMMENT_REJECTED = "rejected";
    public static final String COMMENT_SPAM = "spam";

    // ========== 个人信息审核状态 ==========
    public static final String PROFILE_REVIEW_PENDING = "pending";
    public static final String PROFILE_REVIEW_APPROVED = "approved";
    public static final String PROFILE_REVIEW_REJECTED = "rejected";

    // ========== 分页默认值 ==========
    public static final long DEFAULT_PAGE = 1;
    public static final long DEFAULT_PAGE_SIZE = 10;
    public static final long MAX_PAGE_SIZE = 100;

    // ========== 批量操作 ==========
    public static final int MAX_BATCH_SIZE = 50;

    // ========== 文件上传 ==========
    public static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB
    public static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024; // 2MB

    // ========== Redis Key 前缀 ==========
    public static final String REDIS_PREFIX = "blog:";
    public static final String REDIS_TOKEN_BLACKLIST = REDIS_PREFIX + "token:blacklist:";
    public static final String REDIS_LOGIN_FAILED = REDIS_PREFIX + "login:failed:";
    public static final String REDIS_LOGIN_FAILED_IP = REDIS_PREFIX + "login:failed:ip:";
    public static final String REDIS_RATE_LIMIT = REDIS_PREFIX + "ratelimit:";
    public static final String REDIS_VIEW_COUNT = REDIS_PREFIX + "view:count:";
    public static final String REDIS_LIKE_COUNT = REDIS_PREFIX + "like:count:";
    public static final String REDIS_DEVICE_VIEW = REDIS_PREFIX + "device:view:";
    public static final String REDIS_EMAIL_CODE = REDIS_PREFIX + "email:code:";
    public static final String REDIS_EMAIL_CODE_COOLDOWN = REDIS_PREFIX + "email:cooldown:";
    public static final String REDIS_EMAIL_CODE_ATTEMPTS = REDIS_PREFIX + "email:attempts:";

    // ========== 安全相关 ==========
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final long ACCOUNT_LOCK_MINUTES = 30;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int BCRYPT_COST = 12;
}
