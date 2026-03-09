package com.blog.api.scheduler;

import com.blog.content.service.FriendLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 友链可达性定时检测
 * 每天凌晨3点自动检测
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FriendLinkCheckScheduler {

  private final FriendLinkService friendLinkService;

  @Scheduled(cron = "0 0 3 * * ?")
  public void checkFriendLinks() {
    log.info("开始定时检测友链可达性...");
    int changed = friendLinkService.checkAllLinks();
    log.info("友链定时检测完成，状态变更 {} 条", changed);
  }
}
