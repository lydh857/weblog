package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.content.entity.Advertisement;
import com.blog.content.service.AdPitBindingService;
import com.blog.content.service.AdvertisementService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.infra.security.util.XssUtil;
import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.system.entity.User;
import com.blog.system.mapper.UserMapper;
import com.blog.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 用户端广告接口
 */
@Tag(name = "用户端-广告", description = "广告查询、广告申请")
@Slf4j
@RestController
@RequestMapping("/api/portal/advertisement")
@RequiredArgsConstructor
public class AdvertisementController {

    private final AdvertisementService advertisementService;
    private final AdPitBindingService adPitBindingService;
    private final UserMapper userMapper;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;
    private final DynamicRateLimitPolicyService dynamicRateLimitPolicyService;

    private static final String AD_APPLY_SWITCH_KEY = "ad_apply_enabled";
    private static final String AD_PRICE_RULES_KEY = "ad_price_rules";
    private static final String AD_REVIEW_REASONS_KEY = "ad_review_reasons";
    private static final String AD_APPLY_PIT_IDS_KEY = "ad_apply_pit_ids";
    private static final Set<String> SUPPORTED_POSITIONS = Set.of("home_left", "post_top", "post_bottom", "post_list_card");
    private static final List<String> POSITION_ORDER = List.of("home_left", "post_top", "post_bottom", "post_list_card");
    private static final List<Integer> DEFAULT_DURATION_DAYS = List.of(7, 30, 90, 180);
    private static final int MAX_PIT_COUNT = 7;
    private static final int MAX_APPLICATION_TITLE_LENGTH = 100;
    private static final BigDecimal PIT_PRICE_STEP = new BigDecimal("0.08");
    private static final BigDecimal MIN_PIT_PRICE_FACTOR = new BigDecimal("0.52");
    private static final String AD_APPLY_RATE_LIMIT_KEY = "ad_apply_rate_limit";

    @Operation(summary = "按位置获取有效广告")
    @GetMapping
    @RateLimit(key = "ad-list", capacity = 120, seconds = 60)
    public Result<List<Advertisement>> getByPosition(
            @RequestParam(required = false) String slot,
            @RequestParam(required = false) String position) {
        String targetSlot = slot != null && !slot.isBlank() ? slot : position;
        List<Advertisement> ads = advertisementService.getActiveBySlot(targetSlot);
        attachPitFlags(ads);
        reorderAdsByPitDisplayOrder(ads);
        return Result.success(ads);
    }

    @Operation(summary = "记录广告点击")
    @PostMapping("/{id}/click")
    @RateLimit(key = "ad-click", capacity = 120, seconds = 60)
    public Result<Void> recordClick(@PathVariable Long id) {
        advertisementService.recordClick(id);
        return Result.success();
    }

    @Operation(summary = "查询广告申请入口是否开放")
    @GetMapping("/apply-status")
    @RateLimit(key = "ad-apply-status", capacity = 60, seconds = 60)
    public Result<Map<String, Object>> getApplyStatus() {
        String val = systemConfigService.getValue(AD_APPLY_SWITCH_KEY);
        return Result.success(Map.of(
                "enabled", "true".equals(val),
                "rules", loadPriceRules(),
                "pitOptions", loadApplyPitOptions()));
    }

