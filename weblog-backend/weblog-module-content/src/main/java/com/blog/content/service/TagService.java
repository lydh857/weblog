package com.blog.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.entity.Tag;
import com.blog.content.mapper.TagMapper;
import com.blog.content.util.SlugUtil;
import com.blog.infra.security.util.XssUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 标签服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tagMapper;

    /**
     * 查询所有标签
     * 缓存 1 小时
     */
    @Cacheable(value = "tag:list", key = "'all'")
    public List<Tag> listAll() {
        return tagMapper.selectList(
                new LambdaQueryWrapper<Tag>().orderByDesc(Tag::getCreateTime));
    }

    /**
     * 根据 slug 获取标签
     * 缓存 1 小时
     */
    @Cacheable(value = "tag:slug", key = "#slug")
    public Tag getBySlug(String slug) {
        Tag tag = tagMapper.selectOne(
                new LambdaQueryWrapper<Tag>().eq(Tag::getSlug, slug).last("LIMIT 1"));
        if (tag == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "标签不存在");
        }
        return tag;
    }

    /**
     * 获取标签详情
     * 缓存 1 小时
     */
    @Cacheable(value = "tag:detail", key = "#id")
    public Tag getById(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "标签不存在");
        }
        return tag;
    }

    /**
     * 创建标签
     * 清除所有标签缓存
     */
    @Transactional
    @CacheEvict(value = {"tag:list", "tag:slug", "tag:detail"}, allEntries = true)
    public Tag create(String name, String slug) {
        name = XssUtil.cleanText(name);
        checkNameUnique(name, null);

        Tag tag = new Tag();
        tag.setName(name);
        tag.setSlug(slug != null && !slug.isBlank() ? SlugUtil.sanitize(slug) : SlugUtil.generate(name));
        tagMapper.insert(tag);
        log.info("标签创建成功: id={}, name={}, slug={}", tag.getId(), tag.getName(), tag.getSlug());
        return tag;
    }

    /**
     * 更新标签
     * 清除所有标签缓存
     */
    @Transactional
    @CacheEvict(value = {"tag:list", "tag:slug", "tag:detail"}, allEntries = true)
    public Tag update(Long id, String name, String slug) {
        Tag tag = getById(id);
        name = XssUtil.cleanText(name);
        checkNameUnique(name, id);

        tag.setName(name);
        if (slug != null && !slug.isBlank()) {
            tag.setSlug(SlugUtil.sanitize(slug));
        }
        tagMapper.updateById(tag);
        log.info("标签更新成功: id={}", id);
        return tag;
    }

    /**
     * 删除标签
     * 清除所有标签缓存
     */
    @Transactional
    @CacheEvict(value = {"tag:list", "tag:slug", "tag:detail"}, allEntries = true)
    public void delete(Long id) {
        getById(id);
        tagMapper.deleteById(id);
        log.info("标签删除成功: id={}", id);
    }

    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Tag::getName, name);
        if (excludeId != null) {
            wrapper.ne(Tag::getId, excludeId);
        }
        if (tagMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.NAME_EXISTS, "标签名称已存在");
        }
    }

}
