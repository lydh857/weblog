package com.blog.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.dto.MediaStatsVO;
import com.blog.content.entity.OssResource;
import com.blog.content.mapper.OssResourceMapper;
import com.blog.infra.oss.LocalFileService;
import com.blog.infra.oss.OssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * OSS 资源管理服务
 * 负责记录上传文件到数据库，删除时同步删除 OSS 文件
 */
@Slf4j
@Service
public class OssResourceService {

    private final OssResourceMapper ossResourceMapper;
    private final OssService ossService;
    private final LocalFileService localFileService;

    public OssResourceService(OssResourceMapper ossResourceMapper,
                              @Autowired(required = false) OssService ossService,
                              @Autowired(required = false) LocalFileService localFileService) {
        this.ossResourceMapper = ossResourceMapper;
        this.ossService = ossService;
        this.localFileService = localFileService;
    }

    /**
     * 记录上传的资源
     */
    public OssResource record(String fileName, String filePath, Long fileSize,
                              String fileType, String mimeType, String url,
                              Long uploaderId, String usageType) {
        OssResource resource = new OssResource();
        resource.setFileName(fileName);
        resource.setFilePath(filePath);
        resource.setFileSize(fileSize);
        resource.setFileType(fileType);
        resource.setMimeType(mimeType);
        resource.setUrl(url);
        resource.setUploaderId(uploaderId);
        resource.setUsageType(usageType != null ? usageType : "other");
        ossResourceMapper.insert(resource);
        log.info("OSS 资源已记录: id={}, path={}", resource.getId(), filePath);
        return resource;
    }

    /**
     * 删除资源（同步删除 OSS 文件 + 数据库记录）
     */
    @Transactional
    public void delete(Long id, Long operatorId, boolean isAdmin) {
        OssResource resource = ossResourceMapper.selectById(id);
        if (resource == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        // 权限校验：只有上传者或管理员可删除
        if (!isAdmin && !resource.getUploaderId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除此资源");
        }
        // 先删存储文件（OSS 或本地）
        if (ossService != null) {
            try {
                ossService.delete(resource.getFilePath());
            } catch (Exception e) {
                log.warn("OSS 文件删除失败（可能已不存在）: path={}, error={}", resource.getFilePath(), e.getMessage());
            }
        } else if (localFileService != null) {
            try {
                String objectKey = localFileService.extractObjectKey(resource.getUrl());
                if (objectKey != null) {
                    localFileService.delete(objectKey);
                }
            } catch (Exception e) {
                log.warn("本地文件删除失败（可能已不存在）: path={}, error={}", resource.getFilePath(), e.getMessage());
            }
        }
        // 再删数据库记录（软删除）
        ossResourceMapper.deleteById(id);
        log.info("OSS 资源已删除: id={}, path={}, operator={}", id, resource.getFilePath(), operatorId);
    }

    /**
     * 根据 filePath 查找资源
     */
    public OssResource getByFilePath(String filePath) {
        return ossResourceMapper.selectOne(
                new LambdaQueryWrapper<OssResource>().eq(OssResource::getFilePath, filePath));
    }

    /**
     * 更新直传完成后的资源元信息
     */
    public void updateUploadedMetadata(String filePath,
                                       Long uploaderId,
                                       Long fileSize,
                                       String fileType,
                                       String mimeType,
                                       String url) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        LambdaUpdateWrapper<OssResource> wrapper = new LambdaUpdateWrapper<OssResource>()
                .eq(OssResource::getFilePath, filePath);
        if (uploaderId != null) {
            wrapper.eq(OssResource::getUploaderId, uploaderId);
        }
        wrapper.set(fileSize != null, OssResource::getFileSize, fileSize)
                .set(fileType != null && !fileType.isBlank(), OssResource::getFileType, fileType)
                .set(mimeType != null && !mimeType.isBlank(), OssResource::getMimeType, mimeType)
                .set(url != null && !url.isBlank(), OssResource::getUrl, url);
        ossResourceMapper.update(null, wrapper);
    }

