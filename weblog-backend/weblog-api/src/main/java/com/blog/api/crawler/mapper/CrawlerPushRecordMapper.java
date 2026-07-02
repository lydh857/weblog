package com.blog.api.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.api.crawler.entity.CrawlerPushRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CrawlerPushRecordMapper extends BaseMapper<CrawlerPushRecord> {

    String COLUMNS = "id, candidate_id, push_idempotency_key, device_id, status, " +
            "target_draft_id, message, retry_count, error_code, error_message, " +
            "pushed_at, create_time, update_time";

    @Select("SELECT " + COLUMNS + " FROM t_crawler_push_record WHERE push_idempotency_key = #{pushIdempotencyKey} LIMIT 1")
    CrawlerPushRecord selectByPushIdempotencyKey(@Param("pushIdempotencyKey") String pushIdempotencyKey);
}
