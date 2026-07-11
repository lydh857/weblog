package com.blog.api.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.api.crawler.entity.CrawlerCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CrawlerCandidateMapper extends BaseMapper<CrawlerCandidate> {

    String COLUMNS = "id, request_idempotency_key, item_idempotency_key, worker_run_id, device_id, " +
            "external_url, normalized_url, source_site, title, summary, content_markdown, " +
            "cover_image, image_refs_json, content_fingerprint, published_at, author, " +
            "metadata_json, state, target_draft_id, push_message, last_pushed_at, " +
            "create_time, update_time";

    @Select("SELECT " + COLUMNS + " FROM t_crawler_candidate WHERE item_idempotency_key = #{itemIdempotencyKey} LIMIT 1")
    CrawlerCandidate selectByItemIdempotencyKey(@Param("itemIdempotencyKey") String itemIdempotencyKey);

    @Select("SELECT " + COLUMNS + " FROM t_crawler_candidate WHERE normalized_url = #{normalizedUrl} LIMIT 1")
    CrawlerCandidate selectByNormalizedUrl(@Param("normalizedUrl") String normalizedUrl);

    @Select("SELECT " + COLUMNS + " FROM t_crawler_candidate WHERE content_fingerprint = #{contentFingerprint} LIMIT 1")
    CrawlerCandidate selectByContentFingerprint(@Param("contentFingerprint") String contentFingerprint);
}
