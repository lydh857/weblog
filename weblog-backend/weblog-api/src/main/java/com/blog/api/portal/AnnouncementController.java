package com.blog.api.portal;

import com.blog.common.result.Result;
import com.blog.content.entity.Announcement;
import com.blog.content.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端公告接口
 */
@Tag(name = "用户端-公告", description = "公告查询")
@RestController
@RequestMapping("/api/portal/announcement")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @Operation(summary = "按类型获取有效公告")
    @GetMapping
    public Result<List<Announcement>> getByType(
            @RequestParam(required = false) String type) {
        if (type != null && !type.isBlank()) {
            return Result.success(announcementService.getActiveByType(type));
        }
        return Result.success(announcementService.getAllActive());
    }

    @Operation(summary = "获取公告详情")
    @GetMapping("/{id}")
    public Result<Announcement> getById(@PathVariable Long id) {
        return Result.success(announcementService.getActiveById(id));
    }
}