    @Operation(summary = "查询我的广告申请")
    @GetMapping("/my")
    @RateLimit(key = "ad-my", capacity = 60, seconds = 60)
    public Result<Advertisement> getMyApplication(@RequestParam(required = false) String position) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        Advertisement application = advertisementService.getMyApplication(userId, position);
        if (application != null && application.getId() != null) {
            Map<String, String> reviewReasonMap = loadReviewReasonMap();
            application.setReviewReason(reviewReasonMap.get(String.valueOf(application.getId())));
            Long pitAdId = adPitBindingService.getPitAdIdByApplyAdId(application.getId());
            application.setPitAdId(pitAdId);
            if (pitAdId != null) {
                Map<Long, Integer> pitIndexMap = buildPitIndexMap(loadConfiguredPitAdvertisementMap(), loadPitIdSet());
                application.setPitIndex(pitIndexMap.get(pitAdId));
            }
        }
        return Result.success(application);
    }

    @Operation(summary = "提交广告申请")
    @PostMapping("/apply")
    @RateLimit(key = "ad-apply", capacity = 5, seconds = 300)
    public Result<Advertisement> apply(@RequestBody Advertisement ad) {
        StpUtil.checkLogin();
        dynamicRateLimitPolicyService.enforcePerIp(
                "ad-apply",
                AD_APPLY_RATE_LIMIT_KEY,
                5,
                1,
                60,
                300,
                null,
                "广告申请过于频繁，请稍后再试"
        );
        // 检查申请入口是否开放
        String applyEnabled = systemConfigService.getValue(AD_APPLY_SWITCH_KEY);
        if (!"true".equals(applyEnabled)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "广告申请入口暂未开放");
        }
        Long userId = StpUtil.getLoginIdAsLong();

        ResolvedPit resolvedPit = validateAndResolvePitAd(ad);
        List<PriceRule> rules = loadPriceRules();
        validateApplicationByRules(ad, rules, resolvedPit.getPitIndex());

        User currentUser = userMapper.selectById(userId);
        ad.setTitle(resolveApplyTitle(currentUser, ad));

        // 安全过滤：如果是code类型广告，保留结构并清理危险脚本
        if ("code".equals(ad.getType()) && ad.getContent() != null) {
            ad.setContent(XssUtil.cleanMarkdown(ad.getContent()));
        }

        Advertisement application = advertisementService.submitApplicationWithPit(
                userId,
                ad,
                resolvedPit.getPitAd().getId(),
                resolvedPit.getPitIndex(),
                loadActivePitAdvertisementMap().keySet());
        clearReviewReason(application.getId());
        return Result.success(application);
    }

    private String buildApplyTitle(User user, String position, String type) {
        String email = user == null ? null : user.getEmail();
        String prefix = "user";
        if (email != null) {
            String trimmed = email.trim();
            int atIndex = trimmed.indexOf('@');
            if (atIndex > 0) {
                prefix = trimmed.substring(0, atIndex);
            } else if (!trimmed.isBlank()) {
                prefix = trimmed;
            }
        }
        String safePosition = (position == null || position.isBlank()) ? "unknown" : position.trim();
        String safeType = (type == null || type.isBlank()) ? "image" : type.trim();
        return prefix + "-" + safePosition + "-" + safeType;
    }

    private String resolveApplyTitle(User user, Advertisement ad) {
        if (ad == null) {
            return buildApplyTitle(user, null, null);
        }

        String position = ad.getPosition() == null ? "" : ad.getPosition().trim();
        String customTitle = ad.getTitle() == null ? "" : ad.getTitle().trim();
        if ("post_list_card".equals(position) && !customTitle.isEmpty()) {
            return customTitle;
        }

        return buildApplyTitle(user, ad.getPosition(), ad.getType());
    }

    private void clearReviewReason(Long adId) {
        if (adId == null) {
            return;
        }
        Map<String, String> reviewReasonMap = loadReviewReasonMap();
        if (reviewReasonMap.remove(String.valueOf(adId)) != null) {
            persistReviewReasonMap(reviewReasonMap);
        }
    }

    private void attachPitFlags(List<Advertisement> ads) {
        if (ads == null || ads.isEmpty()) {
            return;
        }

        Set<Long> pitIds = loadPitIdSet();
        Map<Long, Integer> pitIndexMap = buildPitIndexMap(loadConfiguredPitAdvertisementMap(), pitIds);
        Map<String, Long> pitBindingMap = adPitBindingService.loadBindingMap();
        for (Advertisement ad : ads) {
            if (ad == null || ad.getId() == null) {
                continue;
            }
            Long pitAdId = resolveDisplayPitAdId(ad, pitIds, pitBindingMap);
            if (pitAdId == null) {
                continue;
            }
            Integer pitIndex = pitIndexMap.get(pitAdId);
            if (pitIndex == null || pitIndex < 1) {
                continue;
            }

            ad.setPitEnabled(true);
            ad.setPitIndex(pitIndex);
            if (!Objects.equals(ad.getId(), pitAdId)) {
                ad.setPitAdId(pitAdId);
            }
        }
    }

    private Long resolveDisplayPitAdId(Advertisement ad, Set<Long> pitIds, Map<String, Long> pitBindingMap) {
        if (ad == null || ad.getId() == null || pitIds == null || pitIds.isEmpty()) {
            return null;
        }
        if (pitIds.contains(ad.getId()) && ad.getAdvertiserId() == null) {
            return ad.getId();
        }
        if (pitBindingMap == null || pitBindingMap.isEmpty()) {
            return null;
        }
        Long pitAdId = pitBindingMap.get(String.valueOf(ad.getId()));
        return pitAdId != null && pitIds.contains(pitAdId) ? pitAdId : null;
    }

    private void reorderAdsByPitDisplayOrder(List<Advertisement> ads) {
        if (ads == null || ads.size() < 2) {
            return;
        }

        Map<Long, Integer> originalOrderMap = new HashMap<>();
        for (int i = 0; i < ads.size(); i++) {
            Advertisement ad = ads.get(i);
            if (ad != null && ad.getId() != null) {
                originalOrderMap.putIfAbsent(ad.getId(), i);
            }
        }

        ads.sort((left, right) -> {
            Integer leftPitIndex = normalizePositivePitIndex(left == null ? null : left.getPitIndex());
            Integer rightPitIndex = normalizePositivePitIndex(right == null ? null : right.getPitIndex());

            if (leftPitIndex != null && rightPitIndex != null) {
                int compare = Integer.compare(leftPitIndex, rightPitIndex);
                if (compare != 0) {
                    return compare;
                }
            } else if (leftPitIndex != null) {
                return -1;
            } else if (rightPitIndex != null) {
                return 1;
            }

            return Integer.compare(
                    originalOrderMap.getOrDefault(left == null ? null : left.getId(), Integer.MAX_VALUE),
                    originalOrderMap.getOrDefault(right == null ? null : right.getId(), Integer.MAX_VALUE));
        });
    }

    private Integer normalizePositivePitIndex(Integer pitIndex) {
        return pitIndex != null && pitIndex > 0 ? pitIndex : null;
    }

    private String normalizePosition(String rawPosition) {
        if (rawPosition == null || rawPosition.isBlank()) {
            return null;
        }
        String value = rawPosition.trim();
        return switch (value) {
            case "sidebar" -> "home_left";
            case "top" -> "post_top";
            case "bottom" -> "post_bottom";
            default -> value;
        };
    }

    private Set<Long> loadPitIdSet() {
        String raw = systemConfigService.getValue(AD_APPLY_PIT_IDS_KEY);
        if (raw == null || raw.isBlank()) {
            return new LinkedHashSet<>();
        }

        try {
            List<Object> parsed = objectMapper.readValue(raw, new TypeReference<List<Object>>() {});
            Set<Long> result = new LinkedHashSet<>();
            if (parsed == null) {
                return result;
            }
            for (Object item : parsed) {
                if (item == null) {
                    continue;
                }
                try {
                    long value = item instanceof Number number
                            ? number.longValue()
                            : Long.parseLong(String.valueOf(item).trim());
                    if (value > 0) {
                        result.add(value);
                    }
                } catch (Exception e) {
                    log.warn("广告位ID解析失败", e);
                }
            }
            return result;
        } catch (Exception e) {
            return new LinkedHashSet<>();
        }
    }

    private Map<Long, Advertisement> loadConfiguredPitAdvertisementMap() {
        Set<Long> pitIds = loadPitIdSet();
        if (pitIds.isEmpty()) {
            return Map.of();
        }

        List<Advertisement> candidates = advertisementService.listByIds(pitIds.stream().toList());
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Advertisement> result = new HashMap<>();
        for (Advertisement ad : candidates) {
            if (ad == null || ad.getId() == null) {
                continue;
            }
            if (!pitIds.contains(ad.getId())) {
                continue;
            }
            if (ad.getAdvertiserId() != null) {
                continue;
            }
            String normalizedPosition = normalizePosition(ad.getPosition());
            if (!SUPPORTED_POSITIONS.contains(normalizedPosition)) {
                continue;
            }

            ad.setPosition(normalizedPosition);
            result.put(ad.getId(), ad);
        }
        return result;
    }

    private Map<Long, Advertisement> loadActivePitAdvertisementMap() {
        Map<Long, Advertisement> configuredMap = loadConfiguredPitAdvertisementMap();
        if (configuredMap.isEmpty()) {
            return Map.of();
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Long, Advertisement> result = new HashMap<>();
        for (Advertisement ad : configuredMap.values()) {
            if (ad == null || ad.getId() == null) {
                continue;
            }
            if (!"active".equals(ad.getStatus())) {
                continue;
            }

            if (ad.getStartTime() != null && ad.getStartTime().isAfter(now)) {
                continue;
            }
            if (ad.getEndTime() != null && ad.getEndTime().isBefore(now)) {
                continue;
            }

            result.put(ad.getId(), ad);
        }
        return result;
    }

    private Map<Long, Integer> buildPitIndexMap(Map<Long, Advertisement> pitMap, Set<Long> orderedPitIds) {
        if (pitMap == null || pitMap.isEmpty()) {
            return Map.of();
        }
        return buildPitIndexMap(getOrderedPitAdvertisements(pitMap, orderedPitIds));
    }

    private Map<Long, Integer> buildPitIndexMap(List<Advertisement> pitAds) {
        if (pitAds == null || pitAds.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> sequenceByPosition = new HashMap<>();
        Map<Long, Integer> pitIndexMap = new HashMap<>();
        for (Advertisement ad : pitAds) {
            if (ad == null || ad.getId() == null) {
                continue;
            }
            String normalizedPosition = normalizePosition(ad.getPosition());
            if (!SUPPORTED_POSITIONS.contains(normalizedPosition)) {
                continue;
            }

            int next = sequenceByPosition.getOrDefault(normalizedPosition, 0) + 1;
            sequenceByPosition.put(normalizedPosition, next);
            pitIndexMap.put(ad.getId(), next);
        }

        return pitIndexMap;
    }

    private List<Advertisement> getOrderedPitAdvertisements(Map<Long, Advertisement> pitMap, Set<Long> orderedPitIds) {
        List<Advertisement> ordered = new ArrayList<>();
        Set<Long> consumed = new LinkedHashSet<>();
        if (orderedPitIds != null) {
            for (Long pitId : orderedPitIds) {
                Advertisement advertisement = pitMap.get(pitId);
                if (advertisement != null) {
                    ordered.add(advertisement);
                    consumed.add(pitId);
                }
            }
        }

        List<Advertisement> remaining = pitMap.values().stream()
                .filter(ad -> ad != null && ad.getId() != null && !consumed.contains(ad.getId()))
                .sorted(this::comparePitOrder)
                .toList();
        ordered.addAll(remaining);
        return ordered;
    }

    private int comparePitOrder(Advertisement left, Advertisement right) {
        String leftPosition = normalizePosition(left == null ? null : left.getPosition());
        String rightPosition = normalizePosition(right == null ? null : right.getPosition());

        int positionCompare = Integer.compare(positionOrder(leftPosition), positionOrder(rightPosition));
        if (positionCompare != 0) {
            return positionCompare;
        }

        if ("post_list_card".equals(leftPosition) && "post_list_card".equals(rightPosition)) {
            int insertAfterCompare = Comparator.nullsLast(Integer::compareTo)
                    .compare(left == null ? null : left.getInsertAfter(), right == null ? null : right.getInsertAfter());
            if (insertAfterCompare != 0) {
                return insertAfterCompare;
            }
        }

        int weightCompare = Comparator.nullsLast(Integer::compareTo)
                .compare(right == null ? null : right.getWeight(), left == null ? null : left.getWeight());
        if (weightCompare != 0) {
            return weightCompare;
        }

        return Comparator.nullsLast(Long::compareTo)
                .compare(left == null ? null : left.getId(), right == null ? null : right.getId());
    }

    private int positionOrder(String position) {
        if (position == null) {
            return Integer.MAX_VALUE;
        }
        int idx = POSITION_ORDER.indexOf(position);
        return idx >= 0 ? idx : Integer.MAX_VALUE;
    }

    private List<ApplyPitOption> loadApplyPitOptions() {
        Map<Long, Advertisement> pitMap = loadActivePitAdvertisementMap();
        if (pitMap.isEmpty()) {
            return List.of();
        }

        Set<Long> orderedPitIds = loadPitIdSet();
        Map<Long, Integer> pitIndexMap = buildPitIndexMap(loadConfiguredPitAdvertisementMap(), orderedPitIds);
        List<Advertisement> ordered = getOrderedPitAdvertisements(pitMap, orderedPitIds);

        return ordered.stream()
                .map(ad -> new ApplyPitOption(
                        ad.getId(),
                        ad.getPosition(),
                        ad.getTitle(),
                        ad.getInsertAfter(),
                        pitIndexMap.get(ad.getId())))
                .toList();
    }

    private ResolvedPit validateAndResolvePitAd(Advertisement ad) {
        if (ad == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }

        Long pitAdId = ad.getPitAdId();
        if (pitAdId == null || pitAdId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择可申请坑位");
        }

        Map<Long, Advertisement> pitMap = loadActivePitAdvertisementMap();
        Map<Long, Integer> pitIndexMap = buildPitIndexMap(loadConfiguredPitAdvertisementMap(), loadPitIdSet());
        Advertisement pitAd = pitMap.get(pitAdId);
        if (pitAd == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "所选坑位暂不可申请，请刷新后重试");
        }
        Integer pitIndex = pitIndexMap.get(pitAdId);
        if (pitIndex == null || pitIndex < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "所选坑位暂不可申请，请刷新后重试");
        }

        String requestedPosition = normalizePosition(ad.getPosition());
        if (requestedPosition != null && !Objects.equals(requestedPosition, pitAd.getPosition())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "所选广告位与坑位不一致，请重新选择");
        }

        ad.setPosition(pitAd.getPosition());
        if ("post_list_card".equals(pitAd.getPosition())) {
            int insertAfter = pitAd.getInsertAfter() == null ? 4 : pitAd.getInsertAfter();
            insertAfter = Math.max(1, Math.min(19, insertAfter));
            ad.setInsertAfter(insertAfter);
        }

        ad.setPitIndex(pitIndex);

        return new ResolvedPit(pitAd, pitIndex);
    }

    private Map<String, String> loadReviewReasonMap() {
        String raw = systemConfigService.getValue(AD_REVIEW_REASONS_KEY);
        if (raw == null || raw.isBlank()) {
            return new HashMap<>();
        }

        try {
            Map<String, String> parsed = objectMapper.readValue(raw, new TypeReference<Map<String, String>>() {});
            return parsed == null ? new HashMap<>() : new HashMap<>(parsed);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void persistReviewReasonMap(Map<String, String> reasonMap) {
        try {
            String json = objectMapper.writeValueAsString(reasonMap == null ? Map.of() : reasonMap);
            try {
                systemConfigService.batchUpdate(Map.of(AD_REVIEW_REASONS_KEY, json));
            } catch (Exception e) {
                systemConfigService.createIfAbsent(AD_REVIEW_REASONS_KEY, json, "广告审核拒绝原因映射(JSON)");
            }
        } catch (Exception e) {
            log.warn("审核原因持久化失败", e);
        }
    }

    private List<PriceRule> loadPriceRules() {
        String raw = systemConfigService.getValue(AD_PRICE_RULES_KEY);
        if (raw == null || raw.isBlank()) {
            List<PriceRule> defaults = defaultPriceRules();
            persistPriceRules(defaults);
            return defaults;
        }

        try {
            List<PriceRule> parsed = objectMapper.readValue(raw, new TypeReference<List<PriceRule>>() {});
            List<PriceRule> normalized = sanitizeRules(parsed);
            if (normalized.isEmpty()) {
                List<PriceRule> defaults = defaultPriceRules();
                persistPriceRules(defaults);
                return defaults;
            }
            return normalized;
        } catch (Exception e) {
            List<PriceRule> defaults = defaultPriceRules();
            persistPriceRules(defaults);
            return defaults;
        }
    }

    private void persistPriceRules(List<PriceRule> rules) {
        try {
            String json = objectMapper.writeValueAsString(rules);
            try {
                systemConfigService.batchUpdate(Map.of(AD_PRICE_RULES_KEY, json));
            } catch (Exception ex) {
                systemConfigService.createIfAbsent(AD_PRICE_RULES_KEY, json, "广告位时效价格规则(JSON)");
            }
        } catch (Exception e) {
            log.warn("价格规则持久化失败", e);
        }
    }

    private List<PriceRule> sanitizeRules(List<PriceRule> rawRules) {
        if (rawRules == null || rawRules.isEmpty()) {
            return defaultPriceRules();
        }

        Map<String, PriceRule> uniqueRuleMap = new HashMap<>();
        for (PriceRule rule : rawRules) {
            if (rule == null
                    || !SUPPORTED_POSITIONS.contains(rule.getPosition())
                    || rule.getDurationDays() == null
                    || rule.getDurationDays() < 1
                    || rule.getPrice() == null
                    || rule.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                continue;
            }

            Integer pitIndex = normalizePitIndex(rule.getPitIndex());
            if (pitIndex == null) {
                continue;
            }

            PriceRule normalized = new PriceRule(
                    rule.getPosition(),
                    pitIndex,
                    rule.getDurationDays(),
                    rule.getPrice().setScale(2, RoundingMode.HALF_UP));

            String key = normalized.getPosition() + "#" + normalized.getPitIndex() + "#" + normalized.getDurationDays();
            uniqueRuleMap.putIfAbsent(key, normalized);
        }

        if (uniqueRuleMap.isEmpty()) {
            return defaultPriceRules();
        }

        return fillMissingPitRules(new ArrayList<>(uniqueRuleMap.values()));
    }

    private List<PriceRule> defaultPriceRules() {
        List<PriceRule> defaults = new ArrayList<>();
        for (String position : POSITION_ORDER) {
            for (Integer durationDays : DEFAULT_DURATION_DAYS) {
                BigDecimal basePrice = defaultBasePrice(position, durationDays);
                if (basePrice == null) {
                    continue;
                }

                for (int pitIndex = 1; pitIndex <= MAX_PIT_COUNT; pitIndex++) {
                    defaults.add(new PriceRule(position, pitIndex, durationDays, buildPitPrice(basePrice, pitIndex)));
                }
            }
        }
        return defaults;
    }

    private Integer normalizePitIndex(Integer rawPitIndex) {
        if (rawPitIndex == null) {
            return 1;
        }
        if (rawPitIndex < 1 || rawPitIndex > MAX_PIT_COUNT) {
            return null;
        }
        return rawPitIndex;
    }

    private BigDecimal pitPriceFactor(int pitIndex) {
        BigDecimal reduce = PIT_PRICE_STEP.multiply(BigDecimal.valueOf(Math.max(0, pitIndex - 1L)));
        BigDecimal factor = BigDecimal.ONE.subtract(reduce);
        if (factor.compareTo(MIN_PIT_PRICE_FACTOR) < 0) {
            return MIN_PIT_PRICE_FACTOR;
        }
        return factor;
    }

    private BigDecimal buildPitPrice(BigDecimal basePrice, int pitIndex) {
        if (basePrice == null) {
            return null;
        }
        return basePrice.multiply(pitPriceFactor(pitIndex)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultBasePrice(String position, int durationDays) {
        return switch (position) {
            case "home_left" -> switch (durationDays) {
                case 7 -> new BigDecimal("299");
                case 30 -> new BigDecimal("1099");
                case 90 -> new BigDecimal("2999");
                case 180 -> new BigDecimal("5399");
                default -> null;
            };
            case "post_top" -> switch (durationDays) {
                case 7 -> new BigDecimal("239");
                case 30 -> new BigDecimal("899");
                case 90 -> new BigDecimal("2499");
                case 180 -> new BigDecimal("4499");
                default -> null;
            };
            case "post_bottom" -> switch (durationDays) {
                case 7 -> new BigDecimal("179");
                case 30 -> new BigDecimal("699");
                case 90 -> new BigDecimal("1899");
                case 180 -> new BigDecimal("3499");
                default -> null;
            };
            case "post_list_card" -> switch (durationDays) {
                case 7 -> new BigDecimal("129");
                case 30 -> new BigDecimal("499");
                case 90 -> new BigDecimal("1399");
                case 180 -> new BigDecimal("2599");
                default -> null;
            };
            default -> null;
        };
    }

    private List<PriceRule> fillMissingPitRules(List<PriceRule> sourceRules) {
        Map<String, Map<Integer, Map<Integer, BigDecimal>>> matrix = new HashMap<>();
        for (PriceRule rule : sourceRules) {
            matrix.computeIfAbsent(rule.getPosition(), key -> new HashMap<>())
                    .computeIfAbsent(rule.getDurationDays(), key -> new HashMap<>())
                    .put(rule.getPitIndex(), rule.getPrice());
        }

        for (String position : POSITION_ORDER) {
            Map<Integer, Map<Integer, BigDecimal>> durationMap = matrix.computeIfAbsent(position, key -> new HashMap<>());
            List<Integer> durations = new ArrayList<>(durationMap.keySet());
            if (durations.isEmpty()) {
                durations.addAll(DEFAULT_DURATION_DAYS);
            }
            durations.sort(Integer::compareTo);

            for (Integer durationDays : durations) {
                Map<Integer, BigDecimal> pitPriceMap = durationMap.computeIfAbsent(durationDays, key -> new HashMap<>());

                BigDecimal firstPitPrice = pitPriceMap.get(1);
                if (firstPitPrice == null && !pitPriceMap.isEmpty()) {
                    Integer firstKey = pitPriceMap.keySet().stream().sorted().findFirst().orElse(null);
                    if (firstKey != null) {
                        BigDecimal sourcePrice = pitPriceMap.get(firstKey);
                        BigDecimal factor = pitPriceFactor(firstKey);
                        if (sourcePrice != null && factor.compareTo(BigDecimal.ZERO) > 0) {
                            firstPitPrice = sourcePrice.divide(factor, 2, RoundingMode.HALF_UP);
                            pitPriceMap.put(1, firstPitPrice);
                        }
                    }
                }

                if (firstPitPrice == null) {
                    firstPitPrice = defaultBasePrice(position, durationDays);
                    if (firstPitPrice != null) {
                        pitPriceMap.put(1, firstPitPrice);
                    }
                }

                if (firstPitPrice == null) {
                    continue;
                }

                for (int pitIndex = 1; pitIndex <= MAX_PIT_COUNT; pitIndex++) {
                    pitPriceMap.putIfAbsent(pitIndex, buildPitPrice(firstPitPrice, pitIndex));
                }
            }
        }

        List<PriceRule> normalized = new ArrayList<>();
        matrix.forEach((position, durationMap) -> {
            durationMap.forEach((durationDays, pitPriceMap) -> {
                for (int pitIndex = 1; pitIndex <= MAX_PIT_COUNT; pitIndex++) {
                    BigDecimal price = pitPriceMap.get(pitIndex);
                    if (price == null) {
                        continue;
                    }
                    normalized.add(new PriceRule(position, pitIndex, durationDays, price.setScale(2, RoundingMode.HALF_UP)));
                }
            });
        });

        normalized.sort(Comparator
                .comparing((PriceRule rule) -> positionOrder(rule.getPosition()))
                .thenComparing(PriceRule::getPitIndex)
                .thenComparing(PriceRule::getDurationDays));
        return normalized;
    }

    private void validateApplicationByRules(Advertisement ad, List<PriceRule> rules, Integer pitIndex) {
        String position = ad.getPosition() == null ? null : ad.getPosition().trim();
        String type = ad.getType() == null ? null : ad.getType().trim();
        String title = ad.getTitle() == null ? null : ad.getTitle().trim();
        ad.setPosition(position);
        ad.setType(type);
        ad.setTitle(title);

        if (position == null || !SUPPORTED_POSITIONS.contains(position)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "广告位置不合法");
        }
        if (!"image".equals(type)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户端仅支持图片广告申请");
        }
        if ("code".equals(type) && "post_list_card".equals(position)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "代码广告不支持文章列表拟态卡位");
        }

        if ("post_list_card".equals(position)) {
            if (title == null || title.isBlank()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "文章拟态卡广告标题不能为空");
            }
            if (title.length() > MAX_APPLICATION_TITLE_LENGTH) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "广告标题长度不能超过" + MAX_APPLICATION_TITLE_LENGTH + "个字符");
            }
        }

        if (ad.getStartTime() == null || ad.getEndTime() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择投放时长");
        }
        if (!ad.getEndTime().isAfter(ad.getStartTime())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "结束时间必须晚于开始时间");
        }

        long days = ChronoUnit.DAYS.between(ad.getStartTime(), ad.getEndTime());
        if (days < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "投放时长至少1天");
        }

        if (pitIndex == null || pitIndex < 1 || pitIndex > MAX_PIT_COUNT) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "所选坑位无效，请刷新后重试");
        }

        boolean matched = rules.stream().anyMatch(rule ->
                rule.getPosition().equals(position)
                        && Objects.equals(rule.getPitIndex(), pitIndex)
                        && rule.getDurationDays() != null
                        && rule.getDurationDays() == (int) days);

        if (!matched) {
            matched = rules.stream().anyMatch(rule ->
                    rule.getPosition().equals(position)
                            && Objects.equals(rule.getPitIndex(), 1)
                            && rule.getDurationDays() != null
                            && rule.getDurationDays() == (int) days);
        }

        if (!matched) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该坑位(#" + pitIndex + ")不支持当前投放时长，请按价格规则选择");
        }
    }

    public static class ApplyPitOption {
        private Long pitAdId;
        private String position;
        private String title;
        private Integer insertAfter;
        private Integer pitIndex;

        public ApplyPitOption() {
        }

        public ApplyPitOption(Long pitAdId, String position, String title, Integer insertAfter, Integer pitIndex) {
            this.pitAdId = pitAdId;
            this.position = position;
            this.title = title;
            this.insertAfter = insertAfter;
            this.pitIndex = pitIndex;
        }

        public Long getPitAdId() {
            return pitAdId;
        }

        public void setPitAdId(Long pitAdId) {
            this.pitAdId = pitAdId;
        }

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Integer getInsertAfter() {
            return insertAfter;
        }

        public void setInsertAfter(Integer insertAfter) {
            this.insertAfter = insertAfter;
        }

        public Integer getPitIndex() {
            return pitIndex;
        }

        public void setPitIndex(Integer pitIndex) {
            this.pitIndex = pitIndex;
        }
    }

    private static class ResolvedPit {
        private final Advertisement pitAd;
        private final Integer pitIndex;

        private ResolvedPit(Advertisement pitAd, Integer pitIndex) {
            this.pitAd = pitAd;
            this.pitIndex = pitIndex;
        }

        public Advertisement getPitAd() {
            return pitAd;
        }

        public Integer getPitIndex() {
            return pitIndex;
        }
    }

    public static class PriceRule {
        private String position;
        private Integer pitIndex;
        private Integer durationDays;
        private BigDecimal price;

        public PriceRule() {
        }

        public PriceRule(String position, Integer pitIndex, Integer durationDays, BigDecimal price) {
            this.position = position;
            this.pitIndex = pitIndex;
            this.durationDays = durationDays;
            this.price = price;
        }

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position;
        }

        public Integer getPitIndex() {
            return pitIndex;
        }

        public void setPitIndex(Integer pitIndex) {
            this.pitIndex = pitIndex;
        }

        public Integer getDurationDays() {
            return durationDays;
        }

        public void setDurationDays(Integer durationDays) {
            this.durationDays = durationDays;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }
    }
}
