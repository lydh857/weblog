package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.content.entity.Post;
import com.blog.content.mapper.PostMapper;
import com.blog.interaction.dto.CommentVO;
import com.blog.interaction.dto.CreateCommentRequest;
import com.blog.interaction.entity.Comment;
import com.blog.interaction.service.CommentService;
import com.blog.interaction.service.LikeService;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.system.entity.User;
import com.blog.system.mapper.UserMapper;
import com.blog.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.blog.common.constant.CommonConstant.MAX_PAGE_SIZE;

/**
 * 用户端 - 评论接口
 */
@Tag(name = "用户端-评论", description = "评论发表、删除、列表、点赞")
@RestController
@RequestMapping("/api/portal/comment")
@RequiredArgsConstructor
public class CommentController {

    private static final int MAX_BATCH_OPERATE_COUNT = 100;

    private final CommentService commentService;
    private final LikeService likeService;
    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final SystemConfigService systemConfigService;

    @Operation(summary = "发表评论")
    @PostMapping
    @RateLimit(key = "comment-create", capacity = 10, seconds = 60)
    public Result<CommentVO> create(@Valid @RequestBody CreateCommentRequest req) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        String auditVal = systemConfigService.getValue("comment_audit_enabled");
        boolean auditEnabled = !"false".equals(auditVal); // 默认开启审核
        Comment comment = commentService.createComment(userId, req, auditEnabled);
        User user = userMapper.selectById(userId);
        return Result.success(buildVO(comment, user));
    }

    @Operation(summary = "删除评论（只能删自己的）")
    @DeleteMapping("/{commentId}")
    @RateLimit(key = "comment-delete", capacity = 30, seconds = 60)
    public Result<Void> delete(@PathVariable Long commentId) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        commentService.deleteComment(userId, commentId);
        return Result.success();
    }

    @Operation(summary = "批量删除评论（只能删自己的）")
    @DeleteMapping("/batch")
    @RateLimit(key = "comment-batch-delete", capacity = 10, seconds = 60)
    public Result<Void> batchDelete(@RequestBody List<Long> commentIds) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        if (commentIds == null || commentIds.isEmpty()) {
            return Result.success();
        }
        if (commentIds.size() > MAX_BATCH_OPERATE_COUNT) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单次最多删除" + MAX_BATCH_OPERATE_COUNT + "条评论");
        }
        for (Long id : commentIds) {
            if (id == null || id <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "评论ID不合法");
            }
            commentService.deleteComment(userId, id);
        }
        return Result.success();
    }

    @Operation(summary = "文章评论列表（一级+二级回复）")
    @GetMapping("/post/{postId}")
    @RateLimit(key = "comment-list-post", capacity = 120, seconds = 60)
    public Result<Map<String, Object>> listByPost(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "new") String sort) {

        pageSize = (int) Math.min(pageSize, MAX_PAGE_SIZE);
        IPage<Comment> page = commentService.getTopLevelComments(postId, pageNum, pageSize, sort);

        List<Long> parentIds = page.getRecords().stream()
                .map(Comment::getId).collect(Collectors.toList());

        List<Comment> replies = commentService.getReplies(parentIds);

        // 收集所有用户ID
        Set<Long> userIds = new HashSet<>();
        page.getRecords().forEach(c -> userIds.add(c.getUserId()));
        replies.forEach(c -> {
            userIds.add(c.getUserId());
            if (c.getReplyToUserId() != null) userIds.add(c.getReplyToUserId());
        });

        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        // 组装回复（每个一级评论只取前3条预览）
        Map<Long, List<Comment>> replyGrouped = replies.stream()
                .collect(Collectors.groupingBy(Comment::getParentId));

        List<CommentVO> records = page.getRecords().stream().map(c -> {
            CommentVO vo = toVO(c, userMap);
            // 实时点赞数
            vo.setLikeCount((int) likeService.getCommentLikeCount(c.getId()));
            List<Comment> allReplies = replyGrouped.getOrDefault(c.getId(), List.of());
            vo.setReplyTotal((long) allReplies.size());
            List<Comment> preview = allReplies.size() > 3 ? allReplies.subList(0, 3) : allReplies;
            vo.setReplies(preview.stream().map(r -> {
                CommentVO rvo = toVO(r, userMap);
                rvo.setLikeCount((int) likeService.getCommentLikeCount(r.getId()));
                return rvo;
            }).collect(Collectors.toList()));
            return vo;
        }).collect(Collectors.toList());

        // 附带当前用户的点赞状态
        List<Long> allCommentIds = new ArrayList<>();
        records.forEach(r -> {
            allCommentIds.add(r.getId());
            if (r.getReplies() != null) r.getReplies().forEach(rr -> allCommentIds.add(rr.getId()));
        });
        Set<Long> likedIds = Set.of();
        if (StpUtil.isLogin()) {
            likedIds = likeService.getCommentLikedIds(StpUtil.getLoginIdAsLong(), allCommentIds);
        }
        Set<Long> finalLikedIds = likedIds;
        records.forEach(r -> {
            r.setLiked(finalLikedIds.contains(r.getId()));
            if (r.getReplies() != null) r.getReplies().forEach(rr -> rr.setLiked(finalLikedIds.contains(rr.getId())));
        });

        return Result.success(Map.of(
                "records", records,
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "pages", page.getPages()));
    }

    @Operation(summary = "评论点赞/取消点赞")
    @PostMapping("/like/{commentId}")
    @RateLimit(key = "comment-like-toggle", capacity = 60, seconds = 60)
    public Result<Map<String, Object>> toggleCommentLike(@PathVariable Long commentId) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        boolean liked = likeService.toggleCommentLike(userId, commentId);
        long likeCount = likeService.getCommentLikeCount(commentId);
        return Result.success(Map.of("liked", liked, "likeCount", likeCount));
    }

    @Operation(summary = "子评论分页列表")
    @GetMapping("/replies/{parentId}")
    @RateLimit(key = "comment-list-replies", capacity = 120, seconds = 60)
    public Result<Map<String, Object>> listReplies(
            @PathVariable Long parentId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        pageSize = (int) Math.min(pageSize, MAX_PAGE_SIZE);
        IPage<Comment> page = commentService.getRepliesPage(parentId, pageNum, pageSize);

        Set<Long> userIds = new HashSet<>();
        page.getRecords().forEach(c -> {
            userIds.add(c.getUserId());
            if (c.getReplyToUserId() != null) userIds.add(c.getReplyToUserId());
        });
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<CommentVO> records = page.getRecords().stream()
                .map(c -> {
                    CommentVO vo = toVO(c, userMap);
                    vo.setLikeCount((int) likeService.getCommentLikeCount(c.getId()));
                    return vo;
                })
                .collect(Collectors.toList());

        // 附带当前用户的点赞状态
        if (StpUtil.isLogin()) {
            List<Long> commentIds = records.stream().map(CommentVO::getId).collect(Collectors.toList());
            Set<Long> likedIds = likeService.getCommentLikedIds(StpUtil.getLoginIdAsLong(), commentIds);
            records.forEach(r -> r.setLiked(likedIds.contains(r.getId())));
        }

        return Result.success(Map.of(
                "records", records,
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "pages", page.getPages()));
    }

    @Operation(summary = "我的评论列表")
    @GetMapping("/my")
    @RateLimit(key = "comment-my-list", capacity = 60, seconds = 60)
    public Result<Map<String, Object>> myComments(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        pageSize = (int) Math.min(pageSize, MAX_PAGE_SIZE);
        IPage<Comment> page = commentService.getMyComments(userId, pageNum, pageSize);

        // 收集所有需要查询的用户ID
        Set<Long> userIds = page.getRecords().stream()
                .map(Comment::getUserId).collect(Collectors.toSet());
        // 收集 replyToUserId
        page.getRecords().forEach(c -> {
            if (c.getReplyToUserId() != null && c.getReplyToUserId() > 0) {
                userIds.add(c.getReplyToUserId());
            }
        });

        // 批量查询父评论（用于填充直接回复一级评论时的回复目标昵称）
        Set<Long> parentIds = page.getRecords().stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .map(Comment::getParentId).collect(Collectors.toSet());
        Map<Long, Comment> parentMap = parentIds.isEmpty() ? Map.of()
                : commentService.getByIds(parentIds).stream()
                        .collect(Collectors.toMap(Comment::getId, c -> c));
        // 将父评论作者ID也加入用户查询集合
        parentMap.values().forEach(c -> userIds.add(c.getUserId()));

        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        // 批量查询文章信息（标题、slug）
        Set<Long> postIds = page.getRecords().stream()
                .map(Comment::getPostId).collect(Collectors.toSet());
        Map<Long, Post> postMap = postIds.isEmpty() ? Map.of()
                : postMapper.selectByIds(postIds).stream()
                        .collect(Collectors.toMap(Post::getId, p -> p));

        List<CommentVO> records = page.getRecords().stream()
                .map(c -> {
                    CommentVO vo = toVO(c, userMap);
                    Post post = postMap.get(c.getPostId());
                    if (post != null) {
                        vo.setPostTitle(post.getTitle());
                        vo.setPostSlug(post.getSlug());
                    }
                    // 直接回复一级评论时 replyToUserId 为空，用父评论作者昵称填充
                    if (c.getParentId() != null && c.getParentId() > 0 && vo.getReplyToNickname() == null) {
                        Comment parent = parentMap.get(c.getParentId());
                        if (parent != null) {
                            User parentUser = userMap.get(parent.getUserId());
                            vo.setReplyToNickname(parentUser != null ? parentUser.getNickname() : null);
                        }
                    }
                    return vo;
                })
                .collect(Collectors.toList());

        return Result.success(Map.of(
                "records", records,
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "pages", page.getPages()));
    }

    // ========== VO 转换 ==========

    private CommentVO toVO(Comment c, Map<Long, User> userMap) {
        User user = userMap.get(c.getUserId());
        CommentVO vo = buildVO(c, user);
        // 填充回复目标昵称（replyToUserId 为空表示直接回复一级评论，不显示@用户）
        if (c.getReplyToUserId() != null && c.getReplyToUserId() > 0) {
            User replyUser = userMap.get(c.getReplyToUserId());
            vo.setReplyToNickname(replyUser != null ? replyUser.getNickname() : null);
        }
        return vo;
    }

    private CommentVO buildVO(Comment c, User user) {
        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        vo.setPostId(c.getPostId());
        vo.setUserId(c.getUserId());
        vo.setNickname(user != null ? user.getNickname() : "未知用户");
        vo.setAvatar(user != null ? user.getAvatar() : null);
        vo.setParentId(c.getParentId());
        vo.setContent(c.getContent());
        vo.setLikeCount(c.getLikeCount());
        vo.setIsTop(c.getIsTop());
        vo.setStatus(c.getStatus());
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }
}
