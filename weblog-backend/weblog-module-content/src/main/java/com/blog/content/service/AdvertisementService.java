package com.blog.content.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.content.entity.AdPitBinding;
import com.blog.content.entity.Advertisement;
import com.blog.content.entity.Post;
import com.blog.content.mapper.AdvertisementMapper;
import com.blog.content.mapper.PostMapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.infra.security.sensitive.SensitiveWordService;
import com.blog.infra.security.util.XssUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 广告服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvertisementService {

    private static final Map<String, String> POSITION_ALIAS_MAP = Map.ofEntries(
            Map.entry("top", "home_top"),
            Map.entry("sidebar", "home_left"),
            Map.entry("middle", "post_inline"),
            Map.entry("bottom", "post_bottom"),
            Map.entry("home_left", "home_left"),
            Map.entry("post_top", "post_top"),
            Map.entry("post_bottom", "post_bottom"),
            Map.entry("post_inline", "post_inline"),
            Map.entry("post_list_card", "post_list_card")
    );

    private static final int DEFAULT_ROTATE_INTERVAL_SEC = 6;
    private static final int MAX_SLOT_AD_COUNT = 7;
    private static final int MIMIC_POSTS_PER_PAGE = 19;
    private static final int MAX_MIMIC_AD_COUNT = 7;
    private static final int MAX_APPLICATION_IMAGE_URL_LENGTH = 500;
    private static final int MAX_APPLICATION_CODE_LENGTH = 20000;
    private static final int MAX_APPLICATION_LINK_URL_LENGTH = 500;
    private static final int MAX_APPLICATION_AD_INFO_LENGTH = 120;
    private static final int MAX_APPLICATION_MIMIC_CONTENT_LENGTH = 120;
    private static final List<String> CAROUSEL_POSITIONS = List.of("home_left", "post_top", "post_bottom");
    private static final Set<String> PIT_OCCUPIED_STATUSES = Set.of("pending", "approved", "active");

    private final AdvertisementMapper advertisementMapper;
    private final PostMapper postMapper;
    private final AdPitBindingService adPitBindingService;
    private final SensitiveWordService sensitiveWordService;

    /**
     * 按位置获取有效广告（用户端）
     */
    public List<Advertisement> getActiveByPosition(String position) {
        return getActiveBySlot(position);
    }

    public List<Advertisement> getActiveBySlot(String slotOrPosition) {
        String normalizedSlot = normalizePosition(slotOrPosition);
        if (normalizedSlot == null) {
            return List.of();
        }

        List<String> candidatePositions = buildCandidatePositions(normalizedSlot);
        LocalDateTime now = LocalDateTime.now();
        List<Advertisement> records = advertisementMapper.selectList(
                new LambdaQueryWrapper<Advertisement>()
                        .in(Advertisement::getPosition, candidatePositions)
                        .eq(Advertisement::getStatus, "active")
                        .and(w -> w
                                .isNull(Advertisement::getStartTime)
                                .or()
                                .le(Advertisement::getStartTime, now))
                        .and(w -> w
                                .isNull(Advertisement::getEndTime)
                                .or()
                                .ge(Advertisement::getEndTime, now))
                        .orderByDesc(Advertisement::getWeight));

        records.forEach(ad -> {
            if (isCarouselPosition(normalizePosition(ad.getPosition()))) {
                ad.setClosable(true);
                ad.setAutoRotate(true);
                if (ad.getRotateIntervalSec() == null || ad.getRotateIntervalSec() < 2) {
                    ad.setRotateIntervalSec(DEFAULT_ROTATE_INTERVAL_SEC);
                }
            }
        });
        return applySlotDisplayLimit(normalizedSlot, records);
    }

    /**
     * 记录广告点击
     */
    public void recordClick(Long id) {
        Advertisement ad = advertisementMapper.selectById(id);
        if (ad != null) {
            ad.setClickCount(ad.getClickCount() + 1);
            advertisementMapper.updateById(ad);
        }
    }

    public Advertisement getById(Long id) {
        if (id == null) {
            return null;
        }
        return advertisementMapper.selectById(id);
    }

    public List<Advertisement> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return advertisementMapper.selectByIds(ids);
    }

    public long countActiveByPosition(String position) {
        String normalized = normalizePosition(position);
        if (normalized == null || normalized.isBlank()) {
            return 0L;
        }

        return advertisementMapper.selectCount(
                new LambdaQueryWrapper<Advertisement>()
                        .eq(Advertisement::getPosition, normalized)
                        .eq(Advertisement::getStatus, "active")
        );
    }

    public int getMimicSlotCapacity() {
        Long publishedCount = postMapper.selectCount(
                new QueryWrapper<Post>()
                        .eq("status", "published")
                        .ne("is_disabled", 1)
        );
        long total = publishedCount == null ? 0L : publishedCount;
        if (total <= 0) {
            return 0;
        }

        long pages = (total + MIMIC_POSTS_PER_PAGE - 1) / MIMIC_POSTS_PER_PAGE;
        return (int) Math.min(MAX_MIMIC_AD_COUNT, pages);
    }

    // ========== 管理端方法 ==========

    /**
     * 分页查询广告（管理端）
     */
    public IPage<Advertisement> listPage(int pageNum, int pageSize, String status, String position) {
        LambdaQueryWrapper<Advertisement> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Advertisement::getStatus, status);
        }
        if (position != null && !position.isEmpty()) {
            String normalized = normalizePosition(position);
            wrapper.in(Advertisement::getPosition, buildCandidatePositions(normalized));
        }
        wrapper.orderByDesc(Advertisement::getCreateTime);
        return advertisementMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 创建广告
     */
    public Advertisement create(Advertisement ad) {
        sanitizeAdvertisementFields(ad, ad.getType(), true);
        normalizeDisplaySettings(ad);
        validateSchedule(ad);
        if (ad.getStatus() == null) ad.setStatus("pending");
        if (ad.getClickCount() == null) ad.setClickCount(0);
        if (ad.getWeight() == null) ad.setWeight(1);
        advertisementMapper.insert(ad);
        return ad;
    }

    /**
     * 更新广告
     */
    public void update(Long id, Advertisement ad) {
        Advertisement existing = advertisementMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "广告不存在");
        }

        String effectiveType = ad.getType() != null ? ad.getType() : existing.getType();
        sanitizeAdvertisementFields(ad, effectiveType, false);
        normalizeDisplaySettings(ad);
        validateSchedule(ad);

        ad.setId(id);
        advertisementMapper.updateById(ad);
    }

    /**
     * 审核广告（approved/rejected）
     */
    public void updateStatus(Long id, String status) {
        Advertisement existing = advertisementMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "广告不存在");
        }

        Advertisement update = new Advertisement();
        update.setId(id);
        update.setStatus(status);

        // 用户申请广告从待审核转为投放中时，若审核时刻已晚于申请起始时刻，自动顺延完整投放时长
        if ("active".equals(status)
                && "pending".equals(existing.getStatus())
                && existing.getAdvertiserId() != null
                && existing.getStartTime() != null
                && existing.getEndTime() != null
                && existing.getEndTime().isAfter(existing.getStartTime())) {
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(existing.getStartTime())) {
                long durationSeconds = ChronoUnit.SECONDS.between(existing.getStartTime(), existing.getEndTime());
                if (durationSeconds > 0) {
                    update.setStartTime(now);
                    update.setEndTime(now.plusSeconds(durationSeconds));
                    log.info("广告审核顺延: adId={}, oldStart={}, oldEnd={}, newStart={}, newEnd={}",
                            id,
                            existing.getStartTime(),
                            existing.getEndTime(),
                            update.getStartTime(),
                            update.getEndTime());
                }
            }
        }

        advertisementMapper.updateById(update);
    }

    /**
     * 删除广告
     */
    public void delete(Long id) {
        advertisementMapper.deleteById(id);
    }

    /**
     * 批量删除广告
     */
    public void batchDelete(List<Long> ids) {
        advertisementMapper.deleteByIds(ids);
    }

    /**
     * 批量更新广告状态
     */
    public void batchUpdateStatus(List<Long> ids, String status) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        if ("active".equals(status)) {
            // 复用单条审核逻辑，确保用户申请广告在延迟审核时自动顺延完整投放时长
            for (Long id : ids) {
                if (id == null) {
                    continue;
                }
                Advertisement existing = advertisementMapper.selectById(id);
                if (existing == null) {
                    continue;
                }

                Advertisement update = new Advertisement();
                update.setId(id);
                update.setStatus(status);

                if ("pending".equals(existing.getStatus())
                        && existing.getAdvertiserId() != null
                        && existing.getStartTime() != null
                        && existing.getEndTime() != null
                        && existing.getEndTime().isAfter(existing.getStartTime())) {
                    LocalDateTime now = LocalDateTime.now();
                    if (now.isAfter(existing.getStartTime())) {
                        long durationSeconds = ChronoUnit.SECONDS.between(existing.getStartTime(), existing.getEndTime());
                        if (durationSeconds > 0) {
                            update.setStartTime(now);
                            update.setEndTime(now.plusSeconds(durationSeconds));
                        }
                    }
                }

                advertisementMapper.updateById(update);
            }
            return;
        }

        advertisementMapper.update(null, new LambdaUpdateWrapper<Advertisement>()
                .in(Advertisement::getId, ids)
                .set(Advertisement::getStatus, status));
    }

    /**
     * 用户提交广告申请
     */
    public Advertisement submitApplication(Long userId, Advertisement ad) {
        sanitizeAdvertisementFields(ad, ad.getType(), true);
        normalizeDisplaySettings(ad);
        validateSchedule(ad);
        validateApplicationInput(ad);

        if (ad.getPosition() == null || ad.getPosition().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "广告位置不能为空");
        }

        Advertisement existing = advertisementMapper.selectOne(
                new LambdaQueryWrapper<Advertisement>()
                        .eq(Advertisement::getAdvertiserId, userId)
                        .eq(Advertisement::getPosition, ad.getPosition())
                        .orderByDesc(Advertisement::getCreateTime)
                        .last("LIMIT 1"));

        if (StrUtil.isBlank(ad.getTitle())) {
            if (existing != null && StrUtil.isNotBlank(existing.getTitle())) {
                ad.setTitle(existing.getTitle());
            } else {
                ad.setTitle(buildApplicationTitle(userId, ad.getPosition(), ad.getType()));
            }
        }

        if (existing != null) {
            if (!"rejected".equals(existing.getStatus()) && !"expired".equals(existing.getStatus())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "当前申请状态不支持修改，仅审核拒绝或已过期后可重新提交");
            }
            ad.setId(existing.getId());
            ad.setAdvertiserId(userId);
            ad.setStatus("pending");
            ad.setClickCount(existing.getClickCount() != null ? existing.getClickCount() : 0);
            if (ad.getWeight() == null) {
                ad.setWeight(existing.getWeight() != null ? existing.getWeight() : 1);
            }
            advertisementMapper.updateById(ad);
            log.info("广告申请更新: userId={}, adId={}, position={}", userId, existing.getId(), ad.getPosition());
            return advertisementMapper.selectById(existing.getId());
        }

        ad.setAdvertiserId(userId);
        ad.setStatus("pending");
        ad.setClickCount(0);
        if (ad.getWeight() == null) ad.setWeight(1);
        advertisementMapper.insert(ad);
        log.info("广告申请提交: userId={}, adId={}, position={}", userId, ad.getId(), ad.getPosition());
        return ad;
    }

    private String buildApplicationTitle(Long userId, String position, String type) {
        String safePosition = StrUtil.blankToDefault(position, "unknown");
        String safeType = StrUtil.blankToDefault(type, "image");
        return String.format("用户%s-%s-%s", userId, safePosition, safeType);
    }

    private void validateApplicationInput(Advertisement ad) {
        if (ad == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }

        String type = StrUtil.blankToDefault(ad.getType(), "").trim();
        if (!"image".equals(type) && !"code".equals(type)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "广告类型不合法");
        }

        String content = StrUtil.blankToDefault(ad.getContent(), "").trim();
        if (content.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "广告内容不能为空");
        }
        if ("image".equals(type)) {
            if (content.length() > MAX_APPLICATION_IMAGE_URL_LENGTH) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "广告图片地址长度不能超过" + MAX_APPLICATION_IMAGE_URL_LENGTH + "个字符");
            }
            if (!isSafeMediaUrl(content)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "广告图片地址不合法");
            }
        } else {
            if (content.length() > MAX_APPLICATION_CODE_LENGTH) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "代码广告内容长度不能超过" + MAX_APPLICATION_CODE_LENGTH + "个字符");
            }
            if (containsDangerousCodeAdTags(content)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "代码广告包含高风险标签，请移除后重试");
            }
        }

        if (StrUtil.isNotBlank(ad.getLinkUrl()) && ad.getLinkUrl().trim().length() > MAX_APPLICATION_LINK_URL_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接长度不能超过" + MAX_APPLICATION_LINK_URL_LENGTH + "个字符");
        }
        if (StrUtil.isNotBlank(ad.getAdInfo()) && ad.getAdInfo().trim().length() > MAX_APPLICATION_AD_INFO_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "广告信息长度不能超过" + MAX_APPLICATION_AD_INFO_LENGTH + "个字符");
        }
        if (StrUtil.isNotBlank(ad.getMimicContent()) && ad.getMimicContent().trim().length() > MAX_APPLICATION_MIMIC_CONTENT_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "拟态文案长度不能超过" + MAX_APPLICATION_MIMIC_CONTENT_LENGTH + "个字符");
        }

        validateSensitiveWord("广告信息", ad.getAdInfo());
        validateSensitiveWord("拟态文案", ad.getMimicContent());
    }

    private boolean isSafeMediaUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }
        String trimmed = rawUrl.trim();
        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            return true;
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (Exception e) {
            return false;
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            return false;
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            return false;
        }
        return uri.getHost() != null && !uri.getHost().isBlank();
    }

    private boolean containsDangerousCodeAdTags(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return content.matches("(?is).*</?(iframe|object|embed|form|input|button|textarea|select)\\b.*");
    }

    private void validateSensitiveWord(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sensitiveWordService.containsSensitiveWord(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, fieldName + "包含敏感词，请修改后提交");
        }
    }

    /**
     * 提交广告申请并独占绑定坑位
     */
    @Transactional(rollbackFor = Exception.class)
    public Advertisement submitApplicationWithPit(Long userId,
                                                  Advertisement ad,
                                                  Long pitAdId,
                                                  Integer pitIndex,
                                                  Set<Long> activePitAdIds) {
        Advertisement application = submitApplication(userId, ad);
        cleanupInvalidPitBindings(activePitAdIds);
        adPitBindingService.bindExclusive(application.getId(), pitAdId, pitIndex);
        return application;
    }

    /**
     * 查询我的广告申请（按位置）
     */
    public Advertisement getMyApplication(Long userId, String position) {
        LambdaQueryWrapper<Advertisement> wrapper = new LambdaQueryWrapper<Advertisement>()
                .eq(Advertisement::getAdvertiserId, userId)
                .orderByDesc(Advertisement::getCreateTime)
                .last("LIMIT 1");

        String normalizedPosition = normalizePosition(position);
        if (normalizedPosition != null && !normalizedPosition.isBlank()) {
            wrapper.eq(Advertisement::getPosition, normalizedPosition);
        }

        return advertisementMapper.selectOne(wrapper);
    }

    private void cleanupInvalidPitBindings(Set<Long> activePitAdIds) {
        List<AdPitBinding> bindings = adPitBindingService.listAll();
        if (bindings.isEmpty()) {
            return;
        }

        Set<Long> validActivePitIds = activePitAdIds == null ? Set.of() : activePitAdIds;
        Set<Long> invalidPitIds = new LinkedHashSet<>();
        Set<Long> candidateApplyIds = new LinkedHashSet<>();

        for (AdPitBinding binding : bindings) {
            if (binding == null) {
                continue;
            }
            Long applyAdId = binding.getApplyAdId();
            Long pitAdId = binding.getPitAdId();
            if (applyAdId == null || applyAdId <= 0 || pitAdId == null || pitAdId <= 0) {
                if (applyAdId != null && applyAdId > 0) {
                    candidateApplyIds.add(applyAdId);
                }
                if (pitAdId != null && pitAdId > 0) {
                    invalidPitIds.add(pitAdId);
                }
                continue;
            }

            if (!validActivePitIds.contains(pitAdId)) {
                invalidPitIds.add(pitAdId);
            }
            candidateApplyIds.add(applyAdId);
        }

        if (!invalidPitIds.isEmpty()) {
            adPitBindingService.removeByPitAdIds(new ArrayList<>(invalidPitIds));
        }

        if (candidateApplyIds.isEmpty()) {
            return;
        }

        List<Advertisement> applications = listByIds(new ArrayList<>(candidateApplyIds));
        Map<Long, Advertisement> applicationMap = new HashMap<>();
        for (Advertisement application : applications) {
            if (application == null || application.getId() == null) {
                continue;
            }
            applicationMap.put(application.getId(), application);
        }

        List<Long> staleApplyIds = new ArrayList<>();
        for (Long applyAdId : candidateApplyIds) {
            Advertisement application = applicationMap.get(applyAdId);
            if (application == null
                    || application.getAdvertiserId() == null
                    || !PIT_OCCUPIED_STATUSES.contains(application.getStatus())) {
                staleApplyIds.add(applyAdId);
            }
        }

        if (!staleApplyIds.isEmpty()) {
            adPitBindingService.removeByApplyAdIds(staleApplyIds);
        }
    }

    private void sanitizeAdvertisementFields(Advertisement ad, String effectiveType, boolean sanitizeLinkAlways) {
        if (ad.getTitle() != null) {
            ad.setTitle(XssUtil.cleanText(ad.getTitle()));
        }
        if (ad.getType() != null) {
            ad.setType(XssUtil.cleanText(ad.getType()));
        }
        if (ad.getAdInfo() != null) {
            ad.setAdInfo(XssUtil.cleanText(ad.getAdInfo()));
        }
        if (ad.getMimicContent() != null) {
            ad.setMimicContent(XssUtil.cleanText(ad.getMimicContent()));
        }
        if (ad.getPosition() != null) {
            ad.setPosition(normalizePosition(XssUtil.cleanText(ad.getPosition())));
        }
        if ("code".equals(effectiveType) && ad.getContent() != null) {
            ad.setContent(XssUtil.cleanMarkdown(ad.getContent()));
        }

        if (sanitizeLinkAlways || ad.getLinkUrl() != null) {
            ad.setLinkUrl(validateAndCleanLinkUrl(ad.getLinkUrl()));
        }
    }

    private void normalizeDisplaySettings(Advertisement ad) {
        if (ad.getPosition() != null) {
            ad.setPosition(normalizePosition(ad.getPosition()));
        }

        if (isCarouselPosition(ad.getPosition())) {
            ad.setAutoRotate(true);
            ad.setClosable(true);
            if (ad.getRotateIntervalSec() == null || ad.getRotateIntervalSec() < 2) {
                ad.setRotateIntervalSec(DEFAULT_ROTATE_INTERVAL_SEC);
            }
        } else if (Boolean.TRUE.equals(ad.getAutoRotate())) {
            if (ad.getRotateIntervalSec() == null || ad.getRotateIntervalSec() < 2) {
                ad.setRotateIntervalSec(DEFAULT_ROTATE_INTERVAL_SEC);
            }
        } else if (ad.getAutoRotate() == null) {
            ad.setAutoRotate(false);
        }

        if (!isCarouselPosition(ad.getPosition()) && ad.getClosable() == null) {
            ad.setClosable(false);
        }

        if (ad.getInsertAfter() != null && ad.getInsertAfter() < 1) {
            ad.setInsertAfter(1);
        }
        if (ad.getInsertAfter() != null && ad.getInsertAfter() > MIMIC_POSTS_PER_PAGE) {
            ad.setInsertAfter(MIMIC_POSTS_PER_PAGE);
        }
    }

    private void validateSchedule(Advertisement ad) {
        if (ad.getStartTime() != null && ad.getEndTime() != null && ad.getEndTime().isBefore(ad.getStartTime())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "结束时间不能早于开始时间");
        }
    }

    private String normalizePosition(String rawPosition) {
        if (rawPosition == null || rawPosition.isBlank()) {
            return null;
        }
        return POSITION_ALIAS_MAP.getOrDefault(rawPosition.trim(), rawPosition.trim());
    }

    private boolean isCarouselPosition(String position) {
        return position != null && CAROUSEL_POSITIONS.contains(position);
    }

    private List<String> buildCandidatePositions(String normalizedSlot) {
        List<String> candidates = new ArrayList<>();
        candidates.add(normalizedSlot);

        Map<String, String> reverseAlias = new HashMap<>();
        reverseAlias.put("home_top", "top");
        reverseAlias.put("home_left", "sidebar");
        reverseAlias.put("post_inline", "middle");
        reverseAlias.put("post_bottom", "bottom");

        String legacy = reverseAlias.get(normalizedSlot);
        if (legacy != null) {
            candidates.add(legacy);
        }
        return candidates;
    }

    private List<Advertisement> applySlotDisplayLimit(String normalizedSlot, List<Advertisement> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        int limit = records.size();
        if (CAROUSEL_POSITIONS.contains(normalizedSlot)) {
            limit = Math.min(records.size(), MAX_SLOT_AD_COUNT);
        } else if ("post_list_card".equals(normalizedSlot)) {
            int capacity = getMimicSlotCapacity();
            if (capacity <= 0) {
                return List.of();
            }
            limit = Math.min(records.size(), capacity);
        }

        if (limit >= records.size()) {
            return records;
        }
        return new ArrayList<>(records.subList(0, limit));
    }

    private String validateAndCleanLinkUrl(String linkUrl) {
        if (linkUrl == null || linkUrl.isBlank()) {
            return null;
        }

        String trimmed = linkUrl.trim();
        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            return XssUtil.cleanText(trimmed);
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接格式不正确");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接仅支持 http/https 协议");
        }

        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接不允许包含用户信息");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接缺少有效主机名");
        }

        String normalizedHost;
        try {
            normalizedHost = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接主机名无效");
        }

        if (!isPublicHost(normalizedHost)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不允许使用本地或内网跳转链接");
        }

        return XssUtil.cleanText(uri.toString());
    }

    private boolean isPublicHost(String host) {
        if ("localhost".equals(host) || host.endsWith(".localhost") || host.endsWith(".local")) {
            return false;
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }

            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    return false;
                }

                if (address instanceof Inet6Address inet6Address) {
                    byte first = inet6Address.getAddress()[0];
                    if ((first & (byte) 0xFE) == (byte) 0xFC) {
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ========== 回收站方法 ==========

    /**
     * 分页查询已删除广告（回收站）
     */
    public IPage<Advertisement> pageDeleted(int pageNum, int pageSize, String keyword) {
        Page<Advertisement> page = new Page<>(pageNum, pageSize);
        return advertisementMapper.selectDeletedPage(page, StrUtil.isBlank(keyword) ? null : keyword);
    }

    public List<Long> listDeletedIds() {
        return advertisementMapper.selectDeletedIds();
    }

    /**
     * 批量恢复广告（状态设为 expired）
     */
    @Transactional
    public int batchRestore(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            count += advertisementMapper.restoreById(id);
        }
        return count;
    }

    /**
     * 批量永久删除广告
     */
    @Transactional
    public int batchPermanentDelete(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            count += advertisementMapper.permanentDeleteById(id);
        }
        return count;
    }

    /**
     * 清空回收站
     */
    @Transactional
    public int clearTrash() {
        List<Long> ids = advertisementMapper.selectDeletedIds();
        if (ids.isEmpty()) return 0;
        return batchPermanentDelete(ids);
    }
}
