package com.blog.api.admin;

import com.blog.api.service.DashboardService;
import com.blog.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仪表盘统计接口
 */
@Tag(name = "管理端-仪表盘", description = "仪表盘统计")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

  private final DashboardService dashboardService;

  /** 文章统计 */
  @Operation(summary = "文章统计")
  @GetMapping("/article-statistics")
  public Result<DashboardService.ArticleStatisticsVO> getArticleStatistics() {
    return Result.success(dashboardService.getArticleStatistics());
  }

  /** 访问量统计 */
  @Operation(summary = "访问量统计")
  @GetMapping("/pv-statistics")
  public Result<DashboardService.PvStatisticsVO> getPvStatistics() {
    return Result.success(dashboardService.getPvStatistics());
  }

  /** 用户统计 */
  @Operation(summary = "用户统计")
  @GetMapping("/user-statistics")
  public Result<DashboardService.UserStatisticsVO> getUserStatistics() {
    return Result.success(dashboardService.getUserStatistics());
  }

  /** 热门文章排行 Top 10 */
  @Operation(summary = "热门文章排行")
  @GetMapping("/hot-posts")
  public Result<List<DashboardService.HotPostVO>> getHotPosts() {
    return Result.success(dashboardService.getHotPosts());
  }

  /** 待处理事项 */
  @Operation(summary = "待处理事项")
  @GetMapping("/pending")
  public Result<DashboardService.PendingVO> getPending() {
    return Result.success(dashboardService.getPending());
  }

  /** 分类文章分布 */
  @Operation(summary = "分类文章分布")
  @GetMapping("/category-distribution")
  public Result<List<DashboardService.CategoryDistVO>> getCategoryDistribution() {
    return Result.success(dashboardService.getCategoryDistribution());
  }

  /** 评论统计 */
  @Operation(summary = "评论统计")
  @GetMapping("/comment-stats")
  public Result<DashboardService.CommentStatsVO> getCommentStats() {
    return Result.success(dashboardService.getCommentStats());
  }

  /** 批量查询仪表盘数据（一次请求获取所有数据，使用 Redis Pipeline） */
  @Operation(summary = "批量查询仪表盘", description = "一次请求获取所有仪表盘数据，减少网络往返")
  @GetMapping("/batch")
  public Result<Map<String, Object>> getBatchDashboard() {
    // 使用 ConcurrentHashMap 保证线程安全
    Map<String, Object> result = new ConcurrentHashMap<>();
    
    // 使用并行查询优化
    Thread articleThread = new Thread(() -> {
      result.put("articleStats", dashboardService.getArticleStatistics());
    });
    Thread pvThread = new Thread(() -> {
      result.put("pvStats", dashboardService.getPvStatistics());
    });
    Thread userThread = new Thread(() -> {
      result.put("userStats", dashboardService.getUserStatistics());
    });
    Thread hotPostsThread = new Thread(() -> {
      result.put("hotPosts", dashboardService.getHotPosts());
    });
    Thread pendingThread = new Thread(() -> {
      result.put("pending", dashboardService.getPending());
    });
    Thread categoryThread = new Thread(() -> {
      result.put("categoryDist", dashboardService.getCategoryDistribution());
    });
    Thread commentThread = new Thread(() -> {
      result.put("commentStats", dashboardService.getCommentStats());
    });

    // 启动所有线程
    articleThread.start();
    pvThread.start();
    userThread.start();
    hotPostsThread.start();
    pendingThread.start();
    categoryThread.start();
    commentThread.start();

    // 等待所有线程完成
    try {
      articleThread.join();
      pvThread.join();
      userThread.join();
      hotPostsThread.join();
      pendingThread.join();
      categoryThread.join();
      commentThread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    return Result.success(result);
  }
}
