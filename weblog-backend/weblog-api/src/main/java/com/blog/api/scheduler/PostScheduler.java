package com.blog.api.scheduler;

import com.blog.content.service.PostService;
import com.blog.infra.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 文章定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostScheduler {

    private static final String PUBLISH_LOCK_KEY = "scheduler:post:publish";
    private static final String REBUILD_INDEX_LOCK_KEY = "scheduler:post:rebuild-index";

    private final PostService postService;
    private final StringRedisTemplate redisTemplate;
    private final RedisService redisService;

    /**
     * 每分钟扫描定时发布的文章，到期则自动发布
     */
    @Scheduled(fixedRate = 60000)
    public void publishScheduledPosts() {
        String lockValue = acquireLock(PUBLISH_LOCK_KEY, Duration.ofMinutes(10));
        if (lockValue == null) {
            log.debug("定时发布任务跳过：未获取到分布式锁");
            return;
        }

        try {
            int count = postService.publishScheduledPosts();
            if (count > 0) {
                log.info("定时发布任务完成，发布了 {} 篇文章", count);
            }
        } catch (Exception e) {
            log.error("定时发布任务失败: {}", e.getMessage(), e);
        } finally {
            releaseLock(PUBLISH_LOCK_KEY, lockValue);
        }
    }

    /**
     * 每天凌晨3点全量重建 Lucene 索引
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void rebuildIndex() {
        String lockValue = acquireLock(REBUILD_INDEX_LOCK_KEY, Duration.ofMinutes(60));
        if (lockValue == null) {
            log.debug("Lucene 索引重建任务跳过：未获取到分布式锁");
            return;
        }

        try {
            log.info("开始全量重建 Lucene 索引...");
            int count = postService.rebuildIndex();
            log.info("Lucene 索引重建完成，共 {} 篇文章", count);
        } catch (Exception e) {
            log.error("Lucene 索引重建失败: {}", e.getMessage(), e);
        } finally {
            releaseLock(REBUILD_INDEX_LOCK_KEY, lockValue);
        }
    }

    private String acquireLock(String lockKey, Duration ttl) {
        String lockValue = UUID.randomUUID().toString();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, ttl);
        return Boolean.TRUE.equals(success) ? lockValue : null;
    }

    private void releaseLock(String lockKey, String lockValue) {
        redisService.releaseLock(lockKey, lockValue);
    }
}
