package com.blog.api.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CspViolationService {

    private static final String DAILY_KEY_PREFIX = "security:csp:daily:";
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Duration RECORD_TTL = Duration.ofDays(45);
    private static final int WEEKLY_WINDOW_DAYS = 7;
    private static final int WEEKLY_TOP_LIMIT = 20;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void record(String body, String clientIp, String userAgent) {
        List<ViolationRecord> records = parseRecords(body);
        if (records.isEmpty()) {
            records = List.of(new ViolationRecord("parse-error", "unknown", safeValue(clientIp), safeValue(userAgent)));
        }

        String day = LocalDate.now().format(DAY_FORMATTER);
        String key = DAILY_KEY_PREFIX + day;

        for (ViolationRecord record : records) {
            String field = record.directive() + "|" + record.blockedTarget() + "|" + record.clientIp();
            stringRedisTemplate.opsForHash().increment(key, field, 1L);
        }

        stringRedisTemplate.expire(key, RECORD_TTL);
    }

    @Scheduled(cron = "${blog.security.csp-report.weekly-cron:0 15 4 ? * MON}")
    public void summarizeWeekly() {
        LocalDate today = LocalDate.now();
        Map<String, Long> aggregate = new HashMap<>();

        for (int i = 0; i < WEEKLY_WINDOW_DAYS; i++) {
            LocalDate day = today.minusDays(i);
            String key = DAILY_KEY_PREFIX + day.format(DAY_FORMATTER);
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
            if (entries == null || entries.isEmpty()) {
                continue;
            }

            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }

                String field = entry.getKey().toString();
                long count = parseLong(entry.getValue());
                aggregate.merge(field, count, Long::sum);
            }
        }

        if (aggregate.isEmpty()) {
            log.info("CSP 周报: 最近 {} 天未收到违规上报", WEEKLY_WINDOW_DAYS);
            return;
        }

        List<Map.Entry<String, Long>> top = aggregate.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(WEEKLY_TOP_LIMIT)
                .toList();

        log.info("CSP 周报: 最近 {} 天共 {} 类违规，Top{} 如下", WEEKLY_WINDOW_DAYS, aggregate.size(), top.size());
        for (Map.Entry<String, Long> item : top) {
            log.info("CSP 周报项: {} -> {}", item.getKey(), item.getValue());
        }
    }

    private List<ViolationRecord> parseRecords(String body) {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            List<ViolationRecord> records = new ArrayList<>();

            if (root.isArray()) {
                for (JsonNode node : root) {
                    JsonNode payload = node.path("body");
                    records.add(buildRecord(payload.isMissingNode() ? node : payload, "unknown", "unknown"));
                }
                return records;
            }

            if (root.has("csp-report")) {
                records.add(buildRecord(root.path("csp-report"), "unknown", "unknown"));
                return records;
            }

            records.add(buildRecord(root, "unknown", "unknown"));
            return records;
        } catch (Exception ex) {
            log.warn("解析 CSP 上报失败", ex);
            return List.of();
        }
    }

    private ViolationRecord buildRecord(JsonNode node, String defaultIp, String defaultUserAgent) {
        String directive = firstNonBlank(
                node.path("effective-directive").asText(),
                node.path("violated-directive").asText(),
                node.path("directive").asText(),
                "unknown-directive"
        );

        String blockedTarget = normalizeBlockedTarget(firstNonBlank(
                node.path("blocked-uri").asText(),
                node.path("blockedURL").asText(),
                node.path("blocked-url").asText(),
                node.path("source-file").asText(),
                "unknown"
        ));

        return new ViolationRecord(
                safeValue(directive),
                safeValue(blockedTarget),
                safeValue(defaultIp),
                safeValue(defaultUserAgent)
        );
    }

    private String normalizeBlockedTarget(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "unknown";
        }

        String value = raw.trim();
        if (!value.contains("://")) {
            return value.length() > 120 ? value.substring(0, 120) : value;
        }

        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return value.length() > 120 ? value.substring(0, 120) : value;
            }
            return host;
        } catch (Exception ignored) {
            return value.length() > 120 ? value.substring(0, 120) : value;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return "";
        }

        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String safeValue(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "unknown";
        }
        String value = raw.trim();
        if (value.length() <= 160) {
            return value;
        }
        return value.substring(0, 160);
    }

    private long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value == null) {
            return 0L;
        }

        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private record ViolationRecord(String directive, String blockedTarget, String clientIp, String userAgent) {
    }
}
