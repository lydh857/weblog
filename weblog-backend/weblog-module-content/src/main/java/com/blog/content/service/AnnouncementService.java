package com.blog.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.content.entity.Announcement;
import com.blog.content.mapper.AnnouncementMapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 公告服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;
    private static final Set<String> VALID_ANNOUNCEMENT_TYPES = Set.of("popup", "banner");

    /**
     * 按类型获取有效公告（用户端）
     */
    public List<Announcement> getActiveByType(String type) {
        String normalizedType = normalizeType(type);
        validateType(normalizedType);

        LocalDateTime now = LocalDateTime.now();
        return announcementMapper.selectList(
                new LambdaQueryWrapper<Announcement>()
                        .eq(Announcement::getType, normalizedType)
                        .eq(Announcement::getStatus, "published")
                        .and(w -> w
                                .isNull(Announcement::getStartTime)
                                .or()
                                .le(Announcement::getStartTime, now))
                        .and(w -> w
                                .isNull(Announcement::getEndTime)
                                .or()
                                .ge(Announcement::getEndTime, now))
                        .orderByDesc(Announcement::getPriority));
    }

    /**
     * 获取所有有效公告（不限类型）
     */
    public List<Announcement> getAllActive() {
        LocalDateTime now = LocalDateTime.now();
        return announcementMapper.selectList(
                new LambdaQueryWrapper<Announcement>()
                        .eq(Announcement::getStatus, "published")
                        .and(w -> w
                                .isNull(Announcement::getStartTime)
                                .or()
                                .le(Announcement::getStartTime, now))
                        .and(w -> w
                                .isNull(Announcement::getEndTime)
                                .or()
                                .ge(Announcement::getEndTime, now))
                        .orderByDesc(Announcement::getPriority));
    }

    /**
     * 按 ID 获取已发布的公告（用户端）
     */
    public Announcement getActiveById(Long id) {
        Announcement ann = announcementMapper.selectById(id);
        if (ann == null || !"published".equals(ann.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "公告不存在");
        }
        return ann;
    }

    // ========== 管理端方法 ==========

    /**
     * 分页查询公告（管理端）
     */
    public IPage<Announcement> listPage(int pageNum, int pageSize, String status, String type) {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Announcement::getStatus, status);
        }
        if (type != null && !type.isEmpty()) {
            String normalizedType = normalizeType(type);
            validateType(normalizedType);
            wrapper.eq(Announcement::getType, normalizedType);
        }
        wrapper.orderByDesc(Announcement::getCreateTime);
        return announcementMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 创建公告
     */
    public Announcement create(Announcement ann) {
        if (ann.getType() == null || ann.getType().isBlank()) {
            ann.setType("banner");
        } else {
            String normalizedType = normalizeType(ann.getType());
            validateType(normalizedType);
            ann.setType(normalizedType);
        }

        if (ann.getStatus() == null) ann.setStatus("draft");
        if (ann.getPriority() == null) ann.setPriority(0);
        if (ann.getIsClosable() == null) ann.setIsClosable(true);
        announcementMapper.insert(ann);
        // 如果直接以 published 状态创建 popup，归档其他已发布的 popup
        if ("published".equals(ann.getStatus()) && "popup".equals(ann.getType())) {
            archiveOtherPopupsIfNeeded(ann.getId());
        }
        return ann;
    }

    /**
     * 更新公告
     */
    public void update(Long id, Announcement ann) {
        Announcement existing = announcementMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "公告不存在");
        }

        if (ann.getType() != null) {
            if (ann.getType().isBlank()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "公告类型不能为空，仅支持: popup, banner");
            }
            String normalizedType = normalizeType(ann.getType());
            validateType(normalizedType);
            ann.setType(normalizedType);
        }

        ann.setId(id);
        announcementMapper.updateById(ann);
        // 如果更新后是已发布的 popup，归档其他已发布的 popup
        if ("published".equals(ann.getStatus()) && "popup".equals(ann.getType())) {
            archiveOtherPopupsIfNeeded(id);
        }
    }

    /**
     * 更新公告状态
     * popup 类型发布时自动归档其他已发布的 popup
     */
    public void updateStatus(Long id, String status) {
        if ("published".equals(status)) {
            archiveOtherPopupsIfNeeded(id);
        }
        announcementMapper.update(null, new LambdaUpdateWrapper<Announcement>()
                .eq(Announcement::getId, id)
                .set(Announcement::getStatus, status));
    }

    /**
     * 删除公告
     */
    public void delete(Long id) {
        announcementMapper.deleteById(id);
    }

    /**
     * 批量删除公告
     */
    public void batchDelete(List<Long> ids) {
        announcementMapper.deleteByIds(ids);
    }

    /**
     * 批量更新公告状态
     * popup 类型发布时自动归档其他已发布的 popup
     */
    public void batchUpdateStatus(List<Long> ids, String status) {
        if ("published".equals(status)) {
            for (Long id : ids) {
                archiveOtherPopupsIfNeeded(id);
            }
        }
        announcementMapper.update(null, new LambdaUpdateWrapper<Announcement>()
                .in(Announcement::getId, ids)
                .set(Announcement::getStatus, status));
    }

    /**
     * 如果目标公告是 popup 类型，归档其他已发布的 popup 公告
     */
    private void archiveOtherPopupsIfNeeded(Long excludeId) {
        Announcement ann = announcementMapper.selectById(excludeId);
        if (ann == null || !"popup".equals(ann.getType())) return;
        announcementMapper.update(null, new LambdaUpdateWrapper<Announcement>()
                .eq(Announcement::getType, "popup")
                .eq(Announcement::getStatus, "published")
                .ne(Announcement::getId, excludeId)
                .set(Announcement::getStatus, "archived"));
    }

    private void validateType(String type) {
        if (!VALID_ANNOUNCEMENT_TYPES.contains(type)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的公告类型，仅支持: popup, banner");
        }
    }

    private String normalizeType(String type) {
        return type == null ? null : type.trim().toLowerCase();
    }
}
