package com.blog.infra.ai.service;

import com.blog.infra.ai.config.AiProperties;
import com.blog.infra.ai.dto.TokenUsageVO;
import com.blog.infra.ai.entity.AiTokenLog;
import com.blog.infra.ai.mapper.AiTokenLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Token 用量计量服务
 * <p>
 * 优化：record() 异步执行，日志先写入内存队列，定时批量刷入数据库
 */
@Slf4j
@Service
public class TokenMeterService {

  private final StringRedisTemplate redisTemplate;
  private final AiTokenLogMapper tokenLogMapper;
  private final AiProperties aiProperties;
  private final ApplicationContext applicationContext;

  public TokenMeterService(
      StringRedisTemplate redisTemplate,
      AiTokenLogMapper tokenLogMapper,
      AiProperties aiProperties,
      ApplicationContext applicationContext) {
    this.redisTemplate = redisTemplate;
    this.tokenLogMapper = tokenLogMapper;
    this.aiProperties = aiProperties;
    this.applicationContext = applicationContext;
  }

  /**
   * 延迟获取 AiConfigService，避免循环依赖
   */
  private AiConfigService getAiConfigService() {
    return applicationContext.getBean(AiConfigService.class);
  }

  private static final String REDIS_KEY_PREFIX = "ai:token:";
  private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

  /** 内存缓冲队列，避免高频 DB 写入 */
  private final ConcurrentLinkedQueue<AiTokenLog> logBuffer = new ConcurrentLinkedQueue<>();

  /**
   * 记录 token 消耗（异步，先写 Redis 实时计数 + 内存队列缓冲）
   */
  @Async
  public void record(String feature, int inputTokens, int outputTokens) {
    // Redis 记录月度汇总（实时）
    String monthKey = REDIS_KEY_PREFIX + LocalDate.now().format(MONTH_FMT);
    long total = (long) inputTokens + outputTokens;
    redisTemplate.opsForHash().increment(monthKey, feature + ":input", inputTokens);
    redisTemplate.opsForHash().increment(monthKey, feature + ":output", outputTokens);
    Long currentTotal = redisTemplate.opsForHash().increment(monthKey, "total", total);

    // 检查 key 是否已有 TTL，没有则设置（避免 key 永久累积）
    Long ttlSeconds = redisTemplate.getExpire(monthKey);
    if (ttlSeconds != null && ttlSeconds < 0) {
      // TTL 为 -1 表示无过期时间，-2 表示 key 不存在
      java.time.Duration ttl = java.time.Duration.between(
        LocalDateTime.now(),
        LocalDateTime.of(YearMonth.now().atEndOfMonth().plusDays(7), java.time.LocalTime.MIDNIGHT));
      redisTemplate.expire(monthKey, ttl);
    }

    // 写入内存缓冲队列（定时批量刷入 DB）
    AiTokenLog tokenLog = new AiTokenLog();
    tokenLog.setFeature(feature);
    tokenLog.setInputTokens(inputTokens);
    tokenLog.setOutputTokens(outputTokens);
    tokenLog.setModel(aiProperties.getModel());
    logBuffer.offer(tokenLog);
  }

  /**
   * 定时刷入数据库（每 30 秒）
   */
  @Scheduled(fixedDelay = 30000)
  public void flushLogBuffer() {
    if (logBuffer.isEmpty()) return;

    List<AiTokenLog> batch = new ArrayList<>();
    AiTokenLog item;
    while ((item = logBuffer.poll()) != null && batch.size() < 200) {
      batch.add(item);
    }

    if (!batch.isEmpty()) {
      try {
        for (AiTokenLog logItem : batch) {
          tokenLogMapper.insert(logItem);
        }
        log.debug("Token 日志批量写入: {} 条", batch.size());
      } catch (Exception e) {
        log.error("Token 日志批量写入失败，丢弃 {} 条记录", batch.size(), e);
      }
    }
  }

  /**
   * 查询月度用量统计（SQL 聚合，不加载全量日志到内存）
   */
  public TokenUsageVO getMonthlyUsage(String month) {
    if (month == null) {
      month = LocalDate.now().format(MONTH_FMT);
    }

    TokenUsageVO vo = new TokenUsageVO();
    vo.setMonth(month);

    YearMonth ym = YearMonth.parse(month, MONTH_FMT);
    LocalDateTime start = ym.atDay(1).atStartOfDay();
    LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59);

    // SQL 聚合查询，按 feature 分组
    List<Map<String, Object>> rows = tokenLogMapper.selectMonthlyUsageGroupByFeature(start, end);

    long totalInput = 0;
    long totalOutput = 0;
    Map<String, TokenUsageVO.FeatureUsage> breakdown = new HashMap<>();

    for (Map<String, Object> row : rows) {
      String feature = (String) row.get("feature");
      long input = row.get("totalInput") != null ? ((Number) row.get("totalInput")).longValue() : 0;
      long output = row.get("totalOutput") != null ? ((Number) row.get("totalOutput")).longValue() : 0;

      totalInput += input;
      totalOutput += output;

      TokenUsageVO.FeatureUsage fu = new TokenUsageVO.FeatureUsage();
      fu.setInputTokens(input);
      fu.setOutputTokens(output);
      breakdown.put(feature, fu);
    }

    vo.setTotalInput(totalInput);
    vo.setTotalOutput(totalOutput);
    vo.setFeatureBreakdown(breakdown);
    return vo;
  }

  /**
   * 检查当月是否超限
   */
  public boolean isOverLimit() {
    long limit = getAiConfigService().getMonthlyTokenLimit();
    if (limit <= 0) {
      return false;
    }
    String monthKey = REDIS_KEY_PREFIX + LocalDate.now().format(MONTH_FMT);
    Object totalObj = redisTemplate.opsForHash().get(monthKey, "total");
    if (totalObj == null) {
      return false;
    }
    long total = Long.parseLong(totalObj.toString());
    return total >= limit;
  }

  /**
   * 定时清理历史 token 日志（每天凌晨 3 点执行，保留最近 6 个月）
   */
  @Scheduled(cron = "0 0 3 * * ?")
  public void cleanupOldLogs() {
    try {
      LocalDateTime cutoff = LocalDateTime.now().minusMonths(6);
      int deleted = tokenLogMapper.deleteBeforeDate(cutoff);
      if (deleted > 0) {
        log.info("清理历史 token 日志: 删除 {} 条（截止 {}）", deleted, cutoff);
      }
    } catch (Exception e) {
      log.error("清理历史 token 日志失败", e);
    }
  }
}
