package com.blog.api.crawler.service;

import com.blog.api.crawler.dto.CrawlerCandidateIngestRequest;
import com.blog.api.crawler.dto.CrawlerCandidateItemDTO;
import com.blog.api.crawler.dto.CrawlerDraftPushRequest;
import com.blog.api.crawler.dto.CrawlerPushResultUpsertRequest;
import com.blog.api.crawler.entity.CrawlerCandidate;
import com.blog.api.crawler.entity.CrawlerPushRecord;
import com.blog.api.crawler.mapper.CrawlerCandidateMapper;
import com.blog.api.crawler.mapper.CrawlerPushRecordMapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.service.PostService;
import com.blog.content.mapper.CategoryMapper;
import com.blog.content.mapper.TagMapper;
import com.blog.content.service.CategoryService;
import com.blog.content.service.TagService;
import com.blog.content.dto.PostCreateRequest;
import com.blog.content.dto.PostVO;
import com.blog.infra.oss.StorageFacade;
import com.blog.system.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrawlerIngestionServiceContractTest {

    @Mock
    private CrawlerCandidateMapper crawlerCandidateMapper;

    @Mock
    private CrawlerPushRecordMapper crawlerPushRecordMapper;

    @Mock
    private PostService postService;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private StorageFacade storageFacade;

    @Mock
    private com.blog.api.security.UploadValidationService uploadValidationService;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private CategoryService categoryService;

    @Mock
    private TagService tagService;

    @InjectMocks
    private CrawlerIngestionService crawlerIngestionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAcceptReplayWhenItemIdempotencyMatchesSamePayload() {
        CrawlerCandidate existing = new CrawlerCandidate();
        existing.setId(11L);
        existing.setItemIdempotencyKey("item-key");
        existing.setNormalizedUrl("https://example.com/a");
        existing.setState("review_pending");
        when(crawlerCandidateMapper.selectByItemIdempotencyKey("item-key")).thenReturn(existing);

        CrawlerCandidateIngestRequest request = buildIngestRequest("item-key", "https://example.com/a");
        CrawlerIngestionService service = new CrawlerIngestionService(
                crawlerCandidateMapper,
                crawlerPushRecordMapper,
                postService,
                systemConfigService,
                objectMapper,
                storageFacade,
                uploadValidationService,
                categoryMapper,
                tagMapper,
                categoryService,
                tagService
        );

        var response = service.ingestCandidates(request, "device-1");
        assertEquals(1, response.getAccepted().size());
        assertEquals(11L, response.getAccepted().get(0).getCandidateId());
        assertEquals(0, response.getRejected().size());
    }

    @Test
    void shouldRejectWhenItemIdempotencyMatchesDifferentPayload() {
        CrawlerCandidate existing = new CrawlerCandidate();
        existing.setId(12L);
        existing.setItemIdempotencyKey("item-key");
        existing.setNormalizedUrl("https://example.com/a");
        when(crawlerCandidateMapper.selectByItemIdempotencyKey("item-key")).thenReturn(existing);

        CrawlerCandidateIngestRequest request = buildIngestRequest("item-key", "https://example.com/b");
        CrawlerIngestionService service = new CrawlerIngestionService(
                crawlerCandidateMapper,
                crawlerPushRecordMapper,
                postService,
                systemConfigService,
                objectMapper,
                storageFacade,
                uploadValidationService,
                categoryMapper,
                tagMapper,
                categoryService,
                tagService
        );

        var response = service.ingestCandidates(request, "device-1");
        assertEquals(0, response.getAccepted().size());
        assertEquals(1, response.getRejected().size());
        assertEquals("idempotency_conflict", response.getRejected().get(0).getReasonCode());
        assertEquals(12L, response.getRejected().get(0).getDuplicateCandidateId());
        assertEquals("https://example.com/a", response.getRejected().get(0).getDuplicateNormalizedUrl());
    }

    @Test
    void shouldReturnDuplicateCandidateContextWhenContentFingerprintExists() {
        when(crawlerCandidateMapper.selectByItemIdempotencyKey("dup-item")).thenReturn(null);
        when(crawlerCandidateMapper.selectByNormalizedUrl("https://example.com/new-dup")).thenReturn(null);

        CrawlerCandidate existing = new CrawlerCandidate();
        existing.setId(88L);
        existing.setTitle("旧文章标题");
        existing.setExternalUrl("https://example.com/old");
        existing.setNormalizedUrl("https://example.com/old");
        existing.setLastPushedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        when(crawlerCandidateMapper.selectByContentFingerprint("fp-dup")).thenReturn(existing);

        CrawlerCandidateIngestRequest request = new CrawlerCandidateIngestRequest();
        request.setIdempotencyKey("req-dup");
        request.setWorkerRunId("run-dup");
        CrawlerCandidateItemDTO item = new CrawlerCandidateItemDTO();
        item.setItemIdempotencyKey("dup-item");
        item.setExternalUrl("https://example.com/new-dup");
        item.setNormalizedUrl("https://example.com/new-dup");
        item.setTitle("new-title");
        item.setContentMarkdown("content");
        item.setContentFingerprint("fp-dup");
        request.setItems(List.of(item));

        CrawlerIngestionService service = new CrawlerIngestionService(
                crawlerCandidateMapper,
                crawlerPushRecordMapper,
                postService,
                systemConfigService,
                objectMapper,
                storageFacade,
                uploadValidationService,
                categoryMapper,
                tagMapper,
                categoryService,
                tagService
        );

        var response = service.ingestCandidates(request, "device-1");
        assertEquals(1, response.getRejected().size());
        assertEquals("duplicate_content", response.getRejected().get(0).getReasonCode());
        assertEquals(88L, response.getRejected().get(0).getDuplicateCandidateId());
        assertEquals("旧文章标题", response.getRejected().get(0).getDuplicateTitle());
        assertEquals("https://example.com/old", response.getRejected().get(0).getDuplicateExternalUrl());
        assertEquals(LocalDateTime.of(2026, 4, 1, 10, 0), response.getRejected().get(0).getDuplicateLastPushedAt());
    }

    @Test
    void shouldReturnCurrentItemKeyWhenNormalizedUrlMatchesExistingCandidate() {
        when(crawlerCandidateMapper.selectByItemIdempotencyKey("new-item-key")).thenReturn(null);

        CrawlerCandidate existing = new CrawlerCandidate();
        existing.setId(66L);
        existing.setItemIdempotencyKey("old-item-key");
        existing.setNormalizedUrl("https://example.com/existing");
        existing.setState("review_pending");
        when(crawlerCandidateMapper.selectByNormalizedUrl("https://example.com/existing")).thenReturn(existing);

        CrawlerCandidateIngestRequest request = buildIngestRequest("new-item-key", "https://example.com/existing");
        CrawlerIngestionService service = new CrawlerIngestionService(
                crawlerCandidateMapper,
                crawlerPushRecordMapper,
                postService,
                systemConfigService,
                objectMapper,
                storageFacade,
                uploadValidationService,
                categoryMapper,
                tagMapper,
                categoryService,
                tagService
        );

        var response = service.ingestCandidates(request, "device-1");
        assertEquals(1, response.getAccepted().size());
        assertEquals("new-item-key", response.getAccepted().get(0).getItemIdempotencyKey());
        assertEquals(66L, response.getAccepted().get(0).getCandidateId());
    }

    @Test
    void shouldThrowDuplicateWhenPushIdempotencyMappedToAnotherCandidate() {
        CrawlerCandidate candidate = new CrawlerCandidate();
        candidate.setId(100L);
        when(crawlerCandidateMapper.selectById(100L)).thenReturn(candidate);

        CrawlerPushRecord existingPush = new CrawlerPushRecord();
        existingPush.setId(20L);
        existingPush.setCandidateId(999L);
        existingPush.setPushIdempotencyKey("push:key");
        when(crawlerPushRecordMapper.selectByPushIdempotencyKey("push:key")).thenReturn(existingPush);

        CrawlerPushResultUpsertRequest request = new CrawlerPushResultUpsertRequest();
        request.setPushIdempotencyKey("push:key");
        request.setCandidateId(100L);
        request.setStatus("failed");

        CrawlerIngestionService service = new CrawlerIngestionService(
                crawlerCandidateMapper,
                crawlerPushRecordMapper,
                postService,
                systemConfigService,
                objectMapper,
                storageFacade,
                uploadValidationService,
                categoryMapper,
                tagMapper,
                categoryService,
                tagService
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> service.upsertPushResult(request, "device-1"));
        assertEquals(ResultCode.DUPLICATE.getCode(), ex.getCode());
    }

    @Test
    void shouldAcceptNewCandidateWhenNoDedupHit() {
        when(crawlerCandidateMapper.selectByItemIdempotencyKey("new-item")).thenReturn(null);
        when(crawlerCandidateMapper.selectByNormalizedUrl("https://example.com/new")).thenReturn(null);
        when(crawlerCandidateMapper.selectByContentFingerprint("fp-1")).thenReturn(null);
        doAnswer(invocation -> {
            CrawlerCandidate arg = invocation.getArgument(0);
            arg.setId(101L);
            return 1;
        }).when(crawlerCandidateMapper).insert(any(CrawlerCandidate.class));

        CrawlerCandidateIngestRequest request = new CrawlerCandidateIngestRequest();
        request.setIdempotencyKey("req-1");
        request.setWorkerRunId("run-1");
        CrawlerCandidateItemDTO item = new CrawlerCandidateItemDTO();
        item.setItemIdempotencyKey("new-item");
        item.setExternalUrl("https://example.com/new");
        item.setNormalizedUrl("https://example.com/new");
        item.setTitle("title");
        item.setContentMarkdown("content");
        item.setContentFingerprint("fp-1");
        request.setItems(List.of(item));

        CrawlerIngestionService service = new CrawlerIngestionService(
                crawlerCandidateMapper,
                crawlerPushRecordMapper,
                postService,
                systemConfigService,
                objectMapper,
                storageFacade,
                uploadValidationService,
                categoryMapper,
                tagMapper,
                categoryService,
                tagService
        );
        var response = service.ingestCandidates(request, "device-1");
        assertEquals(1, response.getAccepted().size());
        assertEquals(101L, response.getAccepted().get(0).getCandidateId());
    }

    @Test
    void shouldTruncateDraftTitleAndSummaryBeforeCreatingPost() {
        when(systemConfigService.getIntValue("crawler_draft_owner_user_id", 1)).thenReturn(1);

        CrawlerCandidate candidate = new CrawlerCandidate();
        candidate.setId(201L);
        candidate.setState("approved");
        candidate.setTitle("测".repeat(260));
        candidate.setSummary("摘".repeat(600));
        candidate.setContentMarkdown("content");
        when(crawlerCandidateMapper.selectById(201L)).thenReturn(candidate);

        PostVO postVO = new PostVO();
        postVO.setId(301L);
        when(postService.create(any(PostCreateRequest.class), eq(1L))).thenReturn(postVO);

        CrawlerDraftPushRequest request = new CrawlerDraftPushRequest();
        request.setCandidateIds(List.of(201L));

        CrawlerIngestionService service = new CrawlerIngestionService(
                crawlerCandidateMapper,
                crawlerPushRecordMapper,
                postService,
                systemConfigService,
                objectMapper,
                storageFacade,
                uploadValidationService,
                categoryMapper,
                tagMapper,
                categoryService,
                tagService
        );

        var response = service.pushCandidatesToDraft(request, "device-1");

        assertEquals(1, response.getSucceeded());
        verify(postService).create(org.mockito.ArgumentMatchers.argThat(arg -> arg.getTitle() != null && arg.getTitle().length() == 200
                && arg.getSummary() != null && arg.getSummary().length() == 500), eq(1L));
    }

    @Test
    void shouldReturnFailedResultWhenDraftCreationThrows() {
        when(systemConfigService.getIntValue("crawler_draft_owner_user_id", 1)).thenReturn(1);

        CrawlerCandidate candidate = new CrawlerCandidate();
        candidate.setId(202L);
        candidate.setState("approved");
        candidate.setTitle("title");
        candidate.setSummary("summary");
        candidate.setContentMarkdown("content");
        when(crawlerCandidateMapper.selectById(202L)).thenReturn(candidate);
        when(postService.create(any(PostCreateRequest.class), eq(1L)))
                .thenThrow(new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));

        CrawlerDraftPushRequest request = new CrawlerDraftPushRequest();
        request.setCandidateIds(List.of(202L));

        CrawlerIngestionService service = new CrawlerIngestionService(
                crawlerCandidateMapper,
                crawlerPushRecordMapper,
                postService,
                systemConfigService,
                objectMapper,
                storageFacade,
                uploadValidationService,
                categoryMapper,
                tagMapper,
                categoryService,
                tagService
        );

        var response = service.pushCandidatesToDraft(request, "device-1");

        assertEquals(0, response.getSucceeded());
        assertEquals(1, response.getFailed());
        assertEquals("failed", response.getResults().get(0).getStatus());
        assertEquals("文章不存在", response.getResults().get(0).getMessage());
    }

    @Test
    void shouldCreateNewDraftWhenTargetDraftMissing() {
        when(systemConfigService.getIntValue("crawler_draft_owner_user_id", 1)).thenReturn(1);

        CrawlerCandidate candidate = new CrawlerCandidate();
        candidate.setId(203L);
        candidate.setState("approved");
        candidate.setTargetDraftId(999L);
        candidate.setTitle("title");
        candidate.setSummary("summary");
        candidate.setContentMarkdown("content");
        when(crawlerCandidateMapper.selectById(203L)).thenReturn(candidate);
        when(postService.update(eq(999L), any(PostCreateRequest.class), eq(1L)))
                .thenThrow(new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));

        PostVO created = new PostVO();
        created.setId(302L);
        when(postService.create(any(PostCreateRequest.class), eq(1L))).thenReturn(created);

        CrawlerDraftPushRequest request = new CrawlerDraftPushRequest();
        request.setCandidateIds(List.of(203L));

        CrawlerIngestionService service = new CrawlerIngestionService(
                crawlerCandidateMapper,
                crawlerPushRecordMapper,
                postService,
                systemConfigService,
                objectMapper,
                storageFacade,
                uploadValidationService,
                categoryMapper,
                tagMapper,
                categoryService,
                tagService
        );

        var response = service.pushCandidatesToDraft(request, "device-1");

        assertEquals(1, response.getSucceeded());
        verify(postService).create(any(PostCreateRequest.class), eq(1L));
    }

    private CrawlerCandidateIngestRequest buildIngestRequest(String itemKey, String normalizedUrl) {
        CrawlerCandidateIngestRequest request = new CrawlerCandidateIngestRequest();
        request.setIdempotencyKey("req-1");
        request.setWorkerRunId("run-1");
        CrawlerCandidateItemDTO item = new CrawlerCandidateItemDTO();
        item.setItemIdempotencyKey(itemKey);
        item.setExternalUrl(normalizedUrl);
        item.setNormalizedUrl(normalizedUrl);
        item.setTitle("title");
        item.setContentMarkdown("content");
        request.setItems(List.of(item));
        return request;
    }
}
