package com.blog.content.service;

import com.blog.common.exception.BusinessException;
import com.blog.content.entity.LinkDomainPolicy;
import com.blog.content.mapper.LinkDomainPolicyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalLinkGovernanceServiceTest {

  private LinkDomainPolicyMapper linkDomainPolicyMapper;
  private ExternalLinkGovernanceService externalLinkGovernanceService;

  @BeforeEach
  void setUp() {
    linkDomainPolicyMapper = mock(LinkDomainPolicyMapper.class);
    externalLinkGovernanceService = new ExternalLinkGovernanceService(linkDomainPolicyMapper);
    ReflectionTestUtils.setField(externalLinkGovernanceService, "outboundLinkSeedDomains", "");
  }

  @Test
  void shouldRejectNonHttpsLinkBeforePolicyDecision() {
    BusinessException ex = assertThrows(BusinessException.class,
      () -> externalLinkGovernanceService.evaluateForSubmission("http://1.1.1.1/path", true));

    assertEquals("跳转链接仅支持 HTTPS 协议", ex.getMessage());
  }

  @Test
  void shouldTreatSeedDomainAsTrustedWhenPolicyMissing() {
    ReflectionTestUtils.setField(externalLinkGovernanceService, "outboundLinkSeedDomains", "1.1.1.1");
    when(linkDomainPolicyMapper.selectList(any())).thenReturn(List.of());

    ExternalLinkGovernanceService.LinkCheckResult result =
      externalLinkGovernanceService.evaluateForSubmission("https://1.1.1.1/ok", true);

    assertTrue(result.trusted());
    assertEquals("trusted", result.policyStatus());
    assertEquals("命中静态种子名单", result.reason());
  }

  @Test
  void shouldBlockWhenDomainPolicyBlockedEvenIfSeedConfigured() {
    ReflectionTestUtils.setField(externalLinkGovernanceService, "outboundLinkSeedDomains", "1.1.1.1");
    LinkDomainPolicy blocked = new LinkDomainPolicy();
    blocked.setDomain("1.1.1.1");
    blocked.setStatus("blocked");
    when(linkDomainPolicyMapper.selectList(any())).thenReturn(List.of(blocked));

    BusinessException ex = assertThrows(BusinessException.class,
      () -> externalLinkGovernanceService.evaluateForSubmission("https://1.1.1.1/promo", true));

    assertEquals("跳转链接域名不在白名单内", ex.getMessage());
  }

  @Test
  void shouldCreatePendingPolicyForUnknownDomain() {
    when(linkDomainPolicyMapper.selectList(any())).thenReturn(List.of());

    ExternalLinkGovernanceService.LinkCheckResult result =
      externalLinkGovernanceService.evaluateForSubmission("https://8.8.8.8/welcome", true);

    assertTrue(result.pending());
    assertEquals("pending", result.policyStatus());
    verify(linkDomainPolicyMapper, times(1)).insert(any(LinkDomainPolicy.class));
  }
}
