package com.blog.api.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.api.crawler.entity.CrawlerCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CrawlerCandidateMapper extends BaseMapper<CrawlerCandidate> {

    @Select("SELECT * FROM t_crawler_candidate WHERE item_idempotency_key = #{itemIdempotencyKey} LIMIT 1")
    CrawlerCandidate selectByItemIdempotencyKey(@Param("itemIdempotencyKey") String itemIdempotencyKey);

    @Select("SELECT * FROM t_crawler_candidate WHERE normalized_url = #{normalizedUrl} LIMIT 1")
    CrawlerCandidate selectByNormalizedUrl(@Param("normalizedUrl") String normalizedUrl);

    @Select("SELECT * FROM t_crawler_candidate WHERE content_fingerprint = #{contentFingerprint} LIMIT 1")
    CrawlerCandidate selectByContentFingerprint(@Param("contentFingerprint") String contentFingerprint);
}
