package com.blog.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.system.dto.UpdateProfileRequest;
import com.blog.system.entity.User;
import com.blog.system.entity.UserProfileReview;
import com.blog.system.mapper.UserMapper;
import com.blog.system.mapper.UserProfileReviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

import static com.blog.common.constant.CommonConstant.PROFILE_REVIEW_APPROVED;
import static com.blog.common.constant.CommonConstant.PROFILE_REVIEW_PENDING;
import static com.blog.common.constant.CommonConstant.PROFILE_REVIEW_REJECTED;

/**
 * 用户个人信息审核服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileReviewService {

    private final UserMapper userMapper;
    private final UserProfileReviewMapper userProfileReviewMapper;
    private final UserService userService;

    public UserProfileReview getLatestReviewByUserId(Long userId) {
        return userProfileReviewMapper.selectOne(new LambdaQueryWrapper<UserProfileReview>()
                .eq(UserProfileReview::getUserId, userId)
                .orderByDesc(UserProfileReview::getUpdateTime)
                .last("LIMIT 1"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void submitReview(Long userId, UpdateProfileRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        String pendingNickname = req.getNickname() != null ? req.getNickname() : user.getNickname();
        String pendingBio = req.getBio() != null ? req.getBio() : user.getBio();
        String pendingAvatar = req.getAvatar() != null ? req.getAvatar() : user.getAvatar();

        boolean changed = !Objects.equals(user.getNickname(), pendingNickname)
                || !Objects.equals(user.getBio(), pendingBio)
                || !Objects.equals(user.getAvatar(), pendingAvatar);

        if (!changed) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未检测到资料变更，无需提交审核");
        }

        UserProfileReview review = getLatestReviewByUserId(userId);
        if (review == null) {
            review = new UserProfileReview();
            review.setUserId(userId);
        }

        review.setPendingNickname(pendingNickname);
        review.setPendingBio(pendingBio);
        review.setPendingAvatar(pendingAvatar);
        review.setStatus(PROFILE_REVIEW_PENDING);
        review.setRejectReason(null);
        review.setReviewerId(null);
        review.setReviewTime(null);

        if (review.getId() == null) {
            userProfileReviewMapper.insert(review);
        } else {
            userProfileReviewMapper.updateById(review);
        }

        log.info("提交个人信息审核: userId={}", userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void approveReview(Long reviewId, Long reviewerId) {
        UserProfileReview review = userProfileReviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "审核记录不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = userProfileReviewMapper.update(null, new LambdaUpdateWrapper<UserProfileReview>()
                .eq(UserProfileReview::getId, reviewId)
                .eq(UserProfileReview::getStatus, PROFILE_REVIEW_PENDING)
                .set(UserProfileReview::getStatus, PROFILE_REVIEW_APPROVED)
                .set(UserProfileReview::getRejectReason, null)
                .set(UserProfileReview::getReviewerId, reviewerId)
                .set(UserProfileReview::getReviewTime, now));

        if (updated == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该记录不在待审核状态");
        }

        userService.applyApprovedProfile(review.getUserId(), review.getPendingNickname(), review.getPendingBio(), review.getPendingAvatar());
    }

    @Transactional(rollbackFor = Exception.class)
    public void rejectReview(Long reviewId, Long reviewerId, String reason) {
        UserProfileReview review = userProfileReviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "审核记录不存在");
        }
        int updated = userProfileReviewMapper.update(null, new LambdaUpdateWrapper<UserProfileReview>()
                .eq(UserProfileReview::getId, reviewId)
                .eq(UserProfileReview::getStatus, PROFILE_REVIEW_PENDING)
                .set(UserProfileReview::getStatus, PROFILE_REVIEW_REJECTED)
                .set(UserProfileReview::getRejectReason, reason)
                .set(UserProfileReview::getReviewerId, reviewerId)
                .set(UserProfileReview::getReviewTime, LocalDateTime.now()));

        if (updated == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该记录不在待审核状态");
        }
    }
}
