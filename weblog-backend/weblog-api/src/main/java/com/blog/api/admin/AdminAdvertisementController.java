package com.blog.api.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.result.Result;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.entity.Advertisement;
import com.blog.content.service.AdPitBindingService;
import com.blog.content.service.AdvertisementService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.blog.infra.security.audit.AuditLog;
import com.blog.infra.security.util.XssUtil;
import com.blog.system.entity.User;
import com.blog.system.mapper.UserMapper;
import com.blog.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.blog.common.constant.CommonConstant.MAX_BATCH_SIZE;
import static com.blog.common.constant.CommonConstant.MAX_PAGE_SIZE;

/**
 * 管理端 - 广告管理
 */
@Tag(name = "管理端-广告管理", description = "广告审核、上下架、配置")
@RestController
@RequestMapping("/api/admin/advertisement")
@RequiredArgsConstructor
public class AdminAdvertisementController {

    private final AdvertisementService advertisementService;
    private final AdPitBindingService adPitBindingService;
    private final UserMapper userMapper;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;
    private static final Set<String> VALID_AD_STATUSES = Set.of("pending", "approved", "rejected", "active", "expired");
    private static final String AD_PRICE_RULES_KEY = "ad_price_rules";
    private static final String AD_REVIEW_REASONS_KEY = "ad_review_reasons";
    private static final String AD_APPLY_PIT_IDS_KEY = "ad_apply_pit_ids";
    private static final Set<String> SUPPORTED_POSITIONS = Set.of("home_left", "post_top", "post_bottom", "post_list_card");
    private static final Set<String> SLOT_LIMIT_POSITIONS = Set.of("home_left", "post_top", "post_bottom");
    private static final int MAX_SLOT_AD_COUNT = 7;
    private static final List<String> POSITION_ORDER = List.of("home_left", "post_top", "post_bottom", "post_list_card");
    private static final List<Integer> DEFAULT_DURATION_DAYS = List.of(7, 30, 90, 180);
    private static final int MAX_PIT_COUNT = 7;
    private static final BigDecimal PIT_PRICE_STEP = new BigDecimal("0.08");
    private static final BigDecimal MIN_PIT_PRICE_FACTOR = new BigDecimal("0.52");

