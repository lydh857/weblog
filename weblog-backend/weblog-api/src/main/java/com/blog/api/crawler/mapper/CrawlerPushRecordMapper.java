package com.blog.api.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.api.crawler.entity.CrawlerPushRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CrawlerPushRecordMapper extends BaseMapper<CrawlerPushRecord> {

    @Select("SELECT * FROM t_crawler_push_record WHERE push_idempotency_key = #{pushIdempotencyKey} LIMIT 1")
    CrawlerPushRecord selectByPushIdempotencyKey(@Param("pushIdempotencyKey") String pushIdempotencyKey);
}
