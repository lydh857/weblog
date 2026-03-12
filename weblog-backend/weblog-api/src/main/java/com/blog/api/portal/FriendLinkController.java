package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.result.Result;
import com.blog.content.entity.FriendLink;
import com.blog.content.service.FriendLinkService;
import com.blog.infra.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户端友链接口
 */
@Tag(name = "用户端-友链", description = "友链展示与申请")
@RestController
@RequestMapping("/api/friend-link")
@RequiredArgsConstructor
public class FriendLinkController {

    private final FriendLinkService friendLinkService;

    @Operation(summary = "获取有效友链列表")
    @GetMapping
    public Result<List<FriendLink>> listActive() {
        return Result.success(friendLinkService.listActive());
    }

    @Operation(summary = "申请友链")
    @PostMapping("/apply")
    @RateLimit(key = "friend-link-apply", capacity = 5, seconds = 300)
    public Result<FriendLink> applyLink(@RequestBody Map<String, String> body) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(friendLinkService.applyLink(
            userId,
            body.get("name"),
            body.get("url"),
            body.get("logo"),
            body.get("description")
        ));
    }

    @Operation(summary = "查询我的友链申请")
    @GetMapping("/my")
    public Result<FriendLink> getMyLink() {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(friendLinkService.getMyLink(userId));
    }

    @Operation(summary = "更新我的友链申请")
    @PutMapping("/my")
    @RateLimit(key = "friend-link-update", capacity = 10, seconds = 300)
    public Result<FriendLink> updateMyLink(@RequestBody Map<String, String> body) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(friendLinkService.updateMyLink(
            userId,
            body.get("name"),
            body.get("url"),
            body.get("logo"),
            body.get("description")
        ));
    }
}