    @Operation(summary = "广告列表（分页）")
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String position) {
        pageSize = (int) Math.min(pageSize, MAX_PAGE_SIZE);
        IPage<Advertisement> page = advertisementService.listPage(pageNum, pageSize, status, position);
        List<Advertisement> records = page.getRecords();
        attachAdvertiserInfo(records);
        attachReviewReasons(records);
        attachPitContext(records);
        return Result.success(Map.of(
                "records", records,
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "pages", page.getPages()));
    }

    @Operation(summary = "创建广告")
    @PostMapping
    @AuditLog(module = "广告管理", operation = "CREATE", description = "创建广告")
    public Result<Advertisement> create(@RequestBody Advertisement ad) {
        // 管理员创建的广告无需审核，直接投放
        ad.setStatus("active");
        String position = normalizePosition(ad.getPosition());
        ensurePositionCapacityForActive(position, null, null);

        Advertisement created = advertisementService.create(ad);
        reconcilePitSetting(created.getId(), created.getAdvertiserId(), created.getStatus(), created.getPosition(), ad.getPitEnabled());
        created.setPitEnabled(Boolean.TRUE.equals(ad.getPitEnabled()));
        return Result.success(created);
    }

    @Operation(summary = "更新广告")
    @PutMapping("/{id}")
    @AuditLog(module = "广告管理", operation = "UPDATE", description = "更新广告")
    public Result<Void> update(@PathVariable Long id, @RequestBody Advertisement ad) {
        Advertisement existing = advertisementService.getById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "广告不存在");
        }

        String nextPosition = normalizePosition(ad.getPosition() == null ? existing.getPosition() : ad.getPosition());
        String nextStatus = ad.getStatus() == null ? existing.getStatus() : ad.getStatus().trim();
        if ("active".equals(nextStatus)) {
            ensurePositionCapacityForActive(nextPosition, existing, null);
        }

        advertisementService.update(id, ad);

        Long advertiserId = existing.getAdvertiserId();
        String effectiveStatus = nextStatus;
        String effectivePosition = nextPosition;
        reconcilePitSetting(id, advertiserId, effectiveStatus, effectivePosition, ad.getPitEnabled());
        return Result.success();
    }

    @Operation(summary = "快捷切换广告坑位")
    @PutMapping("/{id}/pit")
    @AuditLog(module = "广告管理", operation = "UPDATE", description = "快捷切换广告坑位")
    public Result<Void> updatePitEnabled(@PathVariable Long id, @RequestBody PitToggleRequest body) {
        Advertisement existing = advertisementService.getById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "广告不存在");
        }
        if (body == null || body.getEnabled() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "缺少坑位开关参数");
        }

        reconcilePitSetting(id,
                existing.getAdvertiserId(),
                existing.getStatus(),
                existing.getPosition(),
                body.getEnabled());
        return Result.success();
    }

    @Operation(summary = "调整坑位顺序")
    @PutMapping("/pit-order")
    @AuditLog(module = "广告管理", operation = "UPDATE", description = "调整坑位顺序")
    public Result<Void> updatePitOrder(@RequestBody PitOrderRequest body) {
        if (body == null || body.getPosition() == null || body.getPosition().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "坑位位置不能为空");
        }
        List<Long> pitIds = body.getPitIds();
        if (pitIds == null || pitIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "坑位顺序不能为空");
        }

        String normalizedPosition = normalizePosition(body.getPosition());
        if (!SUPPORTED_POSITIONS.contains(normalizedPosition)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持已开放广告位调整坑位顺序");
        }

        List<Long> normalizedOrderIds = normalizeIds(pitIds);
        if (normalizedOrderIds.size() != pitIds.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "坑位顺序包含重复或非法ID");
        }

        Set<Long> currentPitIds = loadPitIdSet();
        if (currentPitIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前没有可调整的坑位");
        }

        List<Long> targetPositionPitIds = getOrderedPitIdsByPosition(normalizedPosition, currentPitIds);
        if (targetPositionPitIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前广告位没有已开放坑位");
        }
        if (targetPositionPitIds.size() != normalizedOrderIds.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "提交的坑位数量与当前配置不一致，请刷新后重试");
        }
        if (!new LinkedHashSet<>(targetPositionPitIds).equals(new LinkedHashSet<>(normalizedOrderIds))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "提交的坑位集合与当前配置不一致，请刷新后重试");
        }

        LinkedHashSet<Long> reordered = new LinkedHashSet<>();
        boolean inserted = false;
        for (Long pitId : currentPitIds) {
            if (targetPositionPitIds.contains(pitId)) {
                if (!inserted) {
                    reordered.addAll(normalizedOrderIds);
                    inserted = true;
                }
                continue;
            }
            reordered.add(pitId);
        }
        if (!inserted) {
            reordered.addAll(normalizedOrderIds);
        }
        persistPitIdSet(reordered);
        return Result.success();
    }

    @Operation(summary = "审核/上下架广告")
    @PutMapping("/{id}/status")
    @AuditLog(module = "广告管理", operation = "UPDATE", description = "审核广告")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String reason) {
        if (!VALID_AD_STATUSES.contains(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的状态值，仅支持: pending, approved, rejected, active, expired");
        }

        String normalizedReason = null;
        if ("rejected".equals(status)) {
            String raw = reason == null ? "" : reason.trim();
            if (raw.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "拒绝时必须填写原因");
            }
            normalizedReason = XssUtil.cleanText(raw);
            if (normalizedReason.length() > 200) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "拒绝原因不能超过 200 个字符");
            }
        }

        Advertisement existing = advertisementService.getById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "广告不存在");
        }

        Advertisement pitTarget = null;
        if ("active".equals(status)) {
            pitTarget = resolvePitReplacement(existing);
            if (existing.getAdvertiserId() != null
                    && "pending".equals(existing.getStatus())
                    && pitTarget == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "该申请未绑定有效坑位，请让用户重新提交申请");
            }
            ensurePositionCapacityForActive(normalizePosition(existing.getPosition()), existing, pitTarget);
            if (pitTarget != null
                    && pitTarget.getId() != null
                    && !Objects.equals(pitTarget.getId(), id)
                    && "active".equals(pitTarget.getStatus())) {
                advertisementService.updateStatus(pitTarget.getId(), "expired");
            }
        }

        advertisementService.updateStatus(id, status);

        if (!"active".equals(status)) {
            removePitIds(List.of(id));
        }

        if ("rejected".equals(status) || "expired".equals(status)) {
            adPitBindingService.removeByApplyOrPitAdIds(List.of(id));
        }

        Map<String, String> reviewReasonMap = loadReviewReasonMap();
        if ("rejected".equals(status)) {
            reviewReasonMap.put(String.valueOf(id), normalizedReason);
        } else {
            reviewReasonMap.remove(String.valueOf(id));
        }
        persistReviewReasonMap(reviewReasonMap);

        return Result.success();
    }

    @Operation(summary = "删除广告")
    @DeleteMapping("/{id}")
    @AuditLog(module = "广告管理", operation = "DELETE", description = "删除广告")
    public Result<Void> delete(@PathVariable Long id) {
        advertisementService.delete(id);
        removePitIds(List.of(id));
        removePitBindings(List.of(id));
        Map<String, String> reviewReasonMap = loadReviewReasonMap();
        if (reviewReasonMap.remove(String.valueOf(id)) != null) {
            persistReviewReasonMap(reviewReasonMap);
        }
        return Result.success();
    }

    @Operation(summary = "批量删除广告")
    @DeleteMapping("/batch")
    @AuditLog(module = "广告管理", operation = "DELETE", description = "批量删除广告")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            if (ids.size() > MAX_BATCH_SIZE) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "批量操作数量不能超过 " + MAX_BATCH_SIZE);
            }
            advertisementService.batchDelete(ids);
            removePitIds(ids);
            removePitBindings(ids);

            Map<String, String> reviewReasonMap = loadReviewReasonMap();
            boolean changed = false;
            for (Long id : ids) {
                if (id != null && reviewReasonMap.remove(String.valueOf(id)) != null) {
                    changed = true;
                }
            }
            if (changed) {
                persistReviewReasonMap(reviewReasonMap);
            }
        }
        return Result.success();
    }

    @Operation(summary = "批量上下架广告")
    @PutMapping("/batch/status")
    @AuditLog(module = "广告管理", operation = "UPDATE", description = "批量上下架广告")
    public Result<Void> batchUpdateStatus(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        String status = (String) body.get("status");
        if (status != null && !VALID_AD_STATUSES.contains(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的状态值，仅支持: pending, approved, rejected, active, expired");
        }
        if ("rejected".equals(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "批量拒绝请在申请详情中逐条填写原因");
        }
        if (ids != null && !ids.isEmpty() && status != null) {
            List<Long> longIds = ids.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());
            if ("active".equals(status)) {
                for (Long longId : longIds) {
                    Advertisement existing = advertisementService.getById(longId);
                    if (existing == null) {
                        continue;
                    }

                    Advertisement pitTarget = resolvePitReplacement(existing);
                    if (existing.getAdvertiserId() != null
                            && "pending".equals(existing.getStatus())
                            && pitTarget == null) {
                        throw new BusinessException(ResultCode.BAD_REQUEST,
                                "广告ID " + longId + " 未绑定有效坑位，请改为在详情中处理或让用户重新提交申请");
                    }
                    ensurePositionCapacityForActive(normalizePosition(existing.getPosition()), existing, pitTarget);

                    if (pitTarget != null
                            && pitTarget.getId() != null
                            && !Objects.equals(pitTarget.getId(), longId)
                            && "active".equals(pitTarget.getStatus())) {
                        advertisementService.updateStatus(pitTarget.getId(), "expired");
                    }

                    advertisementService.updateStatus(longId, "active");
                }
            } else {
                advertisementService.batchUpdateStatus(longIds, status);
                removePitIds(longIds);
                if ("expired".equals(status)) {
                    adPitBindingService.removeByApplyOrPitAdIds(longIds);
                }
            }

            Map<String, String> reviewReasonMap = loadReviewReasonMap();
            boolean changed = false;
            for (Long longId : longIds) {
                if (reviewReasonMap.remove(String.valueOf(longId)) != null) {
                    changed = true;
                }
            }
            if (changed) {
                persistReviewReasonMap(reviewReasonMap);
            }
        }
        return Result.success();
    }

    @Operation(summary = "获取广告申请开关状态")
    @GetMapping("/apply-switch")
    public Result<Map<String, Object>> getApplySwitch() {
        String val = systemConfigService.getValue("ad_apply_enabled");
        return Result.success(Map.of("enabled", "true".equals(val)));
    }

    @Operation(summary = "设置广告申请开关")
    @PutMapping("/apply-switch")
    @AuditLog(module = "广告管理", operation = "UPDATE", description = "设置广告申请开关")
    public Result<Void> setApplySwitch(@RequestBody Map<String, Object> body) {
        Boolean enabled = (Boolean) body.get("enabled");
        String val = enabled != null && enabled ? "true" : "false";
        try {
            systemConfigService.batchUpdate(Map.of("ad_apply_enabled", val));
        } catch (Exception e) {
            // 配置项不存在时，尝试创建
            systemConfigService.createIfAbsent("ad_apply_enabled", val, "广告申请入口开关");
        }
        return Result.success();
    }

    @Operation(summary = "获取广告价格规则")
    @GetMapping("/price-rules")
    public Result<Map<String, Object>> getPriceRules() {
        return Result.success(Map.of("rules", loadPriceRules()));
    }

    @Operation(summary = "更新广告价格规则")
    @PutMapping("/price-rules")
    @AuditLog(module = "广告管理", operation = "UPDATE", description = "更新广告价格规则")
    public Result<Void> setPriceRules(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rulesBody = (List<Map<String, Object>>) body.get("rules");
        if (rulesBody == null || rulesBody.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "价格规则不能为空");
        }

        List<PriceRule> rules = rulesBody.stream().map(this::parseRule).toList();
        validateRules(rules);

        try {
            String json = objectMapper.writeValueAsString(rules.stream()
                    .sorted(Comparator.comparing(PriceRule::getPosition)
                            .thenComparing(PriceRule::getPitIndex)
                            .thenComparing(PriceRule::getDurationDays))
                    .toList());
            try {
                systemConfigService.batchUpdate(Map.of(AD_PRICE_RULES_KEY, json));
            } catch (Exception e) {
                systemConfigService.createIfAbsent(AD_PRICE_RULES_KEY, json, "广告位时效价格规则(JSON)");
            }
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "价格规则保存失败");
        }

        return Result.success();
    }

    // ========== 回收站 ==========

    @Operation(summary = "回收站列表（分页）")
    @GetMapping("/trash")
    public Result<Map<String, Object>> trashPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        var page = advertisementService.pageDeleted(pageNum, pageSize, keyword);
        List<Advertisement> records = page.getRecords();
        attachReviewReasons(records);
        attachPitContext(records);
        return Result.success(Map.of(
                "records", records,
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "pages", page.getPages()));
    }

    @Operation(summary = "批量恢复广告")
    @PutMapping("/trash/batch-restore")
    @AuditLog(module = "广告管理", operation = "UPDATE", description = "批量恢复广告")
    public Result<Integer> batchRestore(@RequestBody List<Long> ids) {
        return Result.success(advertisementService.batchRestore(ids));
    }

    @Operation(summary = "批量永久删除广告")
    @DeleteMapping("/trash/batch-permanent")
    @AuditLog(module = "广告管理", operation = "DELETE", description = "批量永久删除广告")
    public Result<Integer> batchPermanentDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        List<Long> longIds = ids.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());
        int deletedCount = advertisementService.batchPermanentDelete(longIds);
        removePitIds(longIds);
        removePitBindings(longIds);

        Map<String, String> reviewReasonMap = loadReviewReasonMap();
        boolean changed = false;
        for (Long id : longIds) {
            if (reviewReasonMap.remove(String.valueOf(id)) != null) {
                changed = true;
            }
        }
        if (changed) {
            persistReviewReasonMap(reviewReasonMap);
        }

        return Result.success(deletedCount);
    }

    @Operation(summary = "清空回收站")
    @DeleteMapping("/trash/clear")
    @AuditLog(module = "广告管理", operation = "DELETE", description = "清空广告回收站")
    public Result<Integer> clearTrash() {
        List<Long> deletedIds = advertisementService.listDeletedIds();
        int deletedCount = advertisementService.clearTrash();
        if (deletedIds != null && !deletedIds.isEmpty()) {
            removePitIds(deletedIds);
            removePitBindings(deletedIds);

            Map<String, String> reviewReasonMap = loadReviewReasonMap();
            boolean changed = false;
            for (Long id : deletedIds) {
                if (id != null && reviewReasonMap.remove(String.valueOf(id)) != null) {
                    changed = true;
                }
            }
            if (changed) {
                persistReviewReasonMap(reviewReasonMap);
            }
        }
        return Result.success(deletedCount);
    }

    private void attachReviewReasons(List<Advertisement> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        Map<String, String> reviewReasonMap = loadReviewReasonMap();
        for (Advertisement record : records) {
            if (record == null || record.getId() == null) {
                continue;
            }
            record.setReviewReason(reviewReasonMap.get(String.valueOf(record.getId())));
        }
    }

    private void attachAdvertiserInfo(List<Advertisement> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> advertiserIds = records.stream()
                .map(Advertisement::getAdvertiserId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (advertiserIds.isEmpty()) {
            return;
        }

        List<User> users = userMapper.selectByIds(advertiserIds);
        if (users == null || users.isEmpty()) {
            return;
        }

        Map<Long, User> userMap = users.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(User::getId, user -> user, (left, right) -> left));

        for (Advertisement record : records) {
            if (record == null || record.getAdvertiserId() == null) {
                continue;
            }
            User user = userMap.get(record.getAdvertiserId());
            if (user == null) {
                continue;
            }
            record.setAdvertiserEmail(user.getEmail());
            record.setAdvertiserNickname(user.getNickname());
        }
    }

    private void attachPitContext(List<Advertisement> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        Set<Long> pitIds = loadPitIdSet();
        Map<Long, Advertisement> pitAdMap = loadActivePitAdvertisementMap();
        Map<String, Long> pitBindingMap = adPitBindingService.loadBindingMap();
        Map<Long, Integer> pitIndexMap = buildPitIndexMap(pitAdMap, pitIds);
        for (Advertisement record : records) {
            if (record == null || record.getId() == null) {
                continue;
            }
            String normalizedPosition = normalizePosition(record.getPosition());
            boolean enabled = pitIds.contains(record.getId())
                    && record.getAdvertiserId() == null
                    && "active".equals(record.getStatus())
                    && SUPPORTED_POSITIONS.contains(normalizedPosition);
            record.setPitEnabled(enabled);

            Long pitAdId = null;
            if (record.getAdvertiserId() != null) {
                pitAdId = pitBindingMap.get(String.valueOf(record.getId()));
            } else if (pitIds.contains(record.getId())) {
                pitAdId = record.getId();
            }

            if (pitAdId != null) {
                record.setPitAdId(pitAdId);
                record.setPitIndex(pitIndexMap.get(pitAdId));
                Advertisement pitAd = pitAdMap.get(pitAdId);
                if (pitAd != null) {
                    record.setPitTitle(pitAd.getTitle());
                }
            }
        }
    }

    private Map<Long, Advertisement> loadActivePitAdvertisementMap() {
        Set<Long> pitIds = loadPitIdSet();
        if (pitIds.isEmpty()) {
            return Map.of();
        }

        List<Advertisement> candidates = advertisementService.listByIds(new ArrayList<>(pitIds));
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

    private Map<Long, Integer> buildPitIndexMap(Map<Long, Advertisement> pitMap, Set<Long> orderedPitIds) {
        if (pitMap == null || pitMap.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> sequenceByPosition = new HashMap<>();
        Map<Long, Integer> pitIndexMap = new HashMap<>();
        for (Advertisement ad : getOrderedPitAdvertisements(pitMap, orderedPitIds)) {
            if (ad == null || ad.getId() == null) {
                continue;
            }
            String position = normalizePosition(ad.getPosition());
            if (!SUPPORTED_POSITIONS.contains(position)) {
                continue;
            }

            int next = sequenceByPosition.getOrDefault(position, 0) + 1;
            sequenceByPosition.put(position, next);
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

    private List<Long> getOrderedPitIdsByPosition(String position, Set<Long> orderedPitIds) {
        if (position == null || orderedPitIds == null || orderedPitIds.isEmpty()) {
            return List.of();
        }

        List<Advertisement> advertisements = advertisementService.listByIds(new ArrayList<>(orderedPitIds));
        Map<Long, Advertisement> advertisementMap = new HashMap<>();
        for (Advertisement advertisement : advertisements) {
            if (advertisement == null || advertisement.getId() == null) {
                continue;
            }
            advertisementMap.put(advertisement.getId(), advertisement);
        }

        List<Long> result = new ArrayList<>();
        for (Long pitId : orderedPitIds) {
            Advertisement advertisement = advertisementMap.get(pitId);
            if (advertisement == null
                    || advertisement.getAdvertiserId() != null
                    || !"active".equals(advertisement.getStatus())
                    || !Objects.equals(position, normalizePosition(advertisement.getPosition()))) {
                continue;
            }
            result.add(pitId);
        }
        return result;
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

    private void ensurePositionCapacityForActive(String position, Advertisement self, Advertisement replacedAd) {
        String normalized = normalizePosition(position);
        if (normalized == null || !SUPPORTED_POSITIONS.contains(normalized)) {
            return;
        }

        long activeCount = advertisementService.countActiveByPosition(normalized);

        if (self != null
                && self.getId() != null
                && "active".equals(self.getStatus())
                && Objects.equals(normalized, normalizePosition(self.getPosition()))) {
            activeCount = Math.max(0, activeCount - 1);
        }

        if (replacedAd != null
                && replacedAd.getId() != null
                && !Objects.equals(self == null ? null : self.getId(), replacedAd.getId())
                && "active".equals(replacedAd.getStatus())
                && Objects.equals(normalized, normalizePosition(replacedAd.getPosition()))) {
            activeCount = Math.max(0, activeCount - 1);
        }

        if (SLOT_LIMIT_POSITIONS.contains(normalized) && activeCount >= MAX_SLOT_AD_COUNT) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该广告位最多仅可设置 7 个投放广告");
        }

        if ("post_list_card".equals(normalized)) {
            int capacity = advertisementService.getMimicSlotCapacity();
            if (capacity < 1) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "当前暂无可用拟态广告位，请先发布文章");
            }
            if (activeCount >= capacity) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "拟态广告位已满，当前最多可设置 " + capacity + " 个（每 19 篇文章 1 个广告位，最多 7 个）");
            }
        }
    }

    private Advertisement resolvePitReplacement(Advertisement target) {
        if (target == null || target.getId() == null || target.getAdvertiserId() == null) {
            return null;
        }
        if (!"pending".equals(target.getStatus())) {
            return null;
        }

        Long pitAdId = adPitBindingService.getPitAdIdByApplyAdId(target.getId());
        if (pitAdId == null) {
            return null;
        }

        Advertisement pitAd = advertisementService.getById(pitAdId);
        if (pitAd == null || pitAd.getAdvertiserId() != null) {
            return null;
        }

        if (!Objects.equals(normalizePosition(target.getPosition()), normalizePosition(pitAd.getPosition()))) {
            return null;
        }
        return pitAd;
    }

    private void reconcilePitSetting(Long adId,
                                     Long advertiserId,
                                     String status,
                                     String position,
                                     Boolean requestedPitEnabled) {
        if (adId == null) {
            return;
        }

        String normalizedPosition = normalizePosition(position);
        boolean validCandidate = advertiserId == null
                && "active".equals(status)
                && SUPPORTED_POSITIONS.contains(normalizedPosition);

        if (Boolean.TRUE.equals(requestedPitEnabled) && !validCandidate) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持将投放中的管理员广告设置为申请坑位");
        }

        Set<Long> pitIds = loadPitIdSet();
        boolean current = pitIds.contains(adId);
        boolean next = requestedPitEnabled != null ? requestedPitEnabled : current;
        if (!validCandidate) {
            next = false;
        }

        if (current == next) {
            return;
        }

        if (next) {
            pitIds.add(adId);
        } else {
            pitIds.remove(adId);
        }
        persistPitIdSet(pitIds);
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
                } catch (Exception ignored) {
                    // 忽略非法ID
                }
            }
            return result;
        } catch (Exception e) {
            return new LinkedHashSet<>();
        }
    }

    private void persistPitIdSet(Set<Long> pitIds) {
        try {
            String json = objectMapper.writeValueAsString(pitIds == null ? List.of() : pitIds.stream().toList());
            try {
                systemConfigService.batchUpdate(Map.of(AD_APPLY_PIT_IDS_KEY, json));
            } catch (Exception e) {
                systemConfigService.createIfAbsent(AD_APPLY_PIT_IDS_KEY, json, "广告申请坑位广告ID列表(JSON)");
            }
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "坑位配置保存失败");
        }
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                normalized.add(id);
            }
        }
        return new ArrayList<>(normalized);
    }

    private void removePitIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Set<Long> pitIds = loadPitIdSet();
        boolean changed = false;
        for (Long id : ids) {
            if (id != null && pitIds.remove(id)) {
                changed = true;
            }
        }
        if (changed) {
            persistPitIdSet(pitIds);
        }
    }

    private void removePitBindings(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        adPitBindingService.removeByApplyOrPitAdIds(ids);
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "审核备注保存失败");
        }
    }

    private List<PriceRule> loadPriceRules() {
        String raw = systemConfigService.getValue(AD_PRICE_RULES_KEY);
        if (raw == null || raw.isBlank()) {
            return defaultPriceRules();
        }

        try {
            List<PriceRule> parsed = objectMapper.readValue(raw, new TypeReference<List<PriceRule>>() {});
            return sanitizePriceRules(parsed);
        } catch (Exception e) {
            return defaultPriceRules();
        }
    }

    private List<PriceRule> sanitizePriceRules(List<PriceRule> rawRules) {
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

    private PriceRule parseRule(Map<String, Object> row) {
        if (row == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "价格规则项不能为空");
        }

        String position = row.get("position") == null ? null : String.valueOf(row.get("position")).trim();
        Integer pitIndex = row.get("pitIndex") == null ? 1 : parseInteger(row.get("pitIndex"), "坑位编号必须是正整数");
        Integer durationDays = parseInteger(row.get("durationDays"), "投放天数必须是正整数");
        BigDecimal price = parseDecimal(row.get("price"), "价格格式不正确");

        return new PriceRule(position, pitIndex, durationDays, price);
    }

    private Integer parseInteger(Object value, String errorMsg) {
        if (value == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, errorMsg);
        }
        try {
            if (value instanceof Number number) {
                return number.intValue();
            }
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, errorMsg);
        }
    }

    private BigDecimal parseDecimal(Object value, String errorMsg) {
        if (value == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, errorMsg);
        }
        try {
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, errorMsg);
        }
    }

    private void validateRules(List<PriceRule> rules) {
        if (rules.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "价格规则不能为空");
        }

        java.util.Set<String> uniqueKeys = new java.util.HashSet<>();
        for (PriceRule rule : rules) {
            if (!SUPPORTED_POSITIONS.contains(rule.getPosition())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "存在不支持的广告位: " + rule.getPosition());
            }
            if (rule.getPitIndex() == null || rule.getPitIndex() < 1 || rule.getPitIndex() > MAX_PIT_COUNT) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "坑位编号必须在 1 ~ " + MAX_PIT_COUNT + " 之间");
            }
            if (rule.getDurationDays() == null || rule.getDurationDays() < 1 || rule.getDurationDays() > 3650) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "投放天数必须在 1 ~ 3650 之间");
            }
            if (rule.getPrice() == null || rule.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "价格不能小于 0");
            }

            String key = rule.getPosition() + "#" + rule.getPitIndex() + "#" + rule.getDurationDays();
            if (!uniqueKeys.add(key)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "存在重复规则: " + key);
            }
        }
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

    public static class PitToggleRequest {
        private Boolean enabled;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class PitOrderRequest {
        private String position;
        private List<Long> pitIds;

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position;
        }

        public List<Long> getPitIds() {
            return pitIds;
        }

        public void setPitIds(List<Long> pitIds) {
            this.pitIds = pitIds;
        }
    }
}
