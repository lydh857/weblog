package com.blog.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.entity.Category;
import com.blog.content.mapper.CategoryMapper;
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
 * 分类服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    /**
     * 查询所有分类（树形结构）
     * 缓存 1 小时
     */
    @Cacheable(value = "category:list", key = "'all'")
    public List<Category> listAll() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Category::getSortOrder).orderByAsc(Category::getId);
        return categoryMapper.selectList(wrapper);
    }

    /**
     * 查询顶级分类
     * 缓存 1 小时
     */
    @Cacheable(value = "category:topLevel", key = "'top'")
    public List<Category> listTopLevel() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getParentId, 0L)
                .orderByAsc(Category::getSortOrder);
        return categoryMapper.selectList(wrapper);
    }

    /**
     * 查询子分类
     * 缓存 1 小时
     */
    @Cacheable(value = "category:children", key = "#parentId")
    public List<Category> listByParentId(Long parentId) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getParentId, parentId)
                .orderByAsc(Category::getSortOrder);
        return categoryMapper.selectList(wrapper);
    }

    /**
     * 根据 slug 获取分类
     * 缓存 1 小时
     */
    @Cacheable(value = "category:slug", key = "#slug")
    public Category getBySlug(String slug) {
        Category cat = categoryMapper.selectOne(
                new LambdaQueryWrapper<Category>().eq(Category::getSlug, slug).last("LIMIT 1"));
        if (cat == null) {
            throw new BusinessException(ResultCode.CATEGORY_NOT_FOUND);
        }
        return cat;
    }

    /**
     * 获取分类详情
     * 缓存 1 小时
     */
    @Cacheable(value = "category:detail", key = "#id")
    public Category getById(Long id) {
        Category cat = categoryMapper.selectById(id);
        if (cat == null) {
            throw new BusinessException(ResultCode.CATEGORY_NOT_FOUND);
        }
        return cat;
    }

    /**
     * 创建分类
     * 清除所有分类缓存
     */
    @Transactional
    @CacheEvict(value = {"category:list", "category:topLevel", "category:children"}, allEntries = true)
    public Category create(String name, String slug, String description, Long parentId, Integer sortOrder) {
        name = XssUtil.cleanText(name);
        checkNameUnique(name, parentId, null);

        Category cat = new Category();
        cat.setName(name);
        cat.setSlug(slug != null && !slug.isBlank() ? SlugUtil.sanitize(slug) : SlugUtil.generate(name));
        cat.setDescription(description != null ? XssUtil.cleanText(description) : null);
        cat.setParentId(parentId != null ? parentId : 0L);
        cat.setSortOrder(sortOrder != null ? sortOrder : 0);
        categoryMapper.insert(cat);
        log.info("分类创建成功: id={}, name={}, slug={}", cat.getId(), cat.getName(), cat.getSlug());
        return cat;
    }

    /**
     * 更新分类
     * 清除所有分类缓存
     */
    @Transactional
    @CacheEvict(value = {"category:list", "category:topLevel", "category:children", "category:slug", "category:detail"}, allEntries = true)
    public Category update(Long id, String name, String slug, String description, Long parentId, Integer sortOrder) {
        Category cat = getById(id);
        name = XssUtil.cleanText(name);
        checkNameUnique(name, parentId != null ? parentId : cat.getParentId(), id);

        cat.setName(name);
        if (slug != null && !slug.isBlank()) {
            cat.setSlug(SlugUtil.sanitize(slug));
        }
        cat.setDescription(description != null ? XssUtil.cleanText(description) : null);
        if (parentId != null) {
            cat.setParentId(parentId);
        }
        if (sortOrder != null) {
            cat.setSortOrder(sortOrder);
        }
        categoryMapper.updateById(cat);
        log.info("分类更新成功: id={}", id);
        return cat;
    }

    /**
     * 删除分类
     * 清除所有分类缓存
     */
    @Transactional
    @CacheEvict(value = {"category:list", "category:topLevel", "category:children", "category:slug", "category:detail"}, allEntries = true)
    public void delete(Long id) {
        getById(id);
        // 检查是否有子分类
        long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>().eq(Category::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该分类下有子分类，无法删除");
        }
        categoryMapper.deleteById(id);
        log.info("分类删除成功: id={}", id);
    }

    private void checkNameUnique(String name, Long parentId, Long excludeId) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getName, name)
                .eq(Category::getParentId, parentId != null ? parentId : 0L);
        if (excludeId != null) {
            wrapper.ne(Category::getId, excludeId);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.NAME_EXISTS, "同级下已存在同名分类");
        }
    }

}
