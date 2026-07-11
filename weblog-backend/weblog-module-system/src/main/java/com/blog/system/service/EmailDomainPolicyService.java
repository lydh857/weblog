package com.blog.system.service;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 邮箱域名准入策略。
 */
@Service
@RequiredArgsConstructor
public class EmailDomainPolicyService {

    public static final String ALLOWED_EMAIL_DOMAINS_KEY = "allowed_email_domains";
    public static final String DEFAULT_ALLOWED_EMAIL_DOMAINS = "qq.com,foxmail.com,163.com,126.com,yeah.net,gmail.com,outlook.com,hotmail.com,icloud.com,sina.com,aliyun.com";

    private final SystemConfigService systemConfigService;

    public void assertAllowed(String email) {
        String domain = extractDomain(email);
        Set<String> allowedDomains = getAllowedDomains();
        if (!allowedDomains.contains(domain)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "当前邮箱后缀暂不支持，请使用常见邮箱注册或绑定");
        }
    }

    private Set<String> getAllowedDomains() {
        String value = systemConfigService.getValue(ALLOWED_EMAIL_DOMAINS_KEY);
        String configured = value == null || value.isBlank() ? DEFAULT_ALLOWED_EMAIL_DOMAINS : value;
        return Arrays.stream(configured.split(","))
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String extractDomain(String email) {
        if (email == null) {
            throw new BusinessException(ResultCode.EMAIL_INVALID);
        }
        int atIndex = email.lastIndexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            throw new BusinessException(ResultCode.EMAIL_INVALID);
        }
        return email.substring(atIndex + 1).trim().toLowerCase(Locale.ROOT);
    }
}
