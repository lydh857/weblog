package com.blog.api.portal;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.system.service.EmailDomainPolicyService;
import com.blog.system.service.SystemConfigService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailDomainPolicyServiceTest {

    @Test
    void shouldAllowConfiguredEmailDomain() {
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        when(systemConfigService.getValue(EmailDomainPolicyService.ALLOWED_EMAIL_DOMAINS_KEY))
                .thenReturn("qq.com,163.com");
        EmailDomainPolicyService service = new EmailDomainPolicyService(systemConfigService);

        assertDoesNotThrow(() -> service.assertAllowed("user@qq.com"));
    }

    @Test
    void shouldIgnoreCaseAndSpacesInConfiguredDomains() {
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        when(systemConfigService.getValue(EmailDomainPolicyService.ALLOWED_EMAIL_DOMAINS_KEY))
                .thenReturn(" QQ.COM, 163.COM ");
        EmailDomainPolicyService service = new EmailDomainPolicyService(systemConfigService);

        assertDoesNotThrow(() -> service.assertAllowed("user@QQ.COM"));
    }

    @Test
    void shouldRejectUnsupportedEmailDomain() {
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        when(systemConfigService.getValue(EmailDomainPolicyService.ALLOWED_EMAIL_DOMAINS_KEY))
                .thenReturn("qq.com,163.com");
        EmailDomainPolicyService service = new EmailDomainPolicyService(systemConfigService);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertAllowed("user@example.com"));

        assertEquals(ResultCode.PARAM_INVALID.getCode(), ex.getCode());
    }

    @Test
    void shouldUseDefaultDomainsWhenConfigEmpty() {
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        when(systemConfigService.getValue(EmailDomainPolicyService.ALLOWED_EMAIL_DOMAINS_KEY))
                .thenReturn("");
        EmailDomainPolicyService service = new EmailDomainPolicyService(systemConfigService);

        assertDoesNotThrow(() -> service.assertAllowed("user@foxmail.com"));
    }
}
