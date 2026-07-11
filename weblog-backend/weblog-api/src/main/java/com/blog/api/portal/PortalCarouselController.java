package com.blog.api.portal;

import com.blog.common.result.Result;
import com.blog.content.dto.CarouselVO;
import com.blog.content.service.CarouselService;
import com.blog.infra.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端轮播接口
 */
@Tag(name = "用户端-轮播", description = "轮播配置查询")
@RestController
@RequestMapping("/api/portal/carousel")
@RequiredArgsConstructor
public class PortalCarouselController {

  private final CarouselService carouselService;

  @Operation(summary = "获取启用的轮播列表")
  @GetMapping
  @RateLimit(key = "portal-carousel-list", capacity = 120, seconds = 60)
  public Result<List<CarouselVO>> list() {
    return Result.success(carouselService.listPortal());
  }
}
