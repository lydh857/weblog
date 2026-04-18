package com.blog.content.service;

import com.blog.common.exception.BusinessException;
import com.blog.content.entity.Advertisement;
import com.blog.content.mapper.AdvertisementMapper;
import com.blog.content.mapper.PostMapper;
import com.blog.infra.security.sensitive.SensitiveWordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdvertisementServiceLinkGovernanceFlowTest {

  private AdvertisementMapper advertisementMapper;
  private ExternalLinkGovernanceService externalLinkGovernanceService;
  private AdvertisementService advertisementService;

  @BeforeEach
  void setUp() {
    advertisementMapper = mock(AdvertisementMapper.class);
    PostMapper postMapper = mock(PostMapper.class);
    AdPitBindingService adPitBindingService = mock(AdPitBindingService.class);
    SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
    externalLinkGovernanceService = mock(ExternalLinkGovernanceService.class);

    advertisementService = new AdvertisementService(
      advertisementMapper,
      postMapper,
      adPitBindingService,
      sensitiveWordService,
      externalLinkGovernanceService
    );
  }

  @Test
  void shouldMoveApplicationFromPendingDomainReviewToActiveAfterDomainTrusted() {
    Long userId = 12L;
    Advertisement pendingApplication = new Advertisement();
    pendingApplication.setType("image");
    pendingApplication.setContent("/uploads/banner.png");
    pendingApplication.setPosition("home_left");
    pendingApplication.setLinkUrl("https://1.1.1.1/promo");
    pendingApplication.setStartTime(LocalDateTime.now().plusHours(2));
    pendingApplication.setEndTime(LocalDateTime.now().plusDays(2));

    when(externalLinkGovernanceService.inspectForDisplay(eq("https://1.1.1.1/promo")))
      .thenReturn(ExternalLinkGovernanceService.LinkCheckResult.trusted("https://1.1.1.1/promo", "1.1.1.1", "展示校验"));
    when(externalLinkGovernanceService.evaluateForSubmission(eq("https://1.1.1.1/promo"), eq(true)))
      .thenReturn(ExternalLinkGovernanceService.LinkCheckResult.pending("https://1.1.1.1/promo", "1.1.1.1", "域名待审核"));
    when(advertisementMapper.selectOne(any())).thenReturn(null);
    doAnswer(invocation -> {
      Advertisement arg = invocation.getArgument(0);
      arg.setId(100L);
      return 1;
    }).when(advertisementMapper).insert(any(Advertisement.class));

    Advertisement submitted = advertisementService.submitApplication(userId, pendingApplication);
    assertEquals("pending_domain_review", submitted.getStatus());
    assertEquals(100L, submitted.getId());

    when(advertisementMapper.selectById(100L)).thenReturn(submitted);
    when(externalLinkGovernanceService.evaluateForSubmission(eq("https://1.1.1.1/promo"), eq(false)))
      .thenReturn(ExternalLinkGovernanceService.LinkCheckResult.trusted("https://1.1.1.1/promo", "1.1.1.1", "域名已信任"));

    advertisementService.updateStatus(100L, "active");

    verify(advertisementMapper).updateById(any(Advertisement.class));
  }

  @Test
  void shouldRejectActivationWhenDomainStillPending() {
    Advertisement existing = new Advertisement();
    existing.setId(88L);
    existing.setStatus("pending_domain_review");
    existing.setLinkUrl("https://8.8.8.8/waiting");
    when(advertisementMapper.selectById(88L)).thenReturn(existing);
    when(externalLinkGovernanceService.evaluateForSubmission(eq("https://8.8.8.8/waiting"), eq(false)))
      .thenReturn(ExternalLinkGovernanceService.LinkCheckResult.pending("https://8.8.8.8/waiting", "8.8.8.8", "域名待审核"));

    BusinessException ex = assertThrows(BusinessException.class,
      () -> advertisementService.updateStatus(88L, "active"));

    assertTrue(ex.getMessage().contains("外链域名未审核通过"));
    verify(advertisementMapper, never()).updateById(any(Advertisement.class));
    verify(advertisementMapper, never()).insert(any(Advertisement.class));
    verify(advertisementMapper).selectById(anyLong());
  }
}
