package com.blog.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.system.entity.UserProfileReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户个人信息审核 Mapper
 */
@Mapper
public interface UserProfileReviewMapper extends BaseMapper<UserProfileReview> {

    /** 查询待审核头像 URL */
    @Select("SELECT pending_avatar FROM t_user_profile_review WHERE is_deleted = 0 AND status = 'pending' AND pending_avatar IS NOT NULL AND pending_avatar <> ''")
    java.util.List<String> selectPendingAvatarUrls();

    /** 查询待审核头像引用详情 */
    @Select("""
            SELECT r.user_id, u.nickname, u.email, r.pending_avatar
            FROM t_user_profile_review r
            LEFT JOIN t_user u ON u.id = r.user_id AND u.is_deleted = 0
            WHERE r.is_deleted = 0 AND r.status = 'pending'
              AND r.pending_avatar IS NOT NULL AND r.pending_avatar <> ''
            """)
    java.util.List<java.util.Map<String, Object>> selectPendingAvatarSummaries();
}
