package com.blog.api.scheduler;

import com.blog.content.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 文章定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostScheduler {

    private final PostService postService;

    /**
     * 每分钟扫描定时发布的文章，到期则自动发布
     * 注：synchronized 仅对单机部署有效，多实例部署需改为 Redis 分布式锁
     */
    @Scheduled(fixedRate = 60000)
    public synchronized void publishScheduledPosts() {
        int count = postService.publishScheduledPosts();
        if (count > 0) {
            log.info("定时发布任务完成，发布了 {} 篇文章", count);
        }
    }

    /**
     * 每天凌晨3点全量重建 Lucene 索引
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public synchronized void rebuildIndex() {
        log.info("开始全量重建 Lucene 索引...");
        int count = postService.rebuildIndex();
        log.info("Lucene 索引重建完成，共 {} 篇文章", count);
    }
}
