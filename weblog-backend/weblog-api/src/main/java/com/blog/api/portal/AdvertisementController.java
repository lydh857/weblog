package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.content.entity.Advertisement;
import com.blog.content.service.AdvertisementService;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.infra.security.util.XssUtil;
import com.blog.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户端广告接口
 */
@Tag(name = "用户端-广告", description = "广告查询、广告申请")
@RestController
@RequestMapping("/api/portal/advertisement")
@RequiredArgsConstructor
public class AdvertisementController {

    private final AdvertisementService advertisementService;
    private final SystemConfigService systemConfigService;

    @Operation(summary = "按位置获取有效广告")
    @GetMapping
    public Result<List<Advertisement>> getByPosition(@RequestParam String position) {
        return Result.success(advertisementService.getActiveByPosition(position));
    }

    @Operation(summary = "记录广告点击")
    @PostMapping("/{id}/click")
    @RateLimit(key = "ad-click", capacity = 120, seconds = 60)
    public Result<Void> recordClick(@PathVariable Long id) {
        advertisementService.recordClick(id);
        return Result.success();
    }

    @Operation(summary = "查询广告申请入口是否开放")
    @GetMapping("/apply-status")
    public Result<Map<String, Object>> getApplyStatus() {
        String val = systemConfigService.getValue("ad_apply_enabled");
        return Result.success(Map.of("enabled", "true".equals(val)));
    }

    @Operation(summary = "提交广告申请")
    @PostMapping("/apply")
    @RateLimit(key = "ad-apply", capacity = 5, seconds = 300)
    public Result<Advertisement> apply(@RequestBody Advertisement ad) {
        StpUtil.checkLogin();
        // 检查申请入口是否开放
        String applyEnabled = systemConfigService.getValue("ad_apply_enabled");
        if (!"true".equals(applyEnabled)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "广告申请入口暂未开放");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        // 用户端申请不允许设置时间，由管理员统一设置
        ad.setStartTime(null);
        ad.setEndTime(null);
        // 安全过滤：如果是code类型广告，清理HTML内容
        if ("code".equals(ad.getType()) && ad.getContent() != null) {
            ad.setContent(XssUtil.cleanContent(ad.getContent()));
        }
        return Result.success(advertisementService.submitApplication(userId, ad));
    }
}
