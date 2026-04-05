package com.blog.interaction.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论 VO（含子回复）
 */
@Data
public class CommentVO {

    private Long id;
    private Long postId;
    private Long userId;
    private String nickname;
    private String avatar;
    private Long parentId;
    private String content;
    private Integer likeCount;
    private Boolean isTop;
    private String status;
    private LocalDateTime createTime;

    /** 文章标题 */
    private String postTitle;

    /** 文章 slug（用于跳转） */
    private String postSlug;

    /** 回复对象昵称（parentId > 0 时填充） */
    private String replyToNickname;

    /** 当前用户是否已点赞 */
    private Boolean liked;

    /** 二级回复总数 */
    private Long replyTotal;

    /** 二级回复列表 */
    private List<CommentVO> replies;

    /** 评论者是否为管理员 */
    private Boolean isAdmin;
}
