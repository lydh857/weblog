package com.blog.content.util;

import cn.hutool.core.util.StrUtil;

import java.net.IDN;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 外链域名种子名单解析工具。
 */
public final class ExternalLinkSeedDomainParser {

    private ExternalLinkSeedDomainParser() {
    }

    public static Set<String> parseSeedDomains(String rawList) {
        if (rawList == null || rawList.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(rawList.split(","))
                .map(ExternalLinkSeedDomainParser::normalizeDomain)
                .filter(StrUtil::isNotBlank)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public static String normalizeDomain(String rawDomain) {
        if (rawDomain == null || rawDomain.isBlank()) {
            return null;
        }

        String value = rawDomain.trim();
        try {
            if (value.contains("://")) {
                URI uri = URI.create(value);
                value = uri.getHost();
            }
        } catch (Exception e) {
            return null;
        }

        if (value == null || value.isBlank()) {
            return null;
        }

        value = value.toLowerCase(Locale.ROOT);
        if (value.startsWith("*.")) {
            value = value.substring(2);
        }
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }

        try {
            return IDN.toASCII(value, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }
}
