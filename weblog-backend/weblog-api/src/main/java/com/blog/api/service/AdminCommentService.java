package com.blog.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.PageParamUtil;
import com.blog.content.entity.Post;
import com.blog.content.mapper.PostMapper;
import com.blog.infra.redis.RedisCounterUtil;
import com.blog.interaction.dto.CommentVO;
import com.blog.interaction.entity.Comment;
import com.blog.interaction.mapper.CommentMapper;
import com.blog.system.entity.User;
import com.blog.system.mapper.UserMapper;
import com.blog.system.service.EmailService;
import com.blog.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static com.blog.common.constant.CommonConstant.MAX_BATCH_SIZE;

/**
 * 管理端评论服务 - 聚合 interaction/system/content 模块
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCommentService {

  private final CommentMapper commentMapper;
  private final UserMapper userMapper;
  private final PostMapper postMapper;
  private final StringRedisTemplate redisTemplate;
  private final EmailService emailService;
  private final SystemConfigService systemConfigService;

  private static final String KEY_COMMENT_COUNT = "post:comment:";
  private static final String KEY_COMMENT_DIRTY = "post:comment:dirty";
  private static final int MAX_REJECT_REASON_LENGTH = 200;
  private static final String DEFAULT_REVIEW_BASE_URL = "http://localhost:3000";
  private static final Set<String> VALID_COMMENT_STATUSES = Set.of("pending", "approved", "rejected", "spam");
  private static final List<String> DEFAULT_REJECT_REASONS = List.of(
      "内容与文章主题无关",
      "包含广告或推广信息",
      "存在不友善或攻击性表达",
      "疑似重复评论",
      "包含违规或敏感内容"
  );

  /**
   * 获取待审核评论数量
   */
  public long getPendingCount() {
    return commentMapper.selectCount(
        new LambdaQueryWrapper<Comment>().eq(Comment::getStatus, "pending"));
  }

  /**
   * 分页查询评论列表
   */
  public Map<String, Object> listComments(int pageNum, int pageSize,
                                           String status, Long postId,
                                           String postTitle, Boolean isTop) {
    PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);

    // 按文章标题搜索：先查匹配的 postId 集合
    Set<Long> matchedPostIds = null;
    if (postTitle != null && !postTitle.trim().isEmpty()) {
      List<Post> matchedPosts = postMapper.selectList(
          new LambdaQueryWrapper<Post>()
              .like(Post::getTitle, postTitle.trim())
              .select(Post::getId));
      matchedPostIds = matchedPosts.stream().map(Post::getId).collect(Collectors.toSet());
      if (matchedPostIds.isEmpty()) {
        return Map.of("records", List.of(), "total", 0L, "current", (long) pageParams.pageNum(), "pages", 0L);
      }
    }

    LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(Comment::getStatus, status);
    }
    if (postId != null) {
      wrapper.eq(Comment::getPostId, postId);
    } else if (matchedPostIds != null) {
      wrapper.in(Comment::getPostId, matchedPostIds);
    }
    if (isTop != null) {
      wrapper.eq(Comment::getIsTop, isTop);
    }
    // 待审核优先，然后按时间倒序
    // 注：此处 last() 内容为固定 SQL，不含用户输入，无注入风险
    wrapper.last("ORDER BY FIELD(status, 'pending', 'approved', 'rejected') ASC, create_time DESC");

    IPage<Comment> page = commentMapper.selectPage(new Page<>(pageParams.pageNum(), pageParams.pageSize()), wrapper);

    // 填充用户信息
    Set<Long> userIds = page.getRecords().stream()
        .map(Comment::getUserId).collect(Collectors.toSet());
    Map<Long, User> userMap = userIds.isEmpty() ? new HashMap<>()
        : new HashMap<>(userMapper.selectByIds(userIds).stream()
            .collect(Collectors.toMap(User::getId, u -> u)));

    // 填充文章标题
    Set<Long> postIds = page.getRecords().stream()
        .map(Comment::getPostId).collect(Collectors.toSet());
    Map<Long, String> postTitleMap = postIds.isEmpty() ? Map.of()
        : postMapper.selectByIds(postIds).stream()
            .collect(Collectors.toMap(Post::getId, p -> p.getTitle() != null ? p.getTitle() : "无标题"));

    // 填充回复对象昵称
    Set<Long> parentIds = page.getRecords().stream()
        .map(Comment::getParentId)
        .filter(pid -> pid != null && pid > 0)
        .collect(Collectors.toSet());
    Map<Long, Comment> parentCommentMap = parentIds.isEmpty() ? Map.of()
        : commentMapper.selectByIds(parentIds).stream()
            .collect(Collectors.toMap(Comment::getId, c -> c));
    Set<Long> parentUserIds = parentCommentMap.values().stream()
        .map(Comment::getUserId)
        .filter(uid -> !userMap.containsKey(uid))
        .collect(Collectors.toSet());
    if (!parentUserIds.isEmpty()) {
      userMapper.selectByIds(parentUserIds).forEach(u -> userMap.put(u.getId(), u));
    }

    List<CommentVO> records = page.getRecords().stream().map(c -> {
      CommentVO vo = new CommentVO();
      vo.setId(c.getId());
      vo.setPostId(c.getPostId());
      vo.setUserId(c.getUserId());
      User user = userMap.get(c.getUserId());
      vo.setNickname(user != null ? user.getNickname() : "未知");
      vo.setAvatar(user != null ? user.getAvatar() : null);
      vo.setParentId(c.getParentId());
      vo.setContent(c.getContent());
      vo.setLikeCount(c.getLikeCount());
      vo.setIsTop(c.getIsTop());
      vo.setStatus(c.getStatus());
      vo.setRejectReason(c.getRejectReason());
      vo.setCreateTime(c.getCreateTime());
      vo.setPostTitle(postTitleMap.getOrDefault(c.getPostId(), "未知文章"));
      if (c.getParentId() != null && c.getParentId() > 0) {
        Comment parentComment = parentCommentMap.get(c.getParentId());
        if (parentComment != null) {
          User parentUser = userMap.get(parentComment.getUserId());
          vo.setReplyToNickname(parentUser != null ? parentUser.getNickname() : "未知");
        }
      }
      return vo;
    }).collect(Collectors.toList());

    return Map.of(
        "records", records,
        "total", page.getTotal(),
        "current", page.getCurrent(),
        "pages", page.getPages());
  }

  /**
   * 审核评论（更新状态）
   */
  public void updateStatus(Long commentId, String status) {
    updateStatus(commentId, status, null);
  }

  public void updateStatus(Long commentId, String status, String reason) {
    if (!VALID_COMMENT_STATUSES.contains(status)) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "无效的状态值，仅支持: pending, approved, rejected, spam");
    }
    String normalizedReason = normalizeRejectReason(status, reason);
    Comment comment = commentMapper.selectById(commentId);
    if (comment == null) {
      return;
    }
    String oldStatus = comment.getStatus();
    commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
        .eq(Comment::getId, commentId)
        .set(Comment::getStatus, status)
        .set(Comment::getRejectReason, "rejected".equals(status) ? normalizedReason : null));
    updateCommentCount(comment.getPostId(), oldStatus, status);
    sendCommentReviewResultEmail(comment, status, normalizedReason);
  }

  /**
   * 置顶/取消置顶评论
   */
  public void toggleTop(Long commentId) {
    Comment comment = commentMapper.selectById(commentId);
    if (comment != null) {
      if (!"approved".equals(comment.getStatus())) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "只有审核通过的评论才能置顶");
      }
      commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
          .eq(Comment::getId, commentId)
          .set(Comment::getIsTop, !Boolean.TRUE.equals(comment.getIsTop())));
    }
  }

  /**
   * 删除评论（允许删除已通过和已拒绝状态的评论，待审核评论须先审核）
   */
  public void deleteComment(Long commentId) {
    Comment comment = commentMapper.selectById(commentId);
    if (comment == null) {
      return;
    }
    if ("pending".equals(comment.getStatus())) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "待审核评论请先审核后再删除");
    }
    commentMapper.deleteById(commentId);
    if ("approved".equals(comment.getStatus())) {
      RedisCounterUtil.safeDecrement(redisTemplate, KEY_COMMENT_COUNT + comment.getPostId());
      redisTemplate.opsForSet().add(KEY_COMMENT_DIRTY, comment.getPostId().toString());
    }
  }

  /**
   * 批量删除评论（允许删除已通过和已拒绝状态，待审核评论须先审核）
   */
  public void batchDelete(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return;
    }
    if (ids.size() > MAX_BATCH_SIZE) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "批量操作数量不能超过 " + MAX_BATCH_SIZE);
    }
    List<Comment> comments = commentMapper.selectByIds(ids);
    boolean hasPending = comments.stream().anyMatch(c -> "pending".equals(c.getStatus()));
    if (hasPending) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "待审核评论请先审核后再删除");
    }
    commentMapper.deleteByIds(ids);
    for (Comment c : comments) {
      if ("approved".equals(c.getStatus())) {
        RedisCounterUtil.safeDecrement(redisTemplate, KEY_COMMENT_COUNT + c.getPostId());
        redisTemplate.opsForSet().add(KEY_COMMENT_DIRTY, c.getPostId().toString());
      }
    }
  }

  /**
   * 批量审核评论
   */
  public void batchUpdateStatus(List<Long> ids, String status) {
    batchUpdateStatus(ids, status, null);
  }

  public void batchUpdateStatus(List<Long> ids, String status, String reason) {
    if (!VALID_COMMENT_STATUSES.contains(status)) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "无效的状态值，仅支持: pending, approved, rejected, spam");
    }
    String normalizedReason = normalizeRejectReason(status, reason);
    if (ids == null || ids.isEmpty()) {
      return;
    }
    if (ids.size() > MAX_BATCH_SIZE) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "批量操作数量不能超过 " + MAX_BATCH_SIZE);
    }
    List<Comment> comments = commentMapper.selectByIds(ids);
    commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
        .in(Comment::getId, ids)
        .set(Comment::getStatus, status)
        .set(Comment::getRejectReason, "rejected".equals(status) ? normalizedReason : null));
    for (Comment c : comments) {
      updateCommentCount(c.getPostId(), c.getStatus(), status);
      sendCommentReviewResultEmail(c, status, normalizedReason);
    }
  }

  public void sendPendingReviewEmail(Long commentId) {
    try {
      doSendPendingReviewEmail(commentId);
    } catch (Exception e) {
      log.warn("评论待审核提醒邮件发送失败: commentId={}, error={}", commentId, e.getMessage());
    }
  }

  private void doSendPendingReviewEmail(Long commentId) {
    Comment comment = commentMapper.selectById(commentId);
    if (comment == null || !"pending".equals(comment.getStatus())) {
      return;
    }
    User user = userMapper.selectById(comment.getUserId());
    Post post = postMapper.selectById(comment.getPostId());
    String adminEmail = systemConfigService.getValue("mail_username");
    if (adminEmail == null || adminEmail.isBlank()) {
      return;
    }

    String adminReviewUrl = createAdminReviewUrl("/admin/comment?status=pending&focusId=" + comment.getId());
    String html = """
        <div style=\"max-width:560px;margin:0 auto;padding:32px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;\">
          <p style=\"margin:0 0 20px;font-size:17px;font-weight:600;color:#0f172a;\">有新评论待审核</p>
          <div style=\"padding:16px 18px;border:1px solid #e2e8f0;border-radius:12px;margin-bottom:20px;\">
            <p style=\"margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;\"><b>用户：</b>%s（ID：%d）</p>
            <p style=\"margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;\"><b>邮箱：</b>%s</p>
            <p style=\"margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;\"><b>文章：</b>%s（ID：%d）</p>
            <p style=\"margin:0;color:#334155;font-size:14px;line-height:1.6;\"><b>评论：</b>%s</p>
          </div>
          <p style=\"margin:0 0 16px;color:#94a3b8;font-size:12px;line-height:1.5;\">请进入管理端登录后处理。通过、拒绝、禁言、封禁均由管理端统一鉴权和审计。</p>
          %s
        </div>
        """.formatted(
        escapeHtml(user != null ? user.getNickname() : "未知用户"),
        comment.getUserId(),
        escapeHtml(user != null && user.getEmail() != null ? user.getEmail() : "未绑定"),
        escapeHtml(post != null && post.getTitle() != null ? post.getTitle() : "未知文章"),
        comment.getPostId(),
        escapeHtml(comment.getContent()),
        buttonHtml("去管理端审核", adminReviewUrl, "#2563eb")
    );
    emailService.sendHtmlMailAsync(adminEmail, "新评论待审核", html);
  }

  /**
   * 评论状态变更时更新 Redis 计数
   * 从非 approved → approved：计数+1
   * 从 approved → 非 approved：计数-1
   */
  private void updateCommentCount(Long postId, String oldStatus, String newStatus) {
    boolean wasApproved = "approved".equals(oldStatus);
    boolean isApproved = "approved".equals(newStatus);
    if (!wasApproved && isApproved) {
      redisTemplate.opsForValue().increment(KEY_COMMENT_COUNT + postId);
      redisTemplate.opsForSet().add(KEY_COMMENT_DIRTY, postId.toString());
    } else if (wasApproved && !isApproved) {
      RedisCounterUtil.safeDecrement(redisTemplate, KEY_COMMENT_COUNT + postId);
      redisTemplate.opsForSet().add(KEY_COMMENT_DIRTY, postId.toString());
    }
  }

  private String normalizeRejectReason(String status, String reason) {
    if (!"rejected".equals(status)) {
      return null;
    }
    String normalized = reason == null ? "" : reason.trim();
    if (normalized.isEmpty()) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "拒绝原因不能为空");
    }
    if (normalized.length() > MAX_REJECT_REASON_LENGTH) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "拒绝原因不能超过" + MAX_REJECT_REASON_LENGTH + "字");
    }
    return normalized;
  }

  private void sendCommentReviewResultEmail(Comment comment, String status, String reason) {
    try {
      doSendCommentReviewResultEmail(comment, status, reason);
    } catch (Exception e) {
      log.warn("评论审核结果邮件发送失败: commentId={}, status={}, error={}",
          comment != null ? comment.getId() : null, status, e.getMessage());
    }
  }

  private void doSendCommentReviewResultEmail(Comment comment, String status, String reason) {
    if (!"approved".equals(status) && !"rejected".equals(status)) {
      return;
    }
    User user = userMapper.selectById(comment.getUserId());
    if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
      return;
    }
    Post post = postMapper.selectById(comment.getPostId());
    String subject = "approved".equals(status) ? "您的评论已通过审核" : "您的评论未通过审核";
    String reasonHtml = "rejected".equals(status)
        ? "<div style=\"background:linear-gradient(135deg,#fef2f2,#fff1f2);border:1px solid #fecaca;border-radius:12px;padding:14px 16px;margin:16px 0 0;\"><b>原因：</b>" + escapeHtml(reason) + "</div>"
        : "";
    String html = """
        <div style=\"max-width:480px;margin:0 auto;padding:32px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;\">
          <p style=\"margin:0 0 20px;font-size:17px;font-weight:600;color:#0f172a;\">%s</p>
          <p style=\"margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;\"><b>文章：</b>%s</p>
          <p style=\"margin:0;color:#334155;font-size:14px;line-height:1.6;\"><b>评论：</b>%s</p>
          %s
        </div>
        """.formatted(subject, escapeHtml(post != null && post.getTitle() != null ? post.getTitle() : "未知文章"), escapeHtml(comment.getContent()), reasonHtml);
    emailService.sendHtmlMailAsync(user.getEmail(), subject, html);
  }

  private String createAdminReviewUrl(String pathAndQuery) {
    String baseUrl = systemConfigService.getValue("review_action_base_url");
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = DEFAULT_REVIEW_BASE_URL;
    }
    baseUrl = baseUrl.trim();
    validateReviewBaseUrl(baseUrl);
    baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    return baseUrl + pathAndQuery;
  }

  private void validateReviewBaseUrl(String baseUrl) {
    try {
      URI uri = URI.create(baseUrl.trim());
      String scheme = uri.getScheme();
      if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) || uri.getHost() == null) {
        throw new IllegalArgumentException("invalid review base url");
      }
    } catch (Exception e) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "管理端审核链接基础地址配置无效");
    }
  }

  private String buttonHtml(String text, String url, String color) {
    return "<a href=\"" + escapeHtml(url) + "\" style=\"display:inline-block;margin:6px 6px 0 0;padding:10px 20px;background:"
        + color + ";color:#fff;text-decoration:none;border-radius:10px;font-size:14px;font-weight:600;\">" + escapeHtml(text) + "</a>";
  }

  private String escapeHtml(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}