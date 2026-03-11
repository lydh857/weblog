package com.blog.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.system.entity.UserProfileReview;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户个人信息审核 Mapper
 */
@Mapper
public interface UserProfileReviewMapper extends BaseMapper<UserProfileReview> {
}
