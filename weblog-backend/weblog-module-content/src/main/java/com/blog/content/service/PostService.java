package com.blog.content.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.infra.security.util.XssUtil;
import com.blog.content.dto.PostAutoSaveRequest;
import com.blog.content.dto.PostCreateRequest;
import com.blog.content.dto.PostSeoRequest;
import com.blog.content.dto.PostVO;
import com.blog.content.entity.*;
import com.blog.content.mapper.*;
import com.blog.content.util.SlugUtil;
import com.blog.infra.oss.CdnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文章服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;
    private final PostContentMapper postContentMapper;
    private final PostTagMapper postTagMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final PostIndexService postIndexService;
    private final TagService tagService;
    private final CategoryService categoryService;
    private final AiRecommendService aiRecommendService;
    private final PostEmbeddingMapper postEmbeddingMapper;
    private final StringRedisTemplate redisTemplate;

    /** CDN 刷新服务（OSS 未启用时为 null） */
    @Autowired(required = false)
    private CdnService cdnService;

    /** OSS 服务（用于临时图片迁移） */
    @Autowired(required = false)
    private com.blog.infra.oss.OssService ossService;

    /** 本地文件服务（OSS 未启用时的 fallback） */
    @Autowired(required = false)
    private com.blog.infra.oss.LocalFileService localFileService;

    /**
     * 创建文章
     */
    @Transactional
    public PostVO create(PostCreateRequest req, Long authorId) {
        Post post = new Post();
        post.setTitle(XssUtil.cleanText(req.getTitle()));
        String slug = req.getSlug() != null && !req.getSlug().isBlank()
                ? SlugUtil.sanitize(req.getSlug()) : SlugUtil.generate(req.getTitle());
        // 确保 slug 唯一（包含已软删除记录，绕过 @TableLogic 过滤）
        String baseSlug = slug;
        int suffix = 1;
        while (postMapper.countBySlugIncludeDeleted(slug) > 0 && suffix <= 100) {
            slug = baseSlug + "-" + suffix++;
        }
        post.setSlug(slug);
        post.setSummary(req.getSummary() != null ? XssUtil.cleanText(req.getSummary()) : null);
        post.setCoverImage(migrateCoverImage(req.getCoverImage()));
        post.setCategoryId(req.getCategoryId());
        post.setSubCategoryId(req.getSubCategoryId());
        post.setAuthorId(authorId);
        post.setIsTop(req.getIsTop() != null && req.getIsTop());
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCollectCount(0);
        post.setCommentCount(0);

        // 处理发布状态
        handlePublishStatus(post, req);

        // SEO
        post.setSeoTitle(req.getSeoTitle() != null ? XssUtil.cleanText(req.getSeoTitle()) : null);
        post.setSeoDescription(req.getSeoDescription() != null ? XssUtil.cleanText(req.getSeoDescription()) : null);
        post.setSeoKeywords(req.getSeoKeywords() != null ? XssUtil.cleanText(req.getSeoKeywords()) : null);

        // 主题
        post.setPreviewTheme(req.getPreviewTheme());
        post.setCodeTheme(req.getCodeTheme());

        try {
            postMapper.insert(post);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // slug 并发冲突，追加 UUID 后缀重试
            String fallbackSlug = baseSlug + "-" + java.util.UUID.randomUUID().toString().substring(0, 6);
            post.setSlug(fallbackSlug);
            post.setId(null); // 重置 ID 让数据库重新生成
            postMapper.insert(post);
            log.warn("slug 冲突，已使用备用 slug: {} -> {}", slug, fallbackSlug);
        }

        // 迁移临时图片到正式目录，并做 Markdown 安全清理
        String migratedContent = migrateContentTempImages(XssUtil.cleanMarkdown(req.getContent()));

        // 保存内容
        PostContent content = new PostContent();
        content.setId(post.getId());
        content.setContent(migratedContent);
        postContentMapper.insert(content);

        // 处理新分类（发布时自动创建）
        handleNewCategory(req, post);

        // 处理新标签（发布时自动创建）并合并到 tagIds
        List<Long> allTagIds = mergeNewTags(req);

        // 保存标签关联
        savePostTags(post.getId(), allTagIds);

        log.info("文章创建成功: id={}, title={}", post.getId(), post.getTitle());

        // 索引文章（仅已发布的）
        if ("published".equals(post.getStatus())) {
            postIndexService.indexPost(post.getId(), post.getSlug(), post.getTitle(), migratedContent,
                    post.getSummary(), post.getCategoryId(), post.getAuthorId());
            try {
                aiRecommendService.generateEmbedding(post.getId(), migratedContent);
            } catch (Exception e) {
                log.warn("触发 Embedding 生成失败，不影响文章发布: {}", e.getMessage());
            }
        }

        return getById(post.getId());
    }

    /**
     * 更新文章
     */
    @Transactional
    @CacheEvict(value = "post:detail", key = "#req.slug")
    public PostVO update(Long id, PostCreateRequest req, Long operatorId) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        // 权限校验：只有作者可以编辑
        if (!post.getAuthorId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权编辑此文章");
        }

        post.setTitle(XssUtil.cleanText(req.getTitle()));
        if (req.getSlug() != null && !req.getSlug().isBlank()) {
            post.setSlug(SlugUtil.sanitize(req.getSlug()));
        }
        post.setSummary(req.getSummary() != null ? XssUtil.cleanText(req.getSummary()) : null);
        post.setCoverImage(migrateCoverImage(req.getCoverImage()));
        post.setCategoryId(req.getCategoryId());
        post.setSubCategoryId(req.getSubCategoryId());
        post.setIsTop(req.getIsTop() != null && req.getIsTop());

        handlePublishStatus(post, req);

        post.setSeoTitle(req.getSeoTitle() != null ? XssUtil.cleanText(req.getSeoTitle()) : null);
        post.setSeoDescription(req.getSeoDescription() != null ? XssUtil.cleanText(req.getSeoDescription()) : null);
        post.setSeoKeywords(req.getSeoKeywords() != null ? XssUtil.cleanText(req.getSeoKeywords()) : null);

        // 主题
        post.setPreviewTheme(req.getPreviewTheme());
        post.setCodeTheme(req.getCodeTheme());

        // 处理新分类（发布时自动创建）
        handleNewCategory(req, post);

        postMapper.updateById(post);

        // 更新内容
        PostContent content = postContentMapper.selectById(id);
        String migratedContent = migrateContentTempImages(XssUtil.cleanMarkdown(req.getContent()));
        if (content == null) {
            content = new PostContent();
            content.setId(id);
            content.setContent(migratedContent);
            postContentMapper.insert(content);
        } else {
            content.setContent(migratedContent);
            content.setHtmlContent(null); // 清除缓存的HTML
            postContentMapper.updateById(content);
        }

        // 更新标签
        postTagMapper.delete(new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, id));

        // 处理新标签并合并到 tagIds
        List<Long> allTagIds = mergeNewTags(req);
        savePostTags(id, allTagIds);

        log.info("文章更新成功: id={}", id);

        // 更新索引
        if ("published".equals(post.getStatus())) {
            postIndexService.indexPost(id, post.getSlug(), post.getTitle(), migratedContent,
                    post.getSummary(), post.getCategoryId(), post.getAuthorId());

            // 异步生成 Embedding（不阻塞文章更新）
            try {
                aiRecommendService.generateEmbedding(id, migratedContent);
            } catch (Exception e) {
                log.warn("触发 Embedding 生成失败，不影响文章更新: {}", e.getMessage());
            }
        } else {
            postIndexService.deleteIndex(id);
        }

        // 刷新 CDN 缓存
        if (cdnService != null && "published".equals(post.getStatus())) {
            cdnService.refreshPost(post.getSlug());
        }

        return getById(id);
    }

    /**
     * 自动保存文章（轻量级，只更新标题和内容）
     */
    @Transactional
    public void autoSave(Long id, PostAutoSaveRequest req, Long operatorId) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        if (!post.getAuthorId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权编辑此文章");
        }

        // 只更新标题
        if (req.getTitle() != null) {
            post.setTitle(XssUtil.cleanText(req.getTitle()));
            postMapper.updateById(post);
        }

        // 只更新内容
        if (req.getContent() != null) {
            String cleanedContent = XssUtil.cleanMarkdown(req.getContent());
            PostContent content = postContentMapper.selectById(id);
            if (content == null) {
                content = new PostContent();
                content.setId(id);
                content.setContent(cleanedContent);
                postContentMapper.insert(content);
            } else {
                content.setContent(cleanedContent);
                content.setHtmlContent(null);
                postContentMapper.updateById(content);
            }
        }

        log.debug("文章自动保存: id={}", id);
    }

    /**
     * 删除文章（软删除）
     */
    @Transactional
    public void delete(Long id, Long operatorId, boolean isAdmin) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        if (!isAdmin && !post.getAuthorId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除此文章");
        }
        postMapper.deleteById(id);
        postIndexService.deleteIndex(id);
        // 清理 Embedding 向量
        postEmbeddingMapper.delete(
            new LambdaQueryWrapper<PostEmbedding>().eq(PostEmbedding::getPostId, id));
        aiRecommendService.evictCache(id);
        // 刷新 CDN 缓存
        if (cdnService != null && "published".equals(post.getStatus())) {
            cdnService.refreshPost(post.getSlug());
        }
        log.info("文章删除成功: id={}, operator={}", id, operatorId);
    }

    /**
     * 获取文章详情
     */
    public PostVO getById(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        return toVO(post, true);
    }

    /**
     * 批量获取文章（不含正文，用于列表展示）
     */
    public List<PostVO> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Post> posts = postMapper.selectByIds(ids);
        Map<Long, Post> postMap = posts.stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        // 保持传入 ID 的顺序
        return ids.stream()
                .map(postMap::get)
                .filter(java.util.Objects::nonNull)
                .map(p -> toVO(p, false))
                .collect(Collectors.toList());
    }

    /**
     * 分页查询文章列表
     */
    public IPage<PostVO> page(int pageNum, int pageSize, Long categoryId, String status, Long authorId) {
        return page(pageNum, pageSize, categoryId, status, authorId, null);
    }

    public IPage<PostVO> page(int pageNum, int pageSize, Long categoryId, String status, Long authorId, String keyword) {
        return page(pageNum, pageSize, categoryId, status, authorId, keyword, null, null);
    }

    public IPage<PostVO> page(int pageNum, int pageSize, Long categoryId, String status, Long authorId, String keyword, Boolean isDisabled, Long tagId) {
        // 如果有 tagId 筛选，先查出关联的 postId 列表
        List<Long> postIds = null;
        if (tagId != null) {
            List<PostTag> pts = postTagMapper.selectList(
                    new LambdaQueryWrapper<PostTag>().eq(PostTag::getTagId, tagId));
            postIds = pts.stream().map(PostTag::getPostId).collect(Collectors.toList());
            if (postIds.isEmpty()) {
                Page<PostVO> empty = new Page<>(pageNum, pageSize, 0);
                empty.setRecords(Collections.emptyList());
                return empty;
            }
        }

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.and(w -> w.eq(Post::getCategoryId, categoryId)
                    .or().eq(Post::getSubCategoryId, categoryId));
        }
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(Post::getStatus, status);
        }
        if (authorId != null) {
            wrapper.eq(Post::getAuthorId, authorId);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(Post::getTitle, keyword);
        }
        if (isDisabled != null) {
            wrapper.eq(Post::getIsDisabled, isDisabled);
        }
        if (postIds != null) {
            wrapper.in(Post::getId, postIds);
        }
        wrapper.orderByDesc(Post::getIsTop).orderByDesc(Post::getCreateTime);

        IPage<Post> page = postMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return convertPageWithRelations(page);
    }

    /**
     * 批量转换文章列表（解决 N+1 查询问题）
     */
    private IPage<PostVO> convertPageWithRelations(IPage<Post> page) {
        if (page.getRecords().isEmpty()) {
            return page.convert(p -> toVO(p, false));
        }

        List<Post> posts = page.getRecords();
        Set<Long> categoryIds = new HashSet<>();
        Set<Long> postIds = new HashSet<>();

        for (Post p : posts) {
            if (p.getCategoryId() != null) categoryIds.add(p.getCategoryId());
            if (p.getSubCategoryId() != null) categoryIds.add(p.getSubCategoryId());
            postIds.add(p.getId());
        }

        Map<Long, Category> categoryMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            List<Category> categories = categoryMapper.selectByIds(categoryIds);
            for (Category c : categories) {
                categoryMap.put(c.getId(), c);
            }
        }

        Map<Long, List<Tag>> postTagsMap = new HashMap<>();
        if (!postIds.isEmpty()) {
            List<PostTag> postTags = postTagMapper.selectList(
                    new LambdaQueryWrapper<PostTag>().in(PostTag::getPostId, postIds));
            Set<Long> tagIds = postTags.stream().map(PostTag::getTagId).collect(Collectors.toSet());

            Map<Long, Tag> tagMap = new HashMap<>();
            if (!tagIds.isEmpty()) {
                List<Tag> tags = tagMapper.selectByIds(tagIds);
                for (Tag t : tags) {
                    tagMap.put(t.getId(), t);
                }
            }

            for (PostTag pt : postTags) {
                postTagsMap.computeIfAbsent(pt.getPostId(), k -> new ArrayList<>())
                        .add(tagMap.get(pt.getTagId()));
            }
        }

        return page.convert(p -> toVO(p, false, categoryMap, postTagsMap));
    }

    /**
     * 切换文章置顶状态
     */
    @Transactional
    public void toggleTop(Long id, Long operatorId) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        if (!post.getAuthorId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此文章");
        }
        post.setIsTop(!Boolean.TRUE.equals(post.getIsTop()));
        postMapper.updateById(post);
        log.info("文章置顶状态切换: id={}, isTop={}", id, post.getIsTop());
    }

    /**
     * 切换文章启用/禁用状态
     */
    @Transactional
    public void toggleDisabled(Long id, Long operatorId) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        if (!post.getAuthorId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此文章");
        }
        post.setIsDisabled(!Boolean.TRUE.equals(post.getIsDisabled()));
        postMapper.updateById(post);
        log.info("文章启用/禁用切换: id={}, isDisabled={}", id, post.getIsDisabled());
    }

    /**
     * 批量设置置顶状态
     */
    @Transactional
    public int batchSetTop(List<Long> ids, boolean isTop, Long operatorId) {
        int count = 0;
        for (Long id : ids) {
            Post post = postMapper.selectById(id);
            if (post == null || !post.getAuthorId().equals(operatorId)) continue;
            if (Boolean.TRUE.equals(post.getIsTop()) != isTop) {
                post.setIsTop(isTop);
                postMapper.updateById(post);
                count++;
            }
        }
        log.info("批量设置置顶: ids={}, isTop={}, 变更{}条", ids, isTop, count);
        return count;
    }

    /**
     * 批量设置禁用状态
     */
    @Transactional
    public int batchSetDisabled(List<Long> ids, boolean isDisabled, Long operatorId) {
        int count = 0;
        for (Long id : ids) {
            Post post = postMapper.selectById(id);
            if (post == null || !post.getAuthorId().equals(operatorId)) continue;
            if (Boolean.TRUE.equals(post.getIsDisabled()) != isDisabled) {
                post.setIsDisabled(isDisabled);
                postMapper.updateById(post);
                count++;
            }
        }
        log.info("批量设置禁用: ids={}, isDisabled={}, 变更{}条", ids, isDisabled, count);
        return count;
    }

    /**
     * 批量发布草稿
     */
    @Transactional
    public int batchPublish(List<Long> ids, Long operatorId) {
        int count = 0;
        for (Long id : ids) {
            Post post = postMapper.selectById(id);
            if (post == null) continue;
            if (!post.getAuthorId().equals(operatorId)) continue;
            if (!"draft".equals(post.getStatus()) && !"scheduled".equals(post.getStatus())) continue;
            post.setStatus("published");
            post.setIsPublished(true);
            post.setPublishType("immediate");
            post.setScheduledTime(null);
            postMapper.updateById(post);
            // 索引
            PostContent pc = postContentMapper.selectById(post.getId());
            String content = pc != null ? pc.getContent() : "";
            postIndexService.indexPost(post.getId(), post.getSlug(), post.getTitle(), content,
                    post.getSummary(), post.getCategoryId(), post.getAuthorId());
            count++;
        }
        log.info("批量发布: count={}, operator={}", count, operatorId);
        return count;
    }

    /**
     * 批量删除文章
     */
    @Transactional
    public int batchDelete(List<Long> ids, Long operatorId, boolean isAdmin) {
        int count = 0;
        for (Long id : ids) {
            Post post = postMapper.selectById(id);
            if (post == null) continue;
            if (!isAdmin && !post.getAuthorId().equals(operatorId)) continue;
            postMapper.deleteById(id);
            postIndexService.deleteIndex(id);
            postEmbeddingMapper.delete(
                new LambdaQueryWrapper<PostEmbedding>().eq(PostEmbedding::getPostId, id));
            aiRecommendService.evictCache(id);
            if (cdnService != null && "published".equals(post.getStatus())) {
                cdnService.refreshPost(post.getSlug());
            }
            count++;
        }
        log.info("批量删除文章: count={}, operator={}", count, operatorId);
        return count;
    }

    /**
     * 批量撤销定时发布（回到草稿箱）
     */
    @Transactional
    public int batchCancelSchedule(List<Long> ids, Long operatorId) {
        int count = 0;
        for (Long id : ids) {
            Post post = postMapper.selectById(id);
            if (post == null) continue;
            if (!post.getAuthorId().equals(operatorId)) continue;
            if (!"scheduled".equals(post.getStatus())) continue;
            post.setStatus("draft");
            post.setPublishType("immediate");
            post.setScheduledTime(null);
            post.setIsPublished(false);
            postMapper.updateById(post);
            count++;
        }
        log.info("批量撤销定时发布: count={}, operator={}", count, operatorId);
        return count;
    }

    /**
     * 批量定时发布（将草稿设为定时发布）
     */
    @Transactional
    public int batchSchedule(List<Long> ids, LocalDateTime baseTime, Integer intervalMinutes, Long operatorId) {
        if (baseTime == null || baseTime.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "定时发布时间必须在未来");
        }
        int count = 0;
        LocalDateTime currentTime = baseTime;
        for (Long id : ids) {
            Post post = postMapper.selectById(id);
            if (post == null) continue;
            if (!post.getAuthorId().equals(operatorId)) continue;
            if (!"draft".equals(post.getStatus()) && !"scheduled".equals(post.getStatus())) continue;
            post.setStatus("scheduled");
            post.setPublishType("scheduled");
            post.setScheduledTime(currentTime);
            post.setIsPublished(false);
            postMapper.updateById(post);
            count++;
            if (intervalMinutes != null && intervalMinutes > 0) {
                currentTime = currentTime.plusMinutes(intervalMinutes);
            }
        }
        log.info("批量定时发布: count={}, interval={}min, operator={}", count, intervalMinutes, operatorId);
        return count;
    }

    /**
     * 更新文章SEO设置
     */
    @Transactional
    public void updateSeo(Long id, PostSeoRequest req, Long operatorId) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        if (!post.getAuthorId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权编辑此文章");
        }
        post.setSeoTitle(req.getSeoTitle() != null ? XssUtil.cleanText(req.getSeoTitle()) : null);
        post.setSeoDescription(req.getSeoDescription() != null ? XssUtil.cleanText(req.getSeoDescription()) : null);
        post.setSeoKeywords(req.getSeoKeywords() != null ? XssUtil.cleanText(req.getSeoKeywords()) : null);
        postMapper.updateById(post);
        log.info("文章SEO更新: id={}", id);
    }

    /**
     * 发布到期的定时文章
     */
    @Transactional
    public int publishScheduledPosts() {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getStatus, "scheduled")
                .le(Post::getScheduledTime, LocalDateTime.now())
                .eq(Post::getIsPublished, false);

        List<Post> posts = postMapper.selectList(wrapper);
        for (Post post : posts) {
            post.setStatus("published");
            post.setIsPublished(true);
            post.setPublishType("scheduled");
            postMapper.updateById(post);
            // 索引新发布的文章
            PostContent pc = postContentMapper.selectById(post.getId());
            String content = pc != null ? pc.getContent() : "";
            postIndexService.indexPost(post.getId(), post.getSlug(), post.getTitle(), content,
                    post.getSummary(), post.getCategoryId(), post.getAuthorId());
            // 刷新 CDN 缓存
            if (cdnService != null) {
                cdnService.refreshPost(post.getSlug());
            }
            log.info("定时文章已发布: id={}, title={}", post.getId(), post.getTitle());
        }
        return posts.size();
    }

    /**
     * 全量重建 Lucene 索引
     */
    public int rebuildIndex() {
        postIndexService.deleteAll();
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getStatus, "published");
        List<Post> posts = postMapper.selectList(wrapper);
        for (Post post : posts) {
            PostContent pc = postContentMapper.selectById(post.getId());
            String content = pc != null ? pc.getContent() : "";
            postIndexService.indexPostBatch(post.getId(), post.getSlug(), post.getTitle(), content,
                    post.getSummary(), post.getCategoryId(), post.getAuthorId());
        }
        postIndexService.commitBatch();
        log.info("Lucene 索引重建完成，共索引 {} 篇文章", posts.size());
        return posts.size();
    }

    // ========== 用户端（Portal）方法 ==========

    /**
     * 用户端分页查询（仅已发布文章，支持分类/标签筛选）
     */
    public IPage<PostVO> portalPage(int pageNum, int pageSize, Long categoryId, Long tagId) {
        return portalPage(pageNum, pageSize, categoryId, tagId, null);
    }

    /**
     * 用户端分页查询（支持排序方式切换）
     */
    public IPage<PostVO> portalPage(int pageNum, int pageSize, Long categoryId, Long tagId, String sortBy) {
        // 如果有 tagId 筛选，先查出关联的 postId 列表
        List<Long> postIds = null;
        if (tagId != null) {
            List<PostTag> pts = postTagMapper.selectList(
                    new LambdaQueryWrapper<PostTag>().eq(PostTag::getTagId, tagId));
            postIds = pts.stream().map(PostTag::getPostId).collect(Collectors.toList());
            if (postIds.isEmpty()) {
                Page<PostVO> empty = new Page<>(pageNum, pageSize, 0);
                empty.setRecords(Collections.emptyList());
                return empty;
            }
        }

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getStatus, "published");
        if (categoryId != null) {
            wrapper.and(w -> w.eq(Post::getCategoryId, categoryId)
                    .or().eq(Post::getSubCategoryId, categoryId));
        }
        if (postIds != null) {
            wrapper.in(Post::getId, postIds);
        }
        // 根据排序方式选择排序字段
        if ("recommended".equals(sortBy)) {
            // 推荐排序：置顶优先 → 综合评分（浏览+点赞*3+收藏*5+评论*2）→ 时间
            wrapper.last("ORDER BY is_top DESC, (IFNULL(view_count,0) + IFNULL(like_count,0)*3 + IFNULL(collect_count,0)*5 + IFNULL(comment_count,0)*2) DESC, create_time DESC");
        } else if ("hottest".equals(sortBy)) {
            wrapper.orderByDesc(Post::getIsTop);
            wrapper.orderByDesc(Post::getViewCount);
        } else {
            wrapper.orderByDesc(Post::getIsTop);
            wrapper.orderByDesc(Post::getCreateTime);
        }

        IPage<Post> page = postMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return page.convert(p -> toVO(p, false));
    }

    /**
     * 查询今日发布的已上线文章
     * <p>时间范围：当天 00:00:00 ~ 23:59:59，按 createTime 降序排列</p>
     *
     * @param limit 最大返回条数，截断为 min(limit, 20)
     */
    public List<PostVO> listTodayPosts(int limit) {
        limit = Math.min(limit, 20);

        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(23, 59, 59);

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getStatus, "published")
               .between(Post::getCreateTime, start, end)
               .orderByDesc(Post::getCreateTime)
               .last("LIMIT " + limit);

        List<Post> posts = postMapper.selectList(wrapper);
        return posts.stream().map(p -> toVO(p, false)).toList();
    }

    /**
     * 最近发布文章列表（不限日期，按发布时间倒序）
     *
     * @param limit 最大返回条数，截断为 min(limit, 20)
     */
    public List<PostVO> listRecentPosts(int limit) {
        limit = Math.min(limit, 20);

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getStatus, "published")
               .orderByDesc(Post::getCreateTime)
               .last("LIMIT " + limit);

        List<Post> posts = postMapper.selectList(wrapper);
        return posts.stream().map(p -> toVO(p, false)).toList();
    }

    /**
     * 根据 slug 获取文章详情（用户端）
     * 缓存 1 小时
     */
    @Cacheable(value = "post:detail", key = "#slug", unless = "#result == null")
    public PostVO getBySlug(String slug) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getSlug, slug).eq(Post::getStatus, "published");
        Post post = postMapper.selectOne(wrapper);
        if (post == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        return toVO(post, true);
    }

    /**
     * 获取上一篇文章（比当前文章更早发布的最近一篇）
     */
    public PostVO getPrevPost(Long currentPostId) {
        Post current = postMapper.selectById(currentPostId);
        if (current == null) return null;

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getStatus, "published")
                .lt(Post::getCreateTime, current.getCreateTime())
                .orderByDesc(Post::getCreateTime)
                .last("LIMIT 1");
        Post prev = postMapper.selectOne(wrapper);
        if (prev == null) return null;
        PostVO vo = new PostVO();
        vo.setId(prev.getId());
        vo.setTitle(prev.getTitle());
        vo.setSlug(prev.getSlug());
        vo.setCoverImage(prev.getCoverImage());
        return vo;
    }

    /**
     * 获取下一篇文章（比当前文章更晚发布的最近一篇）
     */
    public PostVO getNextPost(Long currentPostId) {
        Post current = postMapper.selectById(currentPostId);
        if (current == null) return null;

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getStatus, "published")
                .gt(Post::getCreateTime, current.getCreateTime())
                .orderByAsc(Post::getCreateTime)
                .last("LIMIT 1");
        Post next = postMapper.selectOne(wrapper);
        if (next == null) return null;
        PostVO vo = new PostVO();
        vo.setId(next.getId());
        vo.setTitle(next.getTitle());
        vo.setSlug(next.getSlug());
        vo.setCoverImage(next.getCoverImage());
        return vo;
    }

    /**
     * 获取相似文章推荐：同标签 > 同子分类 > 同分类，按互动量排序
     */
    public List<PostVO> getSimilarPosts(Long postId, int limit) {
      Post current = postMapper.selectById(postId);
      if (current == null) return Collections.emptyList();

      // 当前文章的标签 ID
      List<PostTag> currentTags = postTagMapper.selectList(
        new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, postId));
      List<Long> tagIds = currentTags.stream().map(PostTag::getTagId).collect(Collectors.toList());

      Set<Long> candidateIds = new LinkedHashSet<>();

      // 1. 同标签文章（按共同标签数降序）
      if (!tagIds.isEmpty()) {
        List<PostTag> relatedTags = postTagMapper.selectList(
          new LambdaQueryWrapper<PostTag>().in(PostTag::getTagId, tagIds).ne(PostTag::getPostId, postId));
        // 统计每篇文章的共同标签数
        Map<Long, Long> tagCountMap = relatedTags.stream()
          .collect(Collectors.groupingBy(PostTag::getPostId, Collectors.counting()));
        // 按共同标签数降序排列
        tagCountMap.entrySet().stream()
          .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
          .map(Map.Entry::getKey)
          .forEach(candidateIds::add);
      }

      // 2. 同子分类文章
      if (candidateIds.size() < limit * 2 && current.getSubCategoryId() != null) {
        List<Post> subCatPosts = postMapper.selectList(
          new LambdaQueryWrapper<Post>()
            .eq(Post::getStatus, "published")
            .eq(Post::getSubCategoryId, current.getSubCategoryId())
            .ne(Post::getId, postId)
            .last("LIMIT " + (limit * 2)));
        subCatPosts.forEach(p -> candidateIds.add(p.getId()));
      }

      // 3. 同分类文章
      if (candidateIds.size() < limit * 2 && current.getCategoryId() != null) {
        List<Post> catPosts = postMapper.selectList(
          new LambdaQueryWrapper<Post>()
            .eq(Post::getStatus, "published")
            .eq(Post::getCategoryId, current.getCategoryId())
            .ne(Post::getId, postId)
            .last("LIMIT " + (limit * 2)));
        catPosts.forEach(p -> candidateIds.add(p.getId()));
      }

      if (candidateIds.isEmpty()) return Collections.emptyList();

      // 批量查询候选文章，按互动量排序
      List<Post> candidates = postMapper.selectList(
        new LambdaQueryWrapper<Post>()
          .in(Post::getId, candidateIds)
          .eq(Post::getStatus, "published"));

      return candidates.stream()
        .sorted(Comparator.comparingInt((Post p) ->
          (p.getLikeCount() != null ? p.getLikeCount() : 0)
          + (p.getCollectCount() != null ? p.getCollectCount() : 0)
          + (p.getCommentCount() != null ? p.getCommentCount() : 0)).reversed())
        .limit(limit)
        .map(p -> toVO(p, false))
        .collect(Collectors.toList());
    }


    // ========== 私有方法 ==========

    /**
     * 处理新分类创建：如果请求中包含新分类名称，创建分类并设置到 post 上
     */
    private void handleNewCategory(PostCreateRequest req, Post post) {
        if (req.getNewCategoryName() == null || req.getNewCategoryName().isBlank()) return;
        Long parentId = req.getNewCategoryParentId() != null ? req.getNewCategoryParentId() : 0L;
        Category newCat = categoryService.create(req.getNewCategoryName().trim(), null, null, parentId, null);
        if (parentId == 0L) {
            post.setCategoryId(newCat.getId());
        } else {
            post.setSubCategoryId(newCat.getId());
        }
    }

    /**
     * 合并已有标签ID和新标签名称，新标签会自动创建（去重）
     */
    private List<Long> mergeNewTags(PostCreateRequest req) {
        List<Long> allTagIds = new ArrayList<>();
        if (req.getTagIds() != null) {
            allTagIds.addAll(req.getTagIds());
        }
        if (req.getNewTagNames() != null && !req.getNewTagNames().isEmpty()) {
            for (String tagName : req.getNewTagNames()) {
                if (tagName == null || tagName.isBlank()) continue;
                String cleanName = tagName.trim();
                // 查找是否已存在同名标签
                Tag existing = tagMapper.selectOne(
                        new LambdaQueryWrapper<Tag>().eq(Tag::getName, cleanName).last("LIMIT 1"));
                if (existing != null) {
                    if (!allTagIds.contains(existing.getId())) {
                        allTagIds.add(existing.getId());
                    }
                } else {
                    Tag newTag = tagService.create(cleanName, null);
                    allTagIds.add(newTag.getId());
                }
            }
        }
        return allTagIds;
    }

    private void handlePublishStatus(Post post, PostCreateRequest req) {
        String status = req.getStatus() != null ? req.getStatus() : "draft";
        post.setStatus(status);

        if ("scheduled".equals(status)) {
            if (req.getScheduledTime() == null || req.getScheduledTime().isBefore(LocalDateTime.now())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "定时发布时间必须在未来");
            }
            post.setPublishType("scheduled");
            post.setScheduledTime(req.getScheduledTime());
            post.setIsPublished(false);
        } else if ("published".equals(status)) {
            post.setPublishType("immediate");
            post.setIsPublished(true);
            post.setScheduledTime(null);
        } else {
            post.setPublishType("immediate");
            post.setIsPublished(false);
            post.setScheduledTime(null);
        }
    }

    private void savePostTags(Long postId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return;
        for (Long tagId : tagIds) {
            PostTag pt = new PostTag();
            pt.setPostId(postId);
            pt.setTagId(tagId);
            postTagMapper.insert(pt);
        }
    }

    /**
     * 迁移文章内容中的临时图片到正式目录
     * 扫描 Markdown 内容中的图片 URL，将 temp/ 路径的图片迁移到 images/ 路径
     * @return 替换后的内容
     */
    private String migrateContentTempImages(String content) {
        if (content == null || content.isEmpty()) return content;
        boolean useOss = ossService != null;
        boolean useLocal = localFileService != null;
        if (!useOss && !useLocal) return content;
        // 匹配 Markdown 图片语法和 HTML img 标签中的 URL
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(!\\[[^]]*]\\()([^)]+temp/[^)]+)(\\))|(src=[\"'])([^\"']*temp/[^\"']*)(\\4)");
        // 简化：只匹配包含 temp/ 的 CDN URL
        String result = content;
        java.util.regex.Matcher matcher = pattern.matcher(content);
        Map<String, String> urlMap = new HashMap<>();
        while (matcher.find()) {
            String url = matcher.group(2) != null ? matcher.group(2) : matcher.group(5);
            if (url == null || urlMap.containsKey(url)) continue;
            boolean isTemp = useOss ? ossService.isTempUrl(url) : (useLocal && localFileService.isTempUrl(url));
            if (isTemp) {
                try {
                    String objectKey = useOss ? ossService.extractObjectKey(url) : localFileService.extractObjectKey(url);
                    String formalUrl = useOss ? ossService.moveToFormal(objectKey) : localFileService.moveToFormal(objectKey);
                    urlMap.put(url, formalUrl);
                } catch (Exception e) {
                    log.warn("迁移临时图片失败: {}", url, e);
                }
            }
        }
        for (Map.Entry<String, String> entry : urlMap.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * 迁移封面图（如果是临时 URL）
     */
    private String migrateCoverImage(String coverImage) {
        if (coverImage == null || coverImage.isEmpty()) return coverImage;
        boolean useOss = ossService != null;
        boolean useLocal = localFileService != null;
        if (!useOss && !useLocal) return coverImage;
        boolean isTemp = useOss ? ossService.isTempUrl(coverImage) : localFileService.isTempUrl(coverImage);
        if (!isTemp) return coverImage;
        try {
            String objectKey = useOss ? ossService.extractObjectKey(coverImage) : localFileService.extractObjectKey(coverImage);
            return useOss ? ossService.moveToFormal(objectKey) : localFileService.moveToFormal(objectKey);
        } catch (Exception e) {
            log.warn("迁移封面图失败: {}", coverImage, e);
            return coverImage;
        }
    }


    private PostVO toVO(Post post, boolean withContent) {
        return toVO(post, withContent, null, null);
    }

    private PostVO toVO(Post post, boolean withContent, Map<Long, Category> categoryMap, Map<Long, List<Tag>> postTagsMap) {
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setTitle(post.getTitle());
        vo.setSlug(post.getSlug());
        vo.setSummary(post.getSummary());
        vo.setCoverImage(post.getCoverImage());
        vo.setCategoryId(post.getCategoryId());
        vo.setSubCategoryId(post.getSubCategoryId());
        vo.setAuthorId(post.getAuthorId());
        vo.setStatus(post.getStatus());
        vo.setPublishType(post.getPublishType());
        vo.setScheduledTime(post.getScheduledTime());
        vo.setViewCount(post.getViewCount());
        vo.setLikeCount(getRedisCount("post:like:" + post.getId(), post.getLikeCount()));
        vo.setCollectCount(getRedisCount("post:collect:" + post.getId(), post.getCollectCount()));
        vo.setCommentCount(getRedisCount("post:comment:" + post.getId(), post.getCommentCount()));
        vo.setSeoTitle(post.getSeoTitle());
        vo.setSeoDescription(post.getSeoDescription());
        vo.setSeoKeywords(post.getSeoKeywords());
        vo.setPreviewTheme(post.getPreviewTheme());
        vo.setCodeTheme(post.getCodeTheme());
        vo.setIsTop(post.getIsTop());
        vo.setIsDisabled(post.getIsDisabled());
        vo.setCreateTime(post.getCreateTime());
        vo.setUpdateTime(post.getUpdateTime());

        // 分类名称（优先使用缓存 Map）
        if (categoryMap != null) {
            if (post.getCategoryId() != null) {
                Category cat = categoryMap.get(post.getCategoryId());
                if (cat != null) vo.setCategoryName(cat.getName());
            }
            if (post.getSubCategoryId() != null) {
                Category subCat = categoryMap.get(post.getSubCategoryId());
                if (subCat != null) vo.setSubCategoryName(subCat.getName());
            }
        } else {
            if (post.getCategoryId() != null) {
                Category cat = categoryMapper.selectById(post.getCategoryId());
                if (cat != null) vo.setCategoryName(cat.getName());
            }
            if (post.getSubCategoryId() != null) {
                Category subCat = categoryMapper.selectById(post.getSubCategoryId());
                if (subCat != null) vo.setSubCategoryName(subCat.getName());
            }
        }

        // 标签（优先使用缓存 Map）
        List<Tag> tags;
        if (postTagsMap != null) {
            tags = postTagsMap.getOrDefault(post.getId(), Collections.emptyList());
        } else {
            List<PostTag> postTags = postTagMapper.selectList(
                    new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, post.getId()));
            if (!postTags.isEmpty()) {
                List<Long> tagIds = postTags.stream().map(PostTag::getTagId).collect(Collectors.toList());
                tags = tagMapper.selectByIds(tagIds);
            } else {
                tags = Collections.emptyList();
            }
        }

        if (!tags.isEmpty() && !tags.contains(null)) {
            vo.setTags(tags.stream().filter(Objects::nonNull).map(t -> {
                PostVO.TagVO tv = new PostVO.TagVO();
                tv.setId(t.getId());
                tv.setName(t.getName());
                tv.setSlug(t.getSlug());
                tv.setColor(t.getColor());
                return tv;
            }).collect(Collectors.toList()));
        } else {
            vo.setTags(Collections.emptyList());
        }

        // 内容
        if (withContent) {
            PostContent pc = postContentMapper.selectById(post.getId());
            if (pc != null) {
                vo.setContent(pc.getContent());
                vo.setHtmlContent(pc.getHtmlContent());
            }
        }

        return vo;
    }

    /**
     * 从 Redis 读取计数，无值时用数据库值回填
     */
    private int getRedisCount(String key, Integer dbValue) {
        try {
            String val = redisTemplate.opsForValue().get(key);
            if (val != null) return Integer.parseInt(val);
            // Redis 无值，用 DB 值回填
            int count = dbValue != null ? dbValue : 0;
            redisTemplate.opsForValue().set(key, String.valueOf(count));
            return count;
        } catch (Exception e) {
            log.warn("读取 Redis 计数失败: key={}, {}", key, e.getMessage());
            return dbValue != null ? dbValue : 0;
        }
    }

    // ========== 回收站 ==========

    /**
     * 分页查询回收站（已软删除的文章）
     */
    public IPage<PostVO> pageDeleted(int pageNum, int pageSize, String keyword) {
        Page<Post> page = new Page<>(pageNum, pageSize);
        IPage<Post> result = postMapper.selectDeletedPage(page, StrUtil.isBlank(keyword) ? null : keyword);
        return result.convert(p -> toVO(p, false));
    }

    /**
     * 批量恢复文章到草稿箱
     */
    @Transactional
    public int batchRestore(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            count += postMapper.restoreById(id);
        }
        log.info("批量恢复文章: count={}", count);
        return count;
    }

    /**
     * 批量永久删除文章
     */
    @Transactional
    public int batchPermanentDelete(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            int deleted = postMapper.permanentDeleteById(id);
            if (deleted > 0) {
                postContentMapper.deleteById(id);
                postTagMapper.delete(new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, id));
                postEmbeddingMapper.delete(
                    new LambdaQueryWrapper<PostEmbedding>().eq(PostEmbedding::getPostId, id));
                aiRecommendService.evictCache(id);
                count++;
            }
        }
        log.info("批量永久删除文章: count={}", count);
        return count;
    }

    /**
     * 清空回收站
     */
    @Transactional
    public int clearTrash() {
        List<Long> ids = postMapper.selectDeletedIds();
        if (ids.isEmpty()) return 0;
        return batchPermanentDelete(ids);
    }
}
