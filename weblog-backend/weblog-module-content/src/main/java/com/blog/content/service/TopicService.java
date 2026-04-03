package com.blog.content.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.PageParamUtil;
import com.blog.content.dto.CatalogNode;
import com.blog.content.dto.SaveTopicReqVO;
import com.blog.content.dto.TopicRespVO;
import com.blog.content.entity.Topic;
import com.blog.content.entity.TopicCatalog;
import com.blog.content.mapper.TopicCatalogMapper;
import com.blog.content.mapper.TopicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 专题服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicService {

    private final TopicMapper topicMapper;
    private final TopicCatalogMapper topicCatalogMapper;

    // ========== 专题 CRUD ==========

    /**
     * 分页查询专题列表（含文章数）
     */
    public IPage<TopicRespVO> getTopicPage(int pageNum, int pageSize, String keyword, Boolean isPublish, Boolean isTop) {
        PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
        Page<?> page = new Page<>(pageParams.pageNum(), pageParams.pageSize());
        IPage<Map<String, Object>> rawPage = topicMapper.selectPageWithArticleCount(page, keyword, isPublish, isTop);
        return rawPage.convert(this::mapToRespVO);
    }

    /**
     * 创建专题
     */
    public Topic createTopic(SaveTopicReqVO req) {
        Topic topic = new Topic();
        topic.setTitle(req.getTitle());
        topic.setCover(req.getCover());
        topic.setSummary(req.getSummary());
        topic.setWeight(0);
        topic.setIsPublish(false);
        topic.setIsTop(false);
        topicMapper.insert(topic);
        log.info("专题创建成功: id={}, title={}", topic.getId(), topic.getTitle());
        return topic;
    }

    /**
     * 更新专题
     */
    public void updateTopic(Long id, SaveTopicReqVO req) {
        Topic topic = topicMapper.selectById(id);
        if (topic == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "专题不存在");
        }
        topic.setTitle(req.getTitle());
        topic.setCover(req.getCover());
        topic.setSummary(req.getSummary());
        topicMapper.updateById(topic);
        log.info("专题更新成功: id={}", id);
    }

    /**
     * 删除专题（逻辑删除，级联删除目录）
     */
    @Transactional
    public void deleteTopic(Long id) {
        Topic topic = topicMapper.selectById(id);
        if (topic == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "专题不存在");
        }
        topicMapper.deleteById(id);
        // 级联逻辑删除该专题的所有目录节点
        topicCatalogMapper.update(null, new LambdaUpdateWrapper<TopicCatalog>()
                .eq(TopicCatalog::getTopicId, id)
                .set(TopicCatalog::getIsDeleted, true));
        log.info("专题删除成功（级联删除目录）: id={}", id);
    }

    // ========== 状态切换 ==========

    /**
     * 切换置顶状态
     */
    public void toggleTop(Long id) {
        Topic topic = topicMapper.selectById(id);
        if (topic == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "专题不存在");
        }
        topic.setIsTop(!Boolean.TRUE.equals(topic.getIsTop()));
        topicMapper.updateById(topic);
        log.info("专题置顶状态切换: id={}, isTop={}", id, topic.getIsTop());
    }

    /**
     * 切换发布状态
     */
    public void togglePublish(Long id) {
        Topic topic = topicMapper.selectById(id);
        if (topic == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "专题不存在");
        }
        topic.setIsPublish(!Boolean.TRUE.equals(topic.getIsPublish()));
        topicMapper.updateById(topic);
        log.info("专题发布状态切换: id={}, isPublish={}", id, topic.getIsPublish());
    }

    // ========== 批量操作 ==========

    /**
     * 批量删除（级联删除目录）
     */
    @Transactional
    public void batchDelete(List<Long> ids) {
        for (Long id : ids) {
            topicMapper.deleteById(id);
            topicCatalogMapper.update(null, new LambdaUpdateWrapper<TopicCatalog>()
                    .eq(TopicCatalog::getTopicId, id)
                    .set(TopicCatalog::getIsDeleted, true));
        }
        log.info("批量删除专题: ids={}", ids);
    }

    /**
     * 批量设置置顶
     */
    @Transactional
    public void batchSetTop(List<Long> ids, boolean isTop) {
        topicMapper.update(null, new LambdaUpdateWrapper<Topic>()
                .in(Topic::getId, ids)
                .set(Topic::getIsTop, isTop));
        log.info("批量设置置顶: ids={}, isTop={}", ids, isTop);
    }

    /**
     * 批量设置发布状态
     */
    @Transactional
    public void batchSetPublish(List<Long> ids, boolean isPublish) {
        topicMapper.update(null, new LambdaUpdateWrapper<Topic>()
                .in(Topic::getId, ids)
                .set(Topic::getIsPublish, isPublish));
        log.info("批量设置发布: ids={}, isPublish={}", ids, isPublish);
    }

    // ========== 目录树 ==========

    /**
     * 获取专题目录树
     */
    public List<CatalogNode> getCatalogs(Long topicId) {
        List<TopicCatalog> flatList = topicCatalogMapper.selectByTopicId(topicId);
        return buildTree(flatList);
    }

    /**
     * 保存专题目录树（整体替换：先删旧数据，再批量插入新数据）
     */
    @Transactional
    public void saveCatalogs(Long topicId, List<CatalogNode> tree) {
        // 逻辑删除旧目录
        topicCatalogMapper.update(null, new LambdaUpdateWrapper<TopicCatalog>()
                .eq(TopicCatalog::getTopicId, topicId)
                .set(TopicCatalog::getIsDeleted, true));
        // 递归插入新目录（逐个插入以获取自增 ID 作为子节点的 parentId）
        insertNodes(tree, topicId, 0L, 1);
        log.info("专题目录保存成功: topicId={}", topicId);
    }

    /**
     * 获取专题已引用的文章 ID 列表
     */
    public List<Long> getArticleIds(Long topicId) {
        return topicCatalogMapper.selectArticleIdsByTopicId(topicId);
    }

    // ========== 回收站 ==========

    /**
     * 分页查询已删除专题（回收站）
     */
    public IPage<Topic> pageDeleted(int pageNum, int pageSize, String keyword) {
        PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
        Page<Topic> page = new Page<>(pageParams.pageNum(), pageParams.pageSize());
        return topicMapper.selectDeletedPage(page, StrUtil.isBlank(keyword) ? null : keyword);
    }

    /**
     * 批量恢复专题（同时恢复目录节点，取消发布状态）
     */
    @Transactional
    public int batchRestore(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            int restored = topicMapper.restoreById(id);
            if (restored > 0) {
                topicCatalogMapper.restoreByTopicId(id);
                count++;
            }
        }
        log.info("批量恢复专题: count={}", count);
        return count;
    }

    /**
     * 批量永久删除专题（同时物理删除目录节点）
     */
    @Transactional
    public int batchPermanentDelete(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            int deleted = topicMapper.permanentDeleteById(id);
            if (deleted > 0) {
                topicCatalogMapper.permanentDeleteByTopicId(id);
                count++;
            }
        }
        log.info("批量永久删除专题: count={}", count);
        return count;
    }

    /**
     * 清空回收站
     */
    @Transactional
    public int clearTrash() {
        List<Long> ids = topicMapper.selectDeletedIds();
        if (ids.isEmpty()) return 0;
        return batchPermanentDelete(ids);
    }

    // ========== 私有方法 ==========

    /**
     * 将平铺节点列表重建为树形结构
     */
    private List<CatalogNode> buildTree(List<TopicCatalog> flatList) {
        Map<Long, CatalogNode> nodeMap = new LinkedHashMap<>();
        for (TopicCatalog catalog : flatList) {
            CatalogNode node = new CatalogNode();
            node.setId(catalog.getId());
            node.setArticleId(catalog.getArticleId());
            node.setTitle(catalog.getTitle());
            node.setLevel(catalog.getLevel());
            node.setParentId(catalog.getParentId());
            node.setSort(catalog.getSort());
            node.setChildren(new ArrayList<>());
            nodeMap.put(catalog.getId(), node);
        }
        List<CatalogNode> roots = new ArrayList<>();
        for (CatalogNode node : nodeMap.values()) {
            if (node.getParentId() == null || node.getParentId() == 0L) {
                roots.add(node);
            } else {
                CatalogNode parent = nodeMap.get(node.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        }
        return roots;
    }

    /**
     * 递归插入目录节点（逐个插入以获取自增 ID 作为子节点的 parentId）
     */
    private void insertNodes(List<CatalogNode> nodes, Long topicId, Long parentId, int level) {
        if (nodes == null) return;
        for (int i = 0; i < nodes.size(); i++) {
            CatalogNode node = nodes.get(i);
            TopicCatalog catalog = new TopicCatalog();
            catalog.setTopicId(topicId);
            catalog.setArticleId(node.getArticleId());
            catalog.setTitle(node.getTitle());
            catalog.setLevel(level);
            catalog.setParentId(parentId);
            catalog.setSort(i);
            topicCatalogMapper.insert(catalog);
            // insert 后 MyBatis-Plus 回填自增 ID，用于子节点的 parentId
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                insertNodes(node.getChildren(), topicId, catalog.getId(), level + 1);
            }
        }
    }

    /**
     * 将 Map 转换为 TopicRespVO
     */
    private TopicRespVO mapToRespVO(Map<String, Object> map) {
        TopicRespVO vo = new TopicRespVO();
        vo.setId(toLong(map.get("id")));
        vo.setTitle((String) map.get("title"));
        vo.setCover((String) map.get("cover"));
        vo.setSummary((String) map.get("summary"));
        vo.setWeight(toInt(map.get("weight")));
        vo.setIsPublish(toBoolean(map.get("is_publish")));
        vo.setIsTop(toBoolean(map.get("is_top")));
        vo.setArticleCount(toInt(map.get("article_count")));
        vo.setCreateTime(map.get("create_time") instanceof java.time.LocalDateTime lt ? lt : null);
        vo.setUpdateTime(map.get("update_time") instanceof java.time.LocalDateTime lt ? lt : null);
        return vo;
    }

    private Long toLong(Object obj) {
        if (obj instanceof Long l) return l;
        if (obj instanceof Number n) return n.longValue();
        return null;
    }

    private Integer toInt(Object obj) {
        if (obj instanceof Integer i) return i;
        if (obj instanceof Number n) return n.intValue();
        return 0;
    }

    private Boolean toBoolean(Object obj) {
        if (obj instanceof Boolean b) return b;
        if (obj instanceof Number n) return n.intValue() != 0;
        return false;
    }
}
