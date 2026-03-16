package com.blog.interaction.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.blog.interaction.mapper.UserViewLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 阅读计数服务
 * - Redis INCR 实时计数
 * - 设备指纹防刷（UA + IP 哈希）
 * - 定时任务落库到 MySQL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadCountService {

    private final StringRedisTemplate redisTemplate;
    private final UserViewLogMapper userViewLogMapper;

    /** Redis key: 文章阅读数（实时） */
    private static final String KEY_VIEW_COUNT = "post:view:";
    /** Redis key: 设备今日已读文章集合 */
    private static final String KEY_DEVICE_READ = "device:read:";
    /** Redis key: 今日有阅读增量的文章集合 */
    private static final String KEY_DIRTY_POSTS = "post:view:dirty";

    /**
     * 记录一次阅读
     * @return true=有效阅读（计数+1），false=重复阅读（不计数）
     */
    public boolean recordView(Long postId, String userAgent, String ipAddress, Long userId) {
        String deviceHash = generateDeviceHash(userAgent, ipAddress);
        String today = LocalDate.now().toString();

        // 检查该设备今天是否已读过此文章
        String deviceKey = KEY_DEVICE_READ + deviceHash + ":" + today;
        Boolean alreadyRead = redisTemplate.opsForSet().isMember(deviceKey, postId.toString());
        if (Boolean.TRUE.equals(alreadyRead)) {
            return false;
        }

        // 标记已读，设置过期时间到明天
        redisTemplate.opsForSet().add(deviceKey, postId.toString());
        redisTemplate.expire(deviceKey, 2, TimeUnit.DAYS);

        // Redis 计数 +1
        redisTemplate.opsForValue().increment(KEY_VIEW_COUNT + postId);

        // 记录到脏数据集合（供定时任务落库）
        redisTemplate.opsForSet().add(KEY_DIRTY_POSTS, postId.toString());

        // 记录阅读日志到 MySQL（设备维度每日汇总）
        saveViewLog(deviceHash, userId, postId, ipAddress);

        return true;
    }

    /**
     * 获取文章实时阅读数（Redis 增量 + DB 基数）
     */
    public long getViewCount(Long postId) {
        String val = redisTemplate.opsForValue().get(KEY_VIEW_COUNT + postId);
        return val != null ? Long.parseLong(val) : 0;
    }

    /**
     * 获取所有有增量的文章ID及其Redis增量
     * @return postId -> increment
     */
    public Map<Long, Long> getDirtyPostCounts() {
        Set<String> dirtyIds = redisTemplate.opsForSet().members(KEY_DIRTY_POSTS);
        if (dirtyIds == null || dirtyIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new java.util.HashMap<>();
        for (String idStr : dirtyIds) {
            Long postId = Long.parseLong(idStr);
            String val = redisTemplate.opsForValue().get(KEY_VIEW_COUNT + postId);
            if (val != null) {
                result.put(postId, Long.parseLong(val));
            }
        }
        return result;
    }

    /**
     * 落库后清除 Redis 增量
     */
    public void clearDirtyCount(Long postId, long syncedCount) {
        // 用 DECRBY 减去已同步的数量（而非直接删除，避免丢失并发写入）
        redisTemplate.opsForValue().decrement(KEY_VIEW_COUNT + postId, syncedCount);
        redisTemplate.opsForSet().remove(KEY_DIRTY_POSTS, postId.toString());
    }

    /**
     * 生成设备指纹（UA + IP 的 MD5）
     */
    public String generateDeviceHash(String userAgent, String ipAddress) {
        String raw = (userAgent != null ? userAgent : "") + "|" + (ipAddress != null ? ipAddress : "");
        return DigestUtil.md5Hex(raw);
    }

    // ========== 私有方法 ==========

    private void saveViewLog(String deviceHash, Long userId, Long postId, String ipAddress) {
        LocalDate today = LocalDate.now();
        userViewLogMapper.upsertDailyView(deviceHash, userId, postId, today, ipAddress);
    }
}
