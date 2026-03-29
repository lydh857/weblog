package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.content.entity.FriendLink;
import com.blog.content.service.FriendLinkService;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户端友链接口
 */
@Tag(name = "用户端-友链", description = "友链展示与申请")
@RestController
@RequestMapping("/api/friend-link")
public class FriendLinkController {

    private static final String FRIEND_LINK_APPLY_SWITCH_KEY = "friend_link_apply_enabled";

    private final FriendLinkService friendLinkService;
    private final SystemConfigService systemConfigService;

    public FriendLinkController(FriendLinkService friendLinkService, SystemConfigService systemConfigService) {
        this.friendLinkService = friendLinkService;
        this.systemConfigService = systemConfigService;
    }

    @Operation(summary = "查询友链申请入口是否开放")
    @GetMapping("/apply-status")
    @RateLimit(key = "friend-link-apply-status", capacity = 60, seconds = 60)
    public Result<Map<String, Object>> getApplyStatus() {
        String val = systemConfigService.getValue(FRIEND_LINK_APPLY_SWITCH_KEY);
        return Result.success(Map.of("enabled", "true".equals(val)));
    }

    @Operation(summary = "获取有效友链列表")
    @GetMapping
    @RateLimit(key = "friend-link-list", capacity = 120, seconds = 60)
    public Result<List<FriendLink>> listActive() {
        return Result.success(friendLinkService.listActive());
    }

    @Operation(summary = "申请友链")
    @PostMapping("/apply")
    @RateLimit(key = "friend-link-apply", capacity = 5, seconds = 300)
    public Result<FriendLink> applyLink(@Valid @RequestBody FriendLinkApplyRequest body) {
        String enabled = systemConfigService.getValue(FRIEND_LINK_APPLY_SWITCH_KEY);
        if (!"true".equals(enabled)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "友链申请入口暂未开放");
        }
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(friendLinkService.applyLink(
            userId,
            body.getName(),
            body.getUrl(),
            body.getLogo(),
            body.getDescription()
        ));
    }

    @Operation(summary = "查询我的友链申请")
    @GetMapping("/my")
    @RateLimit(key = "friend-link-my", capacity = 60, seconds = 60)
    public Result<FriendLink> getMyLink() {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(friendLinkService.getMyLink(userId));
    }

    @Operation(summary = "更新我的友链申请")
    @PutMapping("/my")
    @RateLimit(key = "friend-link-update", capacity = 10, seconds = 300)
    public Result<FriendLink> updateMyLink(@Valid @RequestBody FriendLinkApplyRequest body) {
        String enabled = systemConfigService.getValue(FRIEND_LINK_APPLY_SWITCH_KEY);
        if (!"true".equals(enabled)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "友链申请入口暂未开放");
        }
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(friendLinkService.updateMyLink(
            userId,
            body.getName(),
            body.getUrl(),
            body.getLogo(),
            body.getDescription()
        ));
    }

    public static class FriendLinkApplyRequest {
        @NotBlank(message = "网站名称不能为空")
        @Size(max = 50, message = "网站名称最长50字")
        private String name;

        @NotBlank(message = "网站链接不能为空")
        @Size(max = 200, message = "网站链接最长200字")
        private String url;

        @NotBlank(message = "Logo URL不能为空")
        @Size(max = 500, message = "Logo URL最长500字")
        private String logo;

        @NotBlank(message = "网站描述不能为空")
        @Size(max = 200, message = "描述最长200字")
        private String description;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getLogo() {
            return logo;
        }

        public void setLogo(String logo) {
            this.logo = logo;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
