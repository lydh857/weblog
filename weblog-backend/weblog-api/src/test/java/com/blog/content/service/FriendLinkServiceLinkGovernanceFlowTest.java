package com.blog.content.service;

import com.blog.common.exception.BusinessException;
import com.blog.content.entity.FriendLink;
import com.blog.content.mapper.FriendLinkMapper;
import com.blog.infra.security.sensitive.SensitiveWordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendLinkServiceLinkGovernanceFlowTest {

  private FriendLinkMapper friendLinkMapper;
  private ExternalLinkGovernanceService externalLinkGovernanceService;
  private FriendLinkService friendLinkService;

  @BeforeEach
  void setUp() {
    friendLinkMapper = mock(FriendLinkMapper.class);
    SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
    externalLinkGovernanceService = mock(ExternalLinkGovernanceService.class);
    friendLinkService = new FriendLinkService(friendLinkMapper, sensitiveWordService, externalLinkGovernanceService);
  }

  @Test
  void shouldCompleteFriendLinkFlowFromPendingDomainReviewToApproved() {
    when(friendLinkMapper.selectOne(any())).thenReturn(null);
    when(externalLinkGovernanceService.evaluateForSubmission(eq("https://1.1.1.1/site"), eq(true)))
      .thenReturn(ExternalLinkGovernanceService.LinkCheckResult.pending("https://1.1.1.1/site", "1.1.1.1", "域名待审核"));

    doAnswer(invocation -> {
      FriendLink arg = invocation.getArgument(0);
      arg.setId(301L);
      return 1;
    }).when(friendLinkMapper).insert(any(FriendLink.class));

    FriendLink applied = friendLinkService.applyLink(9L, "示例站点", "https://1.1.1.1/site", null, "示例描述");
    assertEquals("pending_domain_review", applied.getStatus());

    when(friendLinkMapper.selectById(301L)).thenReturn(applied);
    when(externalLinkGovernanceService.inspectForDisplay(eq("https://1.1.1.1/site")))
      .thenReturn(ExternalLinkGovernanceService.LinkCheckResult.pending("https://1.1.1.1/site", "1.1.1.1", "域名待审核"));
    when(externalLinkGovernanceService.evaluateForSubmission(eq("https://1.1.1.1/site"), eq(true)))
      .thenReturn(ExternalLinkGovernanceService.LinkCheckResult.trusted("https://1.1.1.1/site", "1.1.1.1", "域名已信任"));

    FriendLink approved = friendLinkService.approveLink(301L);

    assertEquals("active", approved.getStatus());
    verify(friendLinkMapper).updateById(any(FriendLink.class));
  }

  @Test
  void shouldRejectApproveWhenDomainStillPending() {
    FriendLink pending = new FriendLink();
    pending.setId(401L);
    pending.setName("待审站点");
    pending.setUrl("https://8.8.8.8/blog");
    pending.setStatus("pending_domain_review");

    when(friendLinkMapper.selectById(401L)).thenReturn(pending);
    when(externalLinkGovernanceService.inspectForDisplay(eq("https://8.8.8.8/blog")))
      .thenReturn(ExternalLinkGovernanceService.LinkCheckResult.pending("https://8.8.8.8/blog", "8.8.8.8", "域名待审核"));
    when(externalLinkGovernanceService.evaluateForSubmission(eq("https://8.8.8.8/blog"), eq(true)))
      .thenReturn(ExternalLinkGovernanceService.LinkCheckResult.pending("https://8.8.8.8/blog", "8.8.8.8", "域名待审核"));

    BusinessException ex = assertThrows(BusinessException.class, () -> friendLinkService.approveLink(401L));

    assertTrue(ex.getMessage().contains("外链域名未审核通过"));
    verify(friendLinkMapper, never()).updateById(any(FriendLink.class));
  }
}