    /**
     * 按 filePath 软删除资源记录
     */
    public void markDeletedByFilePath(String filePath, Long uploaderId) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        LambdaUpdateWrapper<OssResource> wrapper = new LambdaUpdateWrapper<OssResource>()
                .eq(OssResource::getFilePath, filePath)
                .set(OssResource::getIsDeleted, true);
        if (uploaderId != null) {
            wrapper.eq(OssResource::getUploaderId, uploaderId);
        }
        ossResourceMapper.update(null, wrapper);
    }

    /**
     * 分页查询资源列表
     */
    public IPage<OssResource> page(int pageNum, int pageSize, Long uploaderId, String usageType) {
        LambdaQueryWrapper<OssResource> wrapper = new LambdaQueryWrapper<>();
        if (uploaderId != null) {
            wrapper.eq(OssResource::getUploaderId, uploaderId);
        }
        if (usageType != null) {
            wrapper.eq(OssResource::getUsageType, usageType);
        }
        wrapper.orderByDesc(OssResource::getCreateTime);
        return ossResourceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 按引用状态分页查询资源
     * referenced: URL 在 referencedUrls 集合中
     * unreferenced: URL 不在 referencedUrls 集合中
     */
    public IPage<OssResource> pageByReferenceStatus(int pageNum, int pageSize,
                                                     Long uploaderId, String usageType,
                                                     String referenceStatus,
                                                     Set<String> referencedUrls) {
        LambdaQueryWrapper<OssResource> wrapper = new LambdaQueryWrapper<>();
        if (uploaderId != null) {
            wrapper.eq(OssResource::getUploaderId, uploaderId);
        }
        if (usageType != null) {
            wrapper.eq(OssResource::getUsageType, usageType);
        }

        if ("referenced".equals(referenceStatus)) {
            if (referencedUrls.isEmpty()) {
                // 没有任何引用 URL，直接返回空页
                return new Page<>(pageNum, pageSize, 0);
            }
            wrapper.in(OssResource::getUrl, referencedUrls);
        } else if ("unreferenced".equals(referenceStatus)) {
            if (!referencedUrls.isEmpty()) {
                wrapper.notIn(OssResource::getUrl, referencedUrls);
            }
        }

        wrapper.orderByDesc(OssResource::getCreateTime);
        return ossResourceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 查询媒体统计信息
     */
    public MediaStatsVO getStats(Long uploaderId) {
        LambdaQueryWrapper<OssResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(OssResource::getUsageType, OssResource::getFileSize);
        if (uploaderId != null) {
            wrapper.eq(OssResource::getUploaderId, uploaderId);
        }

        List<OssResource> resources = ossResourceMapper.selectList(wrapper);

        MediaStatsVO stats = new MediaStatsVO();
        if (resources == null || resources.isEmpty()) {
            return stats;
        }

        Map<String, MediaStatsVO.UsageTypeStat> usageTypeStatMap = new HashMap<>();
        long totalSize = 0L;

        for (OssResource resource : resources) {
            long fileSize = resource.getFileSize() == null ? 0L : resource.getFileSize();
            totalSize += fileSize;

            String usageType = resource.getUsageType();
            if (usageType == null || usageType.isBlank()) {
                usageType = "other";
            }

            MediaStatsVO.UsageTypeStat usageTypeStat = usageTypeStatMap.computeIfAbsent(usageType, key -> {
                MediaStatsVO.UsageTypeStat created = new MediaStatsVO.UsageTypeStat();
                created.setUsageType(key);
                created.setFileCount(0L);
                created.setTotalSize(0L);
                return created;
            });
            usageTypeStat.setFileCount(usageTypeStat.getFileCount() + 1);
            usageTypeStat.setTotalSize(usageTypeStat.getTotalSize() + fileSize);
        }

        List<MediaStatsVO.UsageTypeStat> usageTypeStats = new ArrayList<>(usageTypeStatMap.values());
        usageTypeStats.sort(Comparator.comparingLong(MediaStatsVO.UsageTypeStat::getTotalSize).reversed());

        stats.setTotalCount(resources.size());
        stats.setTotalSize(totalSize);
        stats.setUsageTypeStats(usageTypeStats);
        return stats;
    }

    /**
     * 根据 ID 获取资源
     */
    public OssResource getById(Long id) {
        return ossResourceMapper.selectById(id);
    }


    /**
     * 批量删除资源（同步删除存储文件 + 数据库记录）
     * 兼容 OSS 和本地文件存储模式，单个文件删除失败时记录日志并继续
     *
     * @param ids 资源 ID 列表
     * @return 成功删除的数量
     */
    public int batchDeleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int deletedCount = 0;
        for (Long id : ids) {
            try {
                OssResource resource = ossResourceMapper.selectById(id);
                if (resource == null) {
                    continue;
                }
                // 删除存储文件（OSS 或本地）
                deleteStorageFile(resource);
                // 删除数据库记录
                ossResourceMapper.deleteById(id);
                deletedCount++;
            } catch (Exception e) {
                log.warn("批量删除资源失败，跳过: id={}, error={}", id, e.getMessage());
            }
        }
        log.info("批量删除完成: 请求={}, 成功={}", ids.size(), deletedCount);
        return deletedCount;
    }

    /**
     * 删除存储文件，兼容 OSS 和本地文件模式
     */
    private void deleteStorageFile(OssResource resource) {
        if (ossService != null) {
            try {
                ossService.delete(resource.getFilePath());
            } catch (Exception e) {
                log.warn("OSS 文件删除失败: path={}, error={}", resource.getFilePath(), e.getMessage());
            }
        } else if (localFileService != null) {
            try {
                String objectKey = localFileService.extractObjectKey(resource.getUrl());
                if (objectKey != null) {
                    localFileService.delete(objectKey);
                }
            } catch (Exception e) {
                log.warn("本地文件删除失败: path={}, error={}", resource.getFilePath(), e.getMessage());
            }
        }
    }

}
