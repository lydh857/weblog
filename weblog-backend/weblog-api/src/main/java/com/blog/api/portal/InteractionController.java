package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.result.Result;
import com.blog.content.entity.Post;
import com.blog.content.mapper.PostMapper;
import com.blog.interaction.entity.UserFavorite;
import com.blog.interaction.entity.UserLike;
import com.blog.interaction.mapper.UserLikeMapper;
import com.blog.interaction.service.FavoriteService;
import com.blog.interaction.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.blog.common.constant.CommonConstant.MAX_PAGE_SIZE;

/**
 * 用户端 - 互动接口（点赞、收藏）
 */
@Tag(name = "用户端-互动", description = "点赞、收藏、我的互动")
@RestController
@RequestMapping("/api/portal/interaction")
@RequiredArgsConstructor
public class InteractionController {

    private final LikeService likeService;
    private final FavoriteService favoriteService;
    private final PostMapper postMapper;
    private final UserLikeMapper userLikeMapper;

    // ========== 点赞 ==========

    @Operation(summary = "点赞/取消点赞文章")
    @PostMapping("/like/{postId}")
    public Result<Map<String, Object>> toggleLike(@PathVariable Long postId) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        boolean liked = likeService.toggleLike(userId, postId);
        long count = likeService.getLikeCount(postId);
        return Result.success(Map.of("liked", liked, "likeCount", count));
    }

    @Operation(summary = "查询是否点赞")
    @GetMapping("/like/{postId}")
    public Result<Map<String, Object>> isLiked(@PathVariable Long postId) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        boolean liked = likeService.isLiked(userId, postId);
        long count = likeService.getLikeCount(postId);
        return Result.success(Map.of("liked", liked, "likeCount", count));
    }

    // ========== 收藏 ==========

    @Operation(summary = "收藏/取消收藏文章")
    @PostMapping("/favorite/{postId}")
    public Result<Map<String, Object>> toggleFavorite(@PathVariable Long postId) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        boolean favorited = favoriteService.toggleFavorite(userId, postId);
        long collectCount = favoriteService.getCollectCount(postId);
        return Result.success(Map.of("favorited", favorited, "collectCount", collectCount));
    }

    @Operation(summary = "查询是否收藏")
    @GetMapping("/favorite/{postId}")
    public Result<Map<String, Object>> isFavorited(@PathVariable Long postId) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        boolean favorited = favoriteService.isFavorited(userId, postId);
        return Result.success(Map.of("favorited", favorited));
    }

    // ========== 我的互动 ==========

    @Operation(summary = "我点赞的文章列表")
    @GetMapping("/my/likes")
    public Result<Map<String, Object>> myLikes(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        pageSize = (int) Math.min(pageSize, MAX_PAGE_SIZE);

        // 查询用户点赞记录
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserLike> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        IPage<UserLike> likePage = userLikeMapper.selectPage(page,
                new LambdaQueryWrapper<UserLike>()
                        .eq(UserLike::getUserId, userId)
                        .eq(UserLike::getTargetType, "post")
                        .orderByDesc(UserLike::getCreateTime));

        List<Long> postIds = likePage.getRecords().stream()
                .map(UserLike::getTargetId).collect(Collectors.toList());

        List<Post> posts = postIds.isEmpty() ? List.of()
                : postMapper.selectByIds(postIds);

        // 保持点赞时间排序
        Map<Long, Post> postMap = posts.stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        List<Post> sorted = postIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return Result.success(Map.of(
                "records", sorted,
                "total", likePage.getTotal(),
                "current", likePage.getCurrent(),
                "pages", likePage.getPages()));
    }

    @Operation(summary = "批量取消收藏")
    @DeleteMapping("/favorite/batch")
    public Result<Void> batchUnfavorite(@RequestBody List<Long> postIds) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        for (Long postId : postIds) {
            if (favoriteService.isFavorited(userId, postId)) {
                favoriteService.toggleFavorite(userId, postId);
            }
        }
        return Result.success();
    }

    @Operation(summary = "我收藏的文章列表")
    @GetMapping("/my/favorites")
    public Result<Map<String, Object>> myFavorites(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        pageSize = (int) Math.min(pageSize, MAX_PAGE_SIZE);

        IPage<UserFavorite> favPage = favoriteService.getUserFavorites(userId, pageNum, pageSize);

        List<Long> postIds = favPage.getRecords().stream()
                .map(UserFavorite::getPostId).collect(Collectors.toList());

        List<Post> posts = postIds.isEmpty() ? List.of()
                : postMapper.selectByIds(postIds);

        Map<Long, Post> postMap = posts.stream()
                .collect(Collectors.toMap(Post::getId, p -> p));

        // 构建收藏时间映射
        Map<Long, java.time.LocalDateTime> favTimeMap = favPage.getRecords().stream()
                .collect(Collectors.toMap(UserFavorite::getPostId, UserFavorite::getCreateTime));

        // 按收藏顺序返回，附带收藏时间
        List<Map<String, Object>> records = postIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .map(p -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", p.getId());
                    item.put("title", p.getTitle());
                    item.put("slug", p.getSlug());
                    item.put("summary", p.getSummary());
                    item.put("coverImage", p.getCoverImage());
                    item.put("viewCount", p.getViewCount());
                    item.put("likeCount", p.getLikeCount());
                    item.put("commentCount", p.getCommentCount());
                    item.put("createTime", p.getCreateTime());
                    item.put("favoriteTime", favTimeMap.get(p.getId()));
                    return item;
                })
                .collect(Collectors.toList());

        return Result.success(Map.of(
                "records", records,
                "total", favPage.getTotal(),
                "current", favPage.getCurrent(),
                "pages", favPage.getPages()));
    }

    // ========== 文章互动状态（供详情页使用） ==========

    @Operation(summary = "查询文章互动状态（点赞+收藏）")
    @GetMapping("/status/{postId}")
    public Result<Map<String, Object>> getInteractionStatus(@PathVariable Long postId) {
        boolean liked = false;
        boolean favorited = false;
        if (StpUtil.isLogin()) {
            Long userId = StpUtil.getLoginIdAsLong();
            liked = likeService.isLiked(userId, postId);
            favorited = favoriteService.isFavorited(userId, postId);
        }
        long likeCount = likeService.getLikeCount(postId);
        long collectCount = favoriteService.getCollectCount(postId);
        return Result.success(Map.of(
                "liked", liked,
                "favorited", favorited,
                "likeCount", likeCount,
                "collectCount", collectCount));
    }
}
