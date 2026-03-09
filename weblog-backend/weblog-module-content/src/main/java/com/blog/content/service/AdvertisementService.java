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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
        ad.setAdvertiserId(userId);
        ad.setStatus("pending");
        ad.setClickCount(0);
        if (ad.getWeight() == null) ad.setWeight(1);
        advertisementMapper.insert(ad);
        log.info("广告申请提交: userId={}, adId={}", userId, ad.getId());
        return ad;
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
