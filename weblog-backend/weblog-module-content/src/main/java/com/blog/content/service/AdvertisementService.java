package com.blog.content.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.content.entity.Advertisement;
import com.blog.content.mapper.AdvertisementMapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
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
import java.util.List;
import java.util.Locale;

/**
 * 广告服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvertisementService {

    private final AdvertisementMapper advertisementMapper;

    /**
     * 按位置获取有效广告（用户端）
     */
    public List<Advertisement> getActiveByPosition(String position) {
        LocalDateTime now = LocalDateTime.now();
        return advertisementMapper.selectList(
                new LambdaQueryWrapper<Advertisement>()
                        .eq(Advertisement::getPosition, position)
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
            wrapper.eq(Advertisement::getPosition, position);
        }
        wrapper.orderByDesc(Advertisement::getCreateTime);
        return advertisementMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 创建广告
     */
    public Advertisement create(Advertisement ad) {
        sanitizeAdvertisementFields(ad, ad.getType(), true);
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

        ad.setId(id);
        advertisementMapper.updateById(ad);
    }

    /**
     * 审核广告（approved/rejected）
     */
    public void updateStatus(Long id, String status) {
        advertisementMapper.update(null, new LambdaUpdateWrapper<Advertisement>()
                .eq(Advertisement::getId, id)
                .set(Advertisement::getStatus, status));
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
        advertisementMapper.update(null, new LambdaUpdateWrapper<Advertisement>()
                .in(Advertisement::getId, ids)
                .set(Advertisement::getStatus, status));
    }

    /**
     * 用户提交广告申请
     */
    public Advertisement submitApplication(Long userId, Advertisement ad) {
        sanitizeAdvertisementFields(ad, ad.getType(), true);
        ad.setAdvertiserId(userId);
        ad.setStatus("pending");
        ad.setClickCount(0);
        if (ad.getWeight() == null) ad.setWeight(1);
        advertisementMapper.insert(ad);
        log.info("广告申请提交: userId={}, adId={}", userId, ad.getId());
        return ad;
    }

    private void sanitizeAdvertisementFields(Advertisement ad, String effectiveType, boolean sanitizeLinkAlways) {
        if (ad.getTitle() != null) {
            ad.setTitle(XssUtil.cleanText(ad.getTitle()));
        }
        if (ad.getType() != null) {
            ad.setType(XssUtil.cleanText(ad.getType()));
        }
        if (ad.getPosition() != null) {
            ad.setPosition(XssUtil.cleanText(ad.getPosition()));
        }

        if ("code".equals(effectiveType) && ad.getContent() != null) {
            ad.setContent(XssUtil.cleanContent(ad.getContent()));
        }

        if (sanitizeLinkAlways || ad.getLinkUrl() != null) {
            ad.setLinkUrl(validateAndCleanLinkUrl(ad.getLinkUrl()));
        }
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
