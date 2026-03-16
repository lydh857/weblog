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
}
