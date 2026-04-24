package com.blog.api.crawler.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.api.crawler.dto.*;
import com.blog.api.crawler.entity.CrawlerCandidate;
import com.blog.api.crawler.entity.CrawlerPushRecord;
import com.blog.api.crawler.mapper.CrawlerCandidateMapper;
import com.blog.api.crawler.mapper.CrawlerPushRecordMapper;
import com.blog.api.crawler.model.CrawlerPushRecordState;
import com.blog.api.crawler.model.CrawlerTaskItemState;
import com.blog.api.security.UploadValidationService;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.dto.PostCreateRequest;
import com.blog.content.dto.PostVO;
import com.blog.content.entity.Category;
import com.blog.content.entity.Tag;
import com.blog.content.mapper.CategoryMapper;
import com.blog.content.mapper.TagMapper;
import com.blog.content.service.CategoryService;
import com.blog.content.service.PostService;
import com.blog.content.service.TagService;
import com.blog.infra.oss.StorageFacade;
import com.blog.system.service.SystemConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerIngestionService {

    private final CrawlerCandidateMapper crawlerCandidateMapper;
    private final CrawlerPushRecordMapper crawlerPushRecordMapper;
    private final PostService postService;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;
    private final StorageFacade storageFacade;
    private final UploadValidationService uploadValidationService;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final CategoryService categoryService;
    private final TagService tagService;

    @Transactional(rollbackFor = Exception.class)
    public CrawlerCandidateIngestResponse ingestCandidates(CrawlerCandidateIngestRequest request, String deviceId) {
        CrawlerCandidateIngestResponse response = new CrawlerCandidateIngestResponse();
        response.setIngestRequestId("ig-" + System.currentTimeMillis());
        List<CrawlerIngestAcceptedItemDTO> accepted = new ArrayList<>();
        List<CrawlerIngestRejectedItemDTO> rejected = new ArrayList<>();

        for (CrawlerCandidateItemDTO item : request.getItems()) {
            CrawlerCandidate existingByItemKey = crawlerCandidateMapper.selectByItemIdempotencyKey(item.getItemIdempotencyKey());
            if (existingByItemKey != null) {
                if (!StrUtil.equals(existingByItemKey.getNormalizedUrl(), item.getNormalizedUrl())) {
                    rejected.add(buildRejected(item.getItemIdempotencyKey(), "idempotency_conflict", "itemIdempotencyKey 对应内容冲突", existingByItemKey));
                    continue;
                }
                accepted.add(buildAccepted(existingByItemKey));
                continue;
            }

            CrawlerCandidate duplicateByUrl = crawlerCandidateMapper.selectByNormalizedUrl(item.getNormalizedUrl());
            if (duplicateByUrl != null) {
                accepted.add(buildAccepted(item.getItemIdempotencyKey(), duplicateByUrl));
                continue;
            }

            if (StrUtil.isNotBlank(item.getContentFingerprint())) {
                CrawlerCandidate duplicateByFingerprint = crawlerCandidateMapper.selectByContentFingerprint(item.getContentFingerprint());
                if (duplicateByFingerprint != null) {
                    rejected.add(buildRejected(item.getItemIdempotencyKey(), "duplicate_content", "contentFingerprint 已存在", duplicateByFingerprint));
                    continue;
                }
            }

            CrawlerCandidate candidate = new CrawlerCandidate();
            candidate.setRequestIdempotencyKey(request.getIdempotencyKey());
            candidate.setItemIdempotencyKey(item.getItemIdempotencyKey());
            candidate.setWorkerRunId(request.getWorkerRunId());
            candidate.setDeviceId(deviceId);
            candidate.setExternalUrl(item.getExternalUrl());
            candidate.setNormalizedUrl(item.getNormalizedUrl());
            candidate.setSourceSite(item.getSourceSite());
            candidate.setTitle(item.getTitle());
            candidate.setSummary(item.getSummary());
            candidate.setContentMarkdown(item.getContentMarkdown());
            candidate.setCoverImage(item.getCoverImage());
            candidate.setImageRefsJson(toJson(item.getImageRefs()));
            candidate.setContentFingerprint(item.getContentFingerprint());
            candidate.setPublishedAt(item.getPublishedAt());
            candidate.setAuthor(item.getAuthor());
            candidate.setMetadataJson(toJson(item.getMetadata()));
            candidate.setState(CrawlerTaskItemState.REVIEW_PENDING.name().toLowerCase());
            crawlerCandidateMapper.insert(candidate);
            accepted.add(buildAccepted(candidate));
        }

        response.setAccepted(accepted);
        response.setRejected(rejected);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public CrawlerPushResultUpsertResponse upsertPushResult(CrawlerPushResultUpsertRequest request, String deviceId) {
        CrawlerCandidate candidate = crawlerCandidateMapper.selectById(request.getCandidateId());
        if (candidate == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "候选内容不存在");
        }

        LocalDateTime pushTime = request.getPushedAt() == null ? LocalDateTime.now() : request.getPushedAt();

        CrawlerPushRecord record = crawlerPushRecordMapper.selectByPushIdempotencyKey(request.getPushIdempotencyKey());
        if (record == null) {
            record = new CrawlerPushRecord();
            record.setPushIdempotencyKey(request.getPushIdempotencyKey());
            record.setCandidateId(request.getCandidateId());
            record.setDeviceId(deviceId);
            record.setRetryCount(0);
            applyPushRecordFields(record, request);
            crawlerPushRecordMapper.insert(record);
        } else {
            if (!record.getCandidateId().equals(request.getCandidateId())) {
                throw new BusinessException(ResultCode.DUPLICATE, "pushIdempotencyKey 与 candidateId 冲突");
            }
            applyPushRecordFields(record, request);
            crawlerPushRecordMapper.updateById(record);
        }

        candidate.setTargetDraftId(request.getTargetDraftId());
        candidate.setPushMessage(request.getMessage());
        candidate.setLastPushedAt(pushTime);
        candidate.setUpdateTime(pushTime);
        candidate.setState(resolveCandidateStateByPushStatus(request.getStatus()));
        crawlerCandidateMapper.updateById(candidate);

        CrawlerPushResultUpsertResponse response = new CrawlerPushResultUpsertResponse();
        response.setPushRecordId(record.getId());
        response.setStatus(record.getStatus());
        response.setTargetDraftId(record.getTargetDraftId());
        return response;
    }

    public CrawlerDraftPushResponse pushCandidatesToDraft(CrawlerDraftPushRequest request, String deviceId) {
        Long ownerUserId = (long) systemConfigService.getIntValue("crawler_draft_owner_user_id", 1);
        String pushMode = StrUtil.blankToDefault(request.getPushMode(), "skip");
        List<CrawlerDraftPushItemResultDTO> results = new ArrayList<>();
        int successCount = 0;

        for (Long candidateId : request.getCandidateIds()) {
            CrawlerDraftPushItemResultDTO result = new CrawlerDraftPushItemResultDTO();
            result.setCandidateId(candidateId);
            CrawlerCandidate candidate = crawlerCandidateMapper.selectById(candidateId);
            if (candidate == null) {
                result.setStatus("failed");
                result.setMessage("候选内容不存在");
                results.add(result);
                continue;
            }

            if (CrawlerTaskItemState.REJECTED.name().equalsIgnoreCase(candidate.getState())) {
                result.setStatus("failed");
                result.setMessage("候选内容已拒绝，不能推送");
                results.add(result);
                continue;
            }

            try {
                PostCreateRequest draftRequest = buildDraftCreateRequest(candidate);
                LocalDateTime pushTime = LocalDateTime.now();
                Long draftId = createOrUpdateDraft(candidate, draftRequest, ownerUserId, pushMode);

                String pushKey = "push:" + deviceId + ":" + candidateId + ":v1";
                CrawlerPushResultUpsertRequest upsertRequest = new CrawlerPushResultUpsertRequest();
                upsertRequest.setPushIdempotencyKey(pushKey);
                upsertRequest.setCandidateId(candidateId);
                upsertRequest.setTargetDraftId(draftId);
                upsertRequest.setStatus(CrawlerPushRecordState.SUCCEEDED.name().toLowerCase());
                upsertRequest.setMessage("draft upsert success");
                upsertRequest.setPushedAt(pushTime);
                upsertPushResult(upsertRequest, deviceId);

                result.setStatus("succeeded");
                result.setDraftId(draftId);
                result.setMessage("推送草稿成功");
                result.setPushedAt(pushTime);
                successCount++;
            } catch (Exception ex) {
                log.warn("推送草稿失败: candidateId={}, msg={}", candidateId, ex.getMessage());
                result.setStatus("failed");
                result.setMessage(StrUtil.blankToDefault(ex.getMessage(), "推送失败"));
                result.setPushedAt(LocalDateTime.now());

                CrawlerPushResultUpsertRequest upsertRequest = new CrawlerPushResultUpsertRequest();
                upsertRequest.setPushIdempotencyKey("push:" + deviceId + ":" + candidateId + ":v1");
                upsertRequest.setCandidateId(candidateId);
                upsertRequest.setStatus(CrawlerPushRecordState.FAILED.name().toLowerCase());
                upsertRequest.setMessage(result.getMessage());
                upsertRequest.setPushedAt(LocalDateTime.now());
                upsertPushResult(upsertRequest, deviceId);
            }
            results.add(result);
        }

        CrawlerDraftPushResponse response = new CrawlerDraftPushResponse();
        response.setTotal(request.getCandidateIds().size());
        response.setSucceeded(successCount);
        response.setFailed(request.getCandidateIds().size() - successCount);
        response.setResults(results);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePushCallback(CrawlerPushCallbackRequest request) {
        CrawlerPushRecord record = crawlerPushRecordMapper.selectByPushIdempotencyKey(request.getPushIdempotencyKey());
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "推送记录不存在");
        }
        record.setStatus(request.getState());
        record.setErrorCode(request.getErrorCode());
        record.setErrorMessage(request.getErrorMessage());
        record.setPushedAt(request.getUpdatedAt() == null ? LocalDateTime.now() : request.getUpdatedAt());
        crawlerPushRecordMapper.updateById(record);
    }

    public CrawlerAssetUploadResponse uploadCrawlerAsset(MultipartFile file) {
        if (!storageFacade.isStorageEnabled()) {
            throw new BusinessException(ResultCode.FAIL, "文件上传服务未启用");
        }
        try {
            uploadValidationService.validateImage(file);
            String url = storageFacade.upload(file.getInputStream(), file.getOriginalFilename(), file.getSize());
            String objectKey = storageFacade.extractObjectKey(url);
            CrawlerAssetUploadResponse response = new CrawlerAssetUploadResponse();
            response.setUrl(url);
            response.setObjectKey(objectKey);
            return response;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.FAIL, "采集图片上传失败: " + ex.getMessage());
        }
    }

    private CrawlerIngestAcceptedItemDTO buildAccepted(CrawlerCandidate candidate) {
        return buildAccepted(candidate.getItemIdempotencyKey(), candidate);
    }

    private CrawlerIngestAcceptedItemDTO buildAccepted(String itemIdempotencyKey, CrawlerCandidate candidate) {
        CrawlerIngestAcceptedItemDTO accepted = new CrawlerIngestAcceptedItemDTO();
        accepted.setItemIdempotencyKey(itemIdempotencyKey);
        accepted.setCandidateId(candidate.getId());
        accepted.setState(candidate.getState());
        return accepted;
    }

    private CrawlerIngestRejectedItemDTO buildRejected(String itemIdempotencyKey, String reasonCode, String reasonMessage, CrawlerCandidate duplicateCandidate) {
        CrawlerIngestRejectedItemDTO rejected = new CrawlerIngestRejectedItemDTO();
        rejected.setItemIdempotencyKey(itemIdempotencyKey);
        rejected.setReasonCode(reasonCode);
        rejected.setReasonMessage(reasonMessage);
        if (duplicateCandidate != null) {
            rejected.setDuplicateCandidateId(duplicateCandidate.getId());
            rejected.setDuplicateTitle(duplicateCandidate.getTitle());
            rejected.setDuplicateExternalUrl(duplicateCandidate.getExternalUrl());
            rejected.setDuplicateNormalizedUrl(duplicateCandidate.getNormalizedUrl());
            rejected.setDuplicateLastPushedAt(duplicateCandidate.getLastPushedAt());
            rejected.setDuplicateTargetDraftId(duplicateCandidate.getTargetDraftId());
        }
        return rejected;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "JSON 序列化失败");
        }
    }

    private void applyPushRecordFields(CrawlerPushRecord record, CrawlerPushResultUpsertRequest request) {
        record.setStatus(request.getStatus());
        record.setTargetDraftId(request.getTargetDraftId());
        record.setMessage(request.getMessage());
        record.setPushedAt(request.getPushedAt() == null ? LocalDateTime.now() : request.getPushedAt());
        if (CrawlerPushRecordState.FAILED.name().equalsIgnoreCase(request.getStatus())) {
            Integer retryCount = record.getRetryCount() == null ? 0 : record.getRetryCount();
            record.setRetryCount(retryCount + 1);
            record.setErrorMessage(request.getMessage());
        }
    }

    private String resolveCandidateStateByPushStatus(String status) {
        if (CrawlerPushRecordState.SUCCEEDED.name().equalsIgnoreCase(status)) {
            return CrawlerTaskItemState.PUSHED.name().toLowerCase();
        }
        if (CrawlerPushRecordState.FAILED.name().equalsIgnoreCase(status)) {
            return CrawlerTaskItemState.FAILED.name().toLowerCase();
        }
        return CrawlerTaskItemState.APPROVED.name().toLowerCase();
    }

    private PostCreateRequest buildDraftCreateRequest(CrawlerCandidate candidate) {
        PostCreateRequest request = new PostCreateRequest();
        request.setTitle(truncate(candidate.getTitle(), 200));
        request.setSummary(truncate(candidate.getSummary(), 500));
        request.setCoverImage(candidate.getCoverImage());
        request.setContent(StrUtil.blankToDefault(candidate.getContentMarkdown(), ""));
        request.setStatus("draft");
        applyTaxonomyFromMetadata(request, parseMetadata(candidate.getMetadataJson()));
        request.setIsTop(false);
        return request;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Long createOrUpdateDraft(CrawlerCandidate candidate, PostCreateRequest draftRequest, Long ownerUserId, String pushMode) {
        if (Objects.equals(pushMode, "create_new_draft") || candidate.getTargetDraftId() == null) {
            return postService.create(draftRequest, ownerUserId).getId();
        }

        try {
            return postService.update(candidate.getTargetDraftId(), draftRequest, ownerUserId).getId();
        } catch (BusinessException ex) {
            if (ex.getCode() != ResultCode.NOT_FOUND.getCode()) {
                throw ex;
            }
            log.warn("目标草稿不存在，改为新建草稿: candidateId={}, targetDraftId={}", candidate.getId(), candidate.getTargetDraftId());
            return postService.create(draftRequest, ownerUserId).getId();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public CrawlerCleanupResponse cleanupHistoricalData(CrawlerCleanupRequest request) {
        int candidateRetentionDays = request.getCandidateRetentionDays() == null ? 30 : Math.max(request.getCandidateRetentionDays(), 1);
        int pushRecordRetentionDays = request.getPushRecordRetentionDays() == null ? 90 : Math.max(request.getPushRecordRetentionDays(), 1);
        LocalDateTime candidateBefore = LocalDateTime.now().minusDays(candidateRetentionDays);
        LocalDateTime pushRecordBefore = LocalDateTime.now().minusDays(pushRecordRetentionDays);

        int deletedCandidates = crawlerCandidateMapper.delete(
                new LambdaQueryWrapper<CrawlerCandidate>()
                        .in(CrawlerCandidate::getState, List.of("failed", "rejected"))
                        .lt(CrawlerCandidate::getUpdateTime, candidateBefore)
        );
        int deletedPushRecords = crawlerPushRecordMapper.delete(
                new LambdaQueryWrapper<CrawlerPushRecord>()
                        .lt(CrawlerPushRecord::getUpdateTime, pushRecordBefore)
        );

        CrawlerCleanupResponse response = new CrawlerCleanupResponse();
        response.setDeletedCandidateCount(deletedCandidates);
        response.setDeletedPushRecordCount(deletedPushRecords);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCandidate(Long candidateId, CrawlerCandidateDeleteRequest request) {
        CrawlerCandidate candidate = crawlerCandidateMapper.selectById(candidateId);
        if (candidate == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "候选内容不存在");
        }
        if (Boolean.TRUE.equals(request.getDeleteDraft()) && candidate.getTargetDraftId() != null) {
            Long ownerUserId = (long) systemConfigService.getIntValue("crawler_draft_owner_user_id", 1);
            postService.delete(candidate.getTargetDraftId(), ownerUserId, true);
        }
        crawlerPushRecordMapper.delete(new LambdaQueryWrapper<CrawlerPushRecord>().eq(CrawlerPushRecord::getCandidateId, candidateId));
        crawlerCandidateMapper.deleteById(candidateId);
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (StrUtil.isBlank(metadataJson)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() {});
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private String getMetadataString(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private void applyTaxonomyFromMetadata(PostCreateRequest request, Map<String, Object> metadata) {
        List<Long> tagIds = new ArrayList<>();
        List<String> newTagNames = new ArrayList<>();
        Object rawTags = metadata.get("tags");
        if (rawTags instanceof List<?> tags) {
            for (Object item : tags) {
                String tagName = StrUtil.trim(String.valueOf(item));
                if (StrUtil.isBlank(tagName)) {
                    continue;
                }
                Tag existing = tagMapper.selectOne(
                        new LambdaQueryWrapper<Tag>().eq(Tag::getName, tagName).last("LIMIT 1")
                );
                if (existing != null) {
                    if (!tagIds.contains(existing.getId())) {
                        tagIds.add(existing.getId());
                    }
                    continue;
                }
                if (!newTagNames.contains(tagName)) {
                    newTagNames.add(tagName);
                }
            }
        }
        request.setTagIds(tagIds);
        request.setNewTagNames(newTagNames);

        String level1 = getMetadataString(metadata, "categoryLevel1");
        String level2 = getMetadataString(metadata, "categoryLevel2");
        if (StrUtil.isBlank(level1) && StrUtil.isBlank(level2)) {
            return;
        }

        Category topCategory = null;
        if (StrUtil.isNotBlank(level1)) {
            topCategory = categoryMapper.selectOne(
                    new LambdaQueryWrapper<Category>().eq(Category::getName, level1).eq(Category::getParentId, 0L).last("LIMIT 1")
            );
            if (topCategory == null) {
                topCategory = categoryService.create(level1, null, null, 0L, null);
            }
            request.setCategoryId(topCategory.getId());
        }

        if (StrUtil.isBlank(level2)) {
            return;
        }

        Long parentId = topCategory != null ? topCategory.getId() : 0L;
        Category subCategory = categoryMapper.selectOne(
                new LambdaQueryWrapper<Category>().eq(Category::getName, level2).eq(Category::getParentId, parentId).last("LIMIT 1")
        );
        if (subCategory == null) {
            subCategory = categoryService.create(level2, null, null, parentId, null);
        }
        if (parentId == 0L) {
            request.setCategoryId(subCategory.getId());
            return;
        }
        request.setSubCategoryId(subCategory.getId());
    }
}
