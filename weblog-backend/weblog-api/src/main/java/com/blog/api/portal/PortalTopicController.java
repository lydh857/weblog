package com.blog.api.portal;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.result.Result;
import com.blog.content.dto.CatalogNode;
import com.blog.content.dto.TopicRespVO;
import com.blog.content.entity.Post;
import com.blog.content.entity.Topic;
import com.blog.content.mapper.PostMapper;
import com.blog.content.mapper.TopicMapper;
import com.blog.content.service.TopicService;
import com.blog.infra.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.blog.common.constant.CommonConstant.MAX_PAGE_SIZE;

/**
 * 用户端 - 专题接口
 */
@Tag(name = "用户端-专题", description = "专题列表、详情、目录")
@RestController
@RequestMapping("/api/portal/topic")
@RequiredArgsConstructor
public class PortalTopicController {

    private final TopicService topicService;
    private final TopicMapper topicMapper;
    private final PostMapper postMapper;

    @Operation(summary = "专题列表（已发布）")
    @GetMapping
    @RateLimit(key = "portal-topic-list", capacity = 90, seconds = 60)
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "12") int pageSize) {
        pageSize = (int) Math.min(pageSize, MAX_PAGE_SIZE);
        IPage<TopicRespVO> page = topicService.getTopicPage(pageNum, pageSize, null, true, null);
        return Result.success(Map.of(
                "records", page.getRecords(),
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "pages", page.getPages()));
    }

    @Operation(summary = "专题详情")
    @GetMapping("/{id}")
    @RateLimit(key = "portal-topic-detail", capacity = 120, seconds = 60)
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Topic topic = topicMapper.selectById(id);
        if (topic == null || !Boolean.TRUE.equals(topic.getIsPublish())) {
            return Result.fail(404, "专题不存在或未发布");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", topic.getId());
        data.put("title", topic.getTitle());
        data.put("cover", topic.getCover());
        data.put("summary", topic.getSummary());
        data.put("createTime", topic.getCreateTime());
        return Result.success(data);
    }

    @Operation(summary = "专题目录树（含文章 slug）")
    @GetMapping("/{id}/catalogs")
    @RateLimit(key = "portal-topic-catalogs", capacity = 90, seconds = 60)
    public Result<List<Map<String, Object>>> catalogs(@PathVariable Long id) {
        Topic topic = topicMapper.selectById(id);
        if (topic == null || !Boolean.TRUE.equals(topic.getIsPublish())) {
            return Result.fail(404, "专题不存在或未发布");
        }
        List<CatalogNode> tree = topicService.getCatalogs(id);

        // 收集所有 articleId，批量查询文章 slug 和标题
        List<Long> articleIds = collectArticleIds(tree);
        Map<Long, Post> postMap = articleIds.isEmpty() ? Map.of()
                : postMapper.selectByIds(articleIds).stream()
                        .collect(Collectors.toMap(Post::getId, p -> p));

        // 转换为前端需要的结构（附带 slug）
        List<Map<String, Object>> result = convertNodes(tree, postMap);
        return Result.success(result);
    }

    /** 递归收集所有 articleId */
    private List<Long> collectArticleIds(List<CatalogNode> nodes) {
        List<Long> ids = new ArrayList<>();
        for (CatalogNode node : nodes) {
            if (node.getArticleId() != null) ids.add(node.getArticleId());
            if (node.getChildren() != null) ids.addAll(collectArticleIds(node.getChildren()));
        }
        return ids;
    }

    /** 递归转换节点，附带文章 slug */
    private List<Map<String, Object>> convertNodes(List<CatalogNode> nodes, Map<Long, Post> postMap) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CatalogNode node : nodes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", node.getId());
            item.put("title", node.getTitle());
            item.put("articleId", node.getArticleId());
            item.put("level", node.getLevel());
            if (node.getArticleId() != null) {
                Post post = postMap.get(node.getArticleId());
                item.put("slug", post != null ? post.getSlug() : null);
                item.put("coverImage", post != null ? post.getCoverImage() : null);
            }
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                item.put("children", convertNodes(node.getChildren(), postMap));
            }
            result.add(item);
        }
        return result;
    }
}
