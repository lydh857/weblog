package com.blog.api.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.content.entity.OssResource;
import com.blog.content.mapper.OssResourceMapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.common.util.PageParamUtil;
import com.blog.infra.security.audit.AuditLog;
import com.blog.system.entity.User;
import com.blog.system.entity.UserProfileReview;
import com.blog.system.mapper.UserMapper;
import com.blog.system.mapper.UserProfileReviewMapper;
import com.blog.system.service.UserProfileReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.blog.common.constant.CommonConstant.PROFILE_REVIEW_PENDING;

@Tag(name = "管理端-个人信息审核", description = "个人头像/昵称/简介审核")
@Validated
@RestController
@RequestMapping({"/api/admin/profile-review", "/api/admin/avatar-review"})
public class AdminProfileReviewController {

    private static final String AVATAR_USAGE = "avatar";
    private static final String AVATAR_PENDING_FILE_PREFIX = "avatar_pending__";
    private static final String AVATAR_REJECTED_FILE_PREFIX = "avatar_rejected__";

    private final UserProfileReviewMapper userProfileReviewMapper;
    private final UserMapper userMapper;
    private final UserProfileReviewService userProfileReviewService;
    private final OssResourceMapper ossResourceMapper;

    public AdminProfileReviewController(UserProfileReviewMapper userProfileReviewMapper,
                                        UserMapper userMapper,
                                        UserProfileReviewService userProfileReviewService,
                                        OssResourceMapper ossResourceMapper) {
        this.userProfileReviewMapper = userProfileReviewMapper;
        this.userMapper = userMapper;
        this.userProfileReviewService = userProfileReviewService;
        this.ossResourceMapper = ossResourceMapper;
    }

    @Operation(summary = "分页查询待审核个人信息")
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);

        LambdaQueryWrapper<UserProfileReview> wrapper = new LambdaQueryWrapper<UserProfileReview>()
                .eq(UserProfileReview::getStatus, PROFILE_REVIEW_PENDING)
                .orderByDesc(UserProfileReview::getUpdateTime);

        if (keyword != null && !keyword.isBlank()) {
            List<Long> matchedUserIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getId)
                            .and(w -> w.like(User::getNickname, keyword).or().like(User::getEmail, keyword)))
                    .stream()
                    .map(User::getId)
                    .toList();
            if (matchedUserIds.isEmpty()) {
                return Result.success(Map.of(
                        "records", List.of(),
                        "total", 0,
                        "current", (long) pageParams.pageNum(),
                        "pages", 0));
            }
            wrapper.in(UserProfileReview::getUserId, matchedUserIds);
        }

        IPage<UserProfileReview> page = userProfileReviewMapper.selectPage(new Page<>(pageParams.pageNum(), pageParams.pageSize()), wrapper);

        Set<Long> userIds = page.getRecords().stream()
                .map(UserProfileReview::getUserId)
                .collect(Collectors.toSet());

        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, userIds))
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<ProfileReviewVO> records = page.getRecords().stream().map(review -> {
            User user = userMap.get(review.getUserId());
            ProfileReviewVO vo = new ProfileReviewVO();
            vo.reviewId = review.getId();
            vo.userId = review.getUserId();
            vo.email = user != null ? user.getEmail() : "";
            vo.currentNickname = user != null ? user.getNickname() : "用户已不存在";
            vo.currentBio = user != null ? user.getBio() : null;
            vo.currentAvatar = user != null ? user.getAvatar() : null;
            vo.pendingNickname = review.getPendingNickname();
            vo.pendingBio = review.getPendingBio();
            vo.pendingAvatar = review.getPendingAvatar();
            vo.submitTime = review.getUpdateTime();
            return vo;
        }).toList();

        return Result.success(Map.of(
                "records", records,
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "pages", page.getPages()));
    }

    @Operation(summary = "通过个人信息审核")
    @PutMapping("/{reviewId}/approve")
    @AuditLog(module = "个人信息审核", operation = "UPDATE", description = "通过个人信息审核")
    public Result<Void> approve(@PathVariable Long reviewId) {
        UserProfileReview review = getPendingReview(reviewId);
        Long reviewerId = StpUtil.getLoginIdAsLong();
        userProfileReviewService.approveReview(reviewId, reviewerId);
        markAvatarResourceApproved(review.getPendingAvatar());
        return Result.success();
    }

    @Operation(summary = "拒绝个人信息审核")
    @PutMapping("/{reviewId}/reject")
    @AuditLog(module = "个人信息审核", operation = "UPDATE", description = "拒绝个人信息审核")
    public Result<Void> reject(@PathVariable Long reviewId,
                               @Valid @RequestBody RejectRequest request) {
        UserProfileReview review = getPendingReview(reviewId);
        Long reviewerId = StpUtil.getLoginIdAsLong();
        userProfileReviewService.rejectReview(reviewId, reviewerId, request.reason.trim());
        markAvatarResourceRejected(review.getPendingAvatar());
        return Result.success();
    }

    private UserProfileReview getPendingReview(Long reviewId) {
        UserProfileReview review = userProfileReviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "审核记录不存在");
        }
        if (!PROFILE_REVIEW_PENDING.equals(review.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该记录不在待审核状态");
        }
        return review;
    }

    private void markAvatarResourceApproved(String avatarUrl) {
        OssResource resource = findPendingAvatarResource(avatarUrl);
        if (resource == null) {
            return;
        }

        ossResourceMapper.update(null, new LambdaUpdateWrapper<OssResource>()
                .eq(OssResource::getId, resource.getId())
                .set(OssResource::getFileName, toApprovedFileName(resource.getFileName())));
    }

    private void markAvatarResourceRejected(String avatarUrl) {
        OssResource resource = findPendingAvatarResource(avatarUrl);
        if (resource == null) {
            return;
        }

        ossResourceMapper.update(null, new LambdaUpdateWrapper<OssResource>()
                .eq(OssResource::getId, resource.getId())
                .set(OssResource::getFileName, toRejectedFileName(resource.getFileName())));
    }

    private OssResource findPendingAvatarResource(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return null;
        }
        return ossResourceMapper.selectOne(new LambdaQueryWrapper<OssResource>()
                .eq(OssResource::getUrl, avatarUrl)
                .eq(OssResource::getUsageType, AVATAR_USAGE)
                .likeRight(OssResource::getFileName, AVATAR_PENDING_FILE_PREFIX)
                .orderByDesc(OssResource::getCreateTime)
                .last("LIMIT 1"));
    }

    private String toApprovedFileName(String fileName) {
        if (fileName == null) {
            return "avatar.webp";
        }
        if (fileName.startsWith(AVATAR_PENDING_FILE_PREFIX)) {
            return fileName.substring(AVATAR_PENDING_FILE_PREFIX.length());
        }
        if (fileName.startsWith(AVATAR_REJECTED_FILE_PREFIX)) {
            return fileName.substring(AVATAR_REJECTED_FILE_PREFIX.length());
        }
        return fileName;
    }

    private String toRejectedFileName(String fileName) {
        return AVATAR_REJECTED_FILE_PREFIX + toApprovedFileName(fileName);
    }

    public static class RejectRequest {
        @NotBlank(message = "拒绝原因不能为空")
        @Size(max = 200, message = "拒绝原因不能超过200个字符")
        public String reason;
    }

    public static class ProfileReviewVO {
        public Long reviewId;
        public Long userId;
        public String email;

        public String currentNickname;
        public String currentBio;
        public String currentAvatar;

        public String pendingNickname;
        public String pendingBio;
        public String pendingAvatar;

        public LocalDateTime submitTime;
    }
}
