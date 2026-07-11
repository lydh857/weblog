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
    /** 生成验证码后最短可提交验证时间（毫秒），防止秒解脚本 */
    public static final long MIN_SOLVE_TIME_MS = 350;

    // ===== 轨迹分析阈值 =====
    public static final int MIN_TRACK_POINTS = 5;
    public static final long MIN_SLIDE_TIME_MS = 200;
    public static final long MAX_SLIDE_TIME_MS = 30_000;
    /** 轨迹整体最小水平位移 */
    public static final double MIN_HORIZONTAL_DISPLACEMENT = 40.0;
    /** 单步位移占整体位移比例上限，防止瞬移轨迹 */
    public static final double MAX_SINGLE_STEP_DISPLACEMENT_RATIO = 0.70;
    /** 速度方差阈值（px/ms 单位），低于此值判定为机器人 */
    public static final double SPEED_VARIANCE_THRESHOLD = 0.00001;
    /** 轨迹首点 X 容差（像素） */
    public static final double TRACK_START_X_TOLERANCE = 3.0;
    /** 轨迹末点与滑块位置容差（像素） */
    public static final double TRACK_END_POSITION_TOLERANCE = 4.0;

    // ===== Redis Key 前缀 =====
    public static final String CAPTCHA_DATA_PREFIX = "captcha:data:";
    public static final String VERIFY_TOKEN_PREFIX = "captcha:verify:";
    public static final String BLACKLIST_PREFIX = "captcha:blacklist:";
    public static final String BLACKLIST_IP_PREFIX = "captcha:blacklist:ip:";
    public static final String BLACKLIST_DEVICE_PREFIX = "captcha:blacklist:device:";
    public static final String BLACKLIST_SESSION_PREFIX = "captcha:blacklist:session:";
    public static final String FAIL_COUNT_PREFIX = "captcha:fail_count:";
    public static final String FAIL_COUNT_IP_PREFIX = "captcha:fail_count:ip:";
    public static final String FAIL_COUNT_DEVICE_PREFIX = "captcha:fail_count:device:";
    public static final String FAIL_COUNT_SESSION_PREFIX = "captcha:fail_count:session:";

    // ===== 黑名单配置 =====
    public static final int BLACKLIST_THRESHOLD = 10;
    public static final int BLACKLIST_DEVICE_THRESHOLD = 8;
    public static final int BLACKLIST_SESSION_THRESHOLD = 6;
    public static final int BLACKLIST_WINDOW_SECONDS = 600;
    public static final int BLACKLIST_DURATION_SECONDS = 3600;
}
