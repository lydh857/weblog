package com.blog.infra.captcha.constant;

/**
 * 验证码相关常量
 */
public final class CaptchaConstants {

    private CaptchaConstants() {}

    // ===== 图片配置 =====
    public static final int IMAGE_WIDTH = 320;
    public static final int IMAGE_HEIGHT = 200;
    public static final int PUZZLE_WIDTH = 60;
    public static final int PUZZLE_HEIGHT = 60;
    public static final String IMAGE_PATH = "captcha/images/";

    // ===== 验证配置 =====
    /** 滑动位置允许偏差（像素） */
    public static final int ACCURACY_THRESHOLD = 5;
    /** 验证码有效期（秒） */
    public static final int CAPTCHA_EXPIRE_SECONDS = 120;
    /** 验证通过凭证有效期（秒） */
    public static final int VERIFY_TOKEN_EXPIRE_SECONDS = 300;

    // ===== 轨迹分析阈值 =====
    public static final int MIN_TRACK_POINTS = 5;
    public static final long MIN_SLIDE_TIME_MS = 200;
    public static final long MAX_SLIDE_TIME_MS = 30_000;
    /** 速度方差阈值（px/ms 单位），低于此值判定为机器人 */
    public static final double SPEED_VARIANCE_THRESHOLD = 0.00001;

    // ===== Redis Key 前缀 =====
    public static final String CAPTCHA_DATA_PREFIX = "captcha:data:";
    public static final String VERIFY_TOKEN_PREFIX = "captcha:verify:";
    public static final String BLACKLIST_PREFIX = "captcha:blacklist:";
    public static final String FAIL_COUNT_PREFIX = "captcha:fail_count:";

    // ===== 黑名单配置 =====
    public static final int BLACKLIST_THRESHOLD = 10;
    public static final int BLACKLIST_WINDOW_SECONDS = 600;
    public static final int BLACKLIST_DURATION_SECONDS = 3600;
}
