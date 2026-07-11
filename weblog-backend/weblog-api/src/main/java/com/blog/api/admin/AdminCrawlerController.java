package com.blog.api.admin;

import com.blog.api.crawler.auth.CrawlerIntegrationAuthService;
import com.blog.api.crawler.dto.*;
import com.blog.api.crawler.service.CrawlerIngestionService;
import com.blog.common.result.Result;
import com.blog.infra.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.Map;

@Tag(name = "管理端-采集集成", description = "本地采集端入库与草稿推送")
@RestController
@RequestMapping("/api/admin/crawler/v1")
@RequiredArgsConstructor
public class AdminCrawlerController {

    private static final String HEADER_CRAWLER_TOKEN = "X-Crawler-Token";
    private static final String HEADER_CRAWLER_DEVICE_ID = "X-Crawler-Device-Id";

    private final CrawlerIntegrationAuthService crawlerIntegrationAuthService;
    private final CrawlerIngestionService crawlerIngestionService;

    @Operation(summary = "批量接收采集候选内容")
    @PostMapping("/candidates:ingest")
    @RateLimit(key = "crawler:ingest", capacity = 20, seconds = 60, perIp = true, message = "采集入库请求过于频繁")
    public Result<CrawlerCandidateIngestResponse> ingestCandidates(
            @RequestHeader(HEADER_CRAWLER_TOKEN) String crawlerToken,
            @RequestHeader(HEADER_CRAWLER_DEVICE_ID) String deviceId,
            @Valid @RequestBody CrawlerCandidateIngestRequest request) {
        crawlerIntegrationAuthService.checkIngestionEnabled();
        crawlerIntegrationAuthService.checkIntegrationCredential(crawlerToken, deviceId);
        return Result.success(crawlerIngestionService.ingestCandidates(request, deviceId));
    }

    @Operation(summary = "推送候选内容到草稿箱")
    @PostMapping("/drafts:push")
    @RateLimit(key = "crawler:push", capacity = 20, seconds = 60, perIp = true, message = "推送草稿请求过于频繁")
    public Result<CrawlerDraftPushResponse> pushDrafts(
            @RequestHeader(HEADER_CRAWLER_TOKEN) String crawlerToken,
            @RequestHeader(HEADER_CRAWLER_DEVICE_ID) String deviceId,
            @Valid @RequestBody CrawlerDraftPushRequest request) {
        crawlerIntegrationAuthService.checkIngestionEnabled();
        crawlerIntegrationAuthService.checkIntegrationCredential(crawlerToken, deviceId);
        return Result.success(crawlerIngestionService.pushCandidatesToDraft(request, deviceId));
    }

    @Operation(summary = "清理历史候选与推送记录")
    @PostMapping("/cleanup")
    @RateLimit(key = "crawler:cleanup", capacity = 5, seconds = 60, perIp = true, message = "清理请求过于频繁")
    public Result<CrawlerCleanupResponse> cleanupHistoricalData(
            @RequestHeader(HEADER_CRAWLER_TOKEN) String crawlerToken,
            @RequestHeader(HEADER_CRAWLER_DEVICE_ID) String deviceId,
            @RequestBody CrawlerCleanupRequest request) {
        crawlerIntegrationAuthService.checkIngestionEnabled();
        crawlerIntegrationAuthService.checkIntegrationCredential(crawlerToken, deviceId);
        return Result.success(crawlerIngestionService.cleanupHistoricalData(request));
    }

    @Operation(summary = "删除后端候选内容")
    @DeleteMapping("/candidates/{candidateId}")
    public Result<Map<String, Boolean>> deleteCandidate(
            @RequestHeader(HEADER_CRAWLER_TOKEN) String crawlerToken,
            @RequestHeader(HEADER_CRAWLER_DEVICE_ID) String deviceId,
            @PathVariable Long candidateId,
            @RequestBody CrawlerCandidateDeleteRequest request) {
        crawlerIntegrationAuthService.checkIngestionEnabled();
        crawlerIntegrationAuthService.checkIntegrationCredential(crawlerToken, deviceId);
        crawlerIngestionService.deleteCandidate(candidateId, request);
        return Result.success(Map.of("deleted", true));
    }

    @Operation(summary = "上报草稿推送结果")
    @PostMapping("/push-results:upsert")
    public Result<CrawlerPushResultUpsertResponse> upsertPushResult(
            @RequestHeader(HEADER_CRAWLER_TOKEN) String crawlerToken,
            @RequestHeader(HEADER_CRAWLER_DEVICE_ID) String deviceId,
            @Valid @RequestBody CrawlerPushResultUpsertRequest request) {
        crawlerIntegrationAuthService.checkIngestionEnabled();
        crawlerIntegrationAuthService.checkIntegrationCredential(crawlerToken, deviceId);
        return Result.success(crawlerIngestionService.upsertPushResult(request, deviceId));
    }

    @Operation(summary = "推送回调状态更新")
    @PostMapping("/push-callback")
    public Result<Map<String, Boolean>> pushCallback(
            @RequestHeader(HEADER_CRAWLER_TOKEN) String crawlerToken,
            @RequestHeader(HEADER_CRAWLER_DEVICE_ID) String deviceId,
            @Valid @RequestBody CrawlerPushCallbackRequest request) {
        crawlerIntegrationAuthService.checkIngestionEnabled();
        crawlerIntegrationAuthService.checkIntegrationCredential(crawlerToken, deviceId);
        crawlerIngestionService.updatePushCallback(request);
        return Result.success(Map.of("ack", true));
    }

    @Operation(summary = "上传采集图片并返回正式地址")
    @PostMapping("/assets:upload")
    @RateLimit(key = "crawler:upload", capacity = 30, seconds = 60, perIp = true, message = "上传请求过于频繁")
    public Result<CrawlerAssetUploadResponse> uploadAsset(
            @RequestHeader(HEADER_CRAWLER_TOKEN) String crawlerToken,
            @RequestHeader(HEADER_CRAWLER_DEVICE_ID) String deviceId,
            @RequestParam("file") MultipartFile file) {
        crawlerIntegrationAuthService.checkIngestionEnabled();
        crawlerIntegrationAuthService.checkIntegrationCredential(crawlerToken, deviceId);
        return Result.success(crawlerIngestionService.uploadCrawlerAsset(file));
    }
}
