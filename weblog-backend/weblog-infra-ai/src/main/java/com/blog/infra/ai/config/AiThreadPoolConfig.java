package com.blog.infra.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 模块专用线程池配置
 * <p>
 * 用于 SSE 流式调用、批量审核等异步任务，
 * 避免占用 ForkJoinPool.commonPool() 影响其他系统功能。
 */
@Configuration
public class AiThreadPoolConfig {

  @Value("${blog.ai.async.core-pool-size:2}")
  private int corePoolSize;

  @Value("${blog.ai.async.max-pool-size:4}")
  private int maxPoolSize;

  @Value("${blog.ai.async.queue-capacity:50}")
  private int queueCapacity;

  @Value("${blog.ai.async.keep-alive-seconds:60}")
  private int keepAliveSeconds;

  @Bean("aiExecutor")
  public Executor aiExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("ai-");
    executor.setKeepAliveSeconds(keepAliveSeconds);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    // 队列满时由调用线程执行，保证不丢失任务
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }
}
