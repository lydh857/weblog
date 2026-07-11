package com.blog.api.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.result.Result;
import com.blog.common.util.PageParamUtil;
import com.blog.content.dto.CarouselCreateRequest;
import com.blog.content.dto.CarouselUpdateRequest;
import com.blog.content.dto.CarouselVO;
import com.blog.content.entity.Carousel;
import com.blog.content.service.CarouselService;
import com.blog.infra.security.audit.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端 - 轮播配置管理
 */
@Tag(name = "管理端-轮播管理", description = "轮播配置的增删改查")
@RestController
@RequestMapping("/api/admin/carousel")
@RequiredArgsConstructor
public class AdminCarouselController {

  private final CarouselService carouselService;

  @Operation(summary = "轮播列表（分页）")
  @GetMapping
  public Result<Map<String, Object>> list(
    @RequestParam(defaultValue = "1") int pageNum,
    @RequestParam(defaultValue = "20") int pageSize) {
    PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
    IPage<CarouselVO> page = carouselService.pageAdmin(pageParams.pageNum(), pageParams.pageSize());
    return Result.success(Map.of(
      "records", page.getRecords(),
      "total", page.getTotal(),
      "current", page.getCurrent(),
      "pages", page.getPages()));
  }

  @Operation(summary = "创建轮播配置")
  @PostMapping
  @AuditLog(module = "轮播管理", operation = "CREATE", description = "创建轮播配置")
  public Result<Carousel> create(@Valid @RequestBody CarouselCreateRequest request) {
    return Result.success(carouselService.create(request));
  }

  @Operation(summary = "更新轮播配置")
  @PutMapping("/{id}")
  @AuditLog(module = "轮播管理", operation = "UPDATE", description = "更新轮播配置")
  public Result<Void> update(@PathVariable Long id, @RequestBody CarouselUpdateRequest request) {
    carouselService.update(id, request);
    return Result.success();
  }

  @Operation(summary = "删除轮播配置")
  @DeleteMapping("/{id}")
  @AuditLog(module = "轮播管理", operation = "DELETE", description = "删除轮播配置")
  public Result<Void> delete(@PathVariable Long id) {
    carouselService.delete(id);
    return Result.success();
  }
}
