package com.blog.infra.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.infra.ai.entity.AiTokenLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI Token 用量日志 Mapper
 */
@Mapper
public interface AiTokenLogMapper extends BaseMapper<AiTokenLog> {

  /**
   * 按功能分组统计月度 token 用量（SQL 聚合，避免全量加载到内存）
   * 返回 List<Map>，每个 Map 包含 feature, totalInput, totalOutput
   */
  @Select("SELECT feature, " +
    "SUM(input_tokens) AS totalInput, " +
    "SUM(output_tokens) AS totalOutput " +
    "FROM t_ai_token_log " +
    "WHERE create_time BETWEEN #{start} AND #{end} " +
    "GROUP BY feature")
  List<Map<String, Object>> selectMonthlyUsageGroupByFeature(
    @Param("start") LocalDateTime start,
    @Param("end") LocalDateTime end);

  /**
   * 删除指定日期之前的历史日志（用于定期清理）
   */
  @org.apache.ibatis.annotations.Delete("DELETE FROM t_ai_token_log WHERE create_time < #{before}")
  int deleteBeforeDate(@Param("before") LocalDateTime before);
}
