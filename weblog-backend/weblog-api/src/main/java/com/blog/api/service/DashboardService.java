package com.blog.api.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.blog.content.entity.Advertisement;
import com.blog.content.entity.Category;
import com.blog.content.entity.FriendLink;
import com.blog.content.entity.Post;
import com.blog.content.mapper.AdvertisementMapper;
import com.blog.content.mapper.CategoryMapper;
import com.blog.content.mapper.FriendLinkMapper;
import com.blog.content.mapper.PostMapper;
import com.blog.interaction.entity.Comment;
import com.blog.interaction.entity.UserViewLog;
import com.blog.interaction.mapper.CommentMapper;
import com.blog.interaction.mapper.PostRankingMapper;
import com.blog.interaction.mapper.UserViewLogMapper;
import com.blog.system.entity.User;
import com.blog.system.entity.UserProfileReview;
import com.blog.system.mapper.UserMapper;
import com.blog.system.mapper.UserProfileReviewMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仪表盘统计服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class DashboardService {

  private final PostMapper postMapper;
  private final CategoryMapper categoryMapper;
  private final AdvertisementMapper advertisementMapper;
  private final CommentMapper commentMapper;
  private final PostRankingMapper postRankingMapper;
  private final UserViewLogMapper userViewLogMapper;
  private final UserMapper userMapper;
  private final UserProfileReviewMapper userProfileReviewMapper;
  private final FriendLinkMapper friendLinkMapper;
  private final JdbcTemplate jdbcTemplate;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  private static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("MM-dd");
  private static final String CACHE_PREFIX = "dashboard:";
  /** 缓存过期时间：5 分钟 */
  private static final long CACHE_TTL_SECONDS = 300;

  // ==================== 文章统计 ====================

  /** 获取文章统计数据 */
  public ArticleStatisticsVO getArticleStatistics() {
    ArticleStatisticsVO cached = getFromCache("articleStats", ArticleStatisticsVO.class);
    if (cached != null) return cached;

    LocalDate today = LocalDate.now();
    LocalDate yesterday = today.minusDays(1);
    LocalDate weekStart = today.with(DayOfWeek.MONDAY);
    LocalDate monthStart = today.withDayOfMonth(1);
    LocalDate lastMonthStart = today.minusMonths(1);
    LocalDate lastWeekStart = today.minusWeeks(1);

    // 总文章数（未删除、未禁用）
    long total = postMapper.selectCount(
        Wrappers.<Post>lambdaQuery().eq(Post::getIsDisabled, false));

    // 查询最近一个月的文章（按 createTime）
    List<Post> recentPosts = postMapper.selectList(
        Wrappers.<Post>lambdaQuery()
            .select(Post::getCreateTime)
            .ge(Post::getCreateTime, lastMonthStart.atStartOfDay())
            .le(Post::getCreateTime, today.plusDays(1).atStartOfDay()));

    Map<LocalDate, Long> dateMap = recentPosts.stream()
        .collect(Collectors.groupingBy(p -> p.getCreateTime().toLocalDate(), Collectors.counting()));

    long todayCount = dateMap.getOrDefault(today, 0L);
    long yesterdayCount = dateMap.getOrDefault(yesterday, 0L);
    long weekCount = sumRange(dateMap, weekStart, today);
    long monthCount = sumRange(dateMap, monthStart, today);

    // 趋势数据
    TrendData trend = buildTrend(dateMap, today, lastMonthStart, lastWeekStart);

    ArticleStatisticsVO vo = new ArticleStatisticsVO();
    vo.setTotal(total);
    vo.setToday(todayCount);
    vo.setYesterday(yesterdayCount);
    vo.setWeek(weekCount);
    vo.setMonth(monthCount);
    vo.setMonthLabels(trend.monthLabels);
    vo.setMonthData(trend.monthData);
    vo.setWeekLabels(trend.weekLabels);
    vo.setWeekData(trend.weekData);
    putToCache("articleStats", vo);
    return vo;
  }

  // ==================== 访问量统计 ====================

  /** 获取访问量统计数据 */
  public PvStatisticsVO getPvStatistics() {
    PvStatisticsVO cached = getFromCache("pvStats", PvStatisticsVO.class);
    if (cached != null) return cached;

    LocalDate today = LocalDate.now();
    LocalDate yesterday = today.minusDays(1);
    LocalDate weekStart = today.with(DayOfWeek.MONDAY);
    LocalDate monthStart = today.withDayOfMonth(1);
    LocalDate lastMonthStart = today.minusMonths(1);
    LocalDate lastWeekStart = today.minusWeeks(1);

    // 总浏览量（SQL 聚合，避免全表加载到内存）
    Long totalPvResult = jdbcTemplate.queryForObject(
        "SELECT COALESCE(SUM(view_count), 0) FROM t_post WHERE is_deleted = 0", Long.class);
    long totalPv = totalPvResult != null ? totalPvResult : 0;

    // 从 t_user_view_log 按日期聚合 PV
    List<UserViewLog> logs = userViewLogMapper.selectList(
        Wrappers.<UserViewLog>lambdaQuery()
            .select(UserViewLog::getViewDate, UserViewLog::getViewCount)
            .ge(UserViewLog::getViewDate, lastMonthStart)
            .le(UserViewLog::getViewDate, today));

    Map<LocalDate, Long> dateMap = new HashMap<>();
    for (UserViewLog log : logs) {
      dateMap.merge(log.getViewDate(), (long) (log.getViewCount() != null ? log.getViewCount() : 0), Long::sum);
    }

    long todayPv = dateMap.getOrDefault(today, 0L);
    long yesterdayPv = dateMap.getOrDefault(yesterday, 0L);
    long weekPv = sumRange(dateMap, weekStart, today);
    long monthPv = sumRange(dateMap, monthStart, today);

    TrendData trend = buildTrend(dateMap, today, lastMonthStart, lastWeekStart);

    PvStatisticsVO vo = new PvStatisticsVO();
    vo.setTotal(totalPv);
    vo.setToday(todayPv);
    vo.setYesterday(yesterdayPv);
    vo.setWeek(weekPv);
    vo.setMonth(monthPv);
    vo.setMonthLabels(trend.monthLabels);
    vo.setMonthData(trend.monthData);
    vo.setWeekLabels(trend.weekLabels);
    vo.setWeekData(trend.weekData);
    putToCache("pvStats", vo);
    return vo;
  }

  // ==================== 用户统计 ====================

  /** 获取用户统计数据 */
  public UserStatisticsVO getUserStatistics() {
    UserStatisticsVO cached = getFromCache("userStats", UserStatisticsVO.class);
    if (cached != null) return cached;

    LocalDate today = LocalDate.now();
    LocalDate yesterday = today.minusDays(1);
    LocalDate weekStart = today.with(DayOfWeek.MONDAY);
    LocalDate monthStart = today.withDayOfMonth(1);
    LocalDate lastMonthStart = today.minusMonths(1);
    LocalDate lastWeekStart = today.minusWeeks(1);

    // 总用户数
    long total = userMapper.selectCount(null);

    // 查询最近一个月注册的用户
    List<User> recentUsers = userMapper.selectList(
        Wrappers.<User>lambdaQuery()
            .select(User::getCreateTime)
            .ge(User::getCreateTime, lastMonthStart.atStartOfDay())
            .le(User::getCreateTime, today.plusDays(1).atStartOfDay()));

    Map<LocalDate, Long> dateMap = recentUsers.stream()
        .collect(Collectors.groupingBy(u -> u.getCreateTime().toLocalDate(), Collectors.counting()));

    long todayCount = dateMap.getOrDefault(today, 0L);
    long yesterdayCount = dateMap.getOrDefault(yesterday, 0L);
    long weekCount = sumRange(dateMap, weekStart, today);
    long monthCount = sumRange(dateMap, monthStart, today);

    TrendData trend = buildTrend(dateMap, today, lastMonthStart, lastWeekStart);

    UserStatisticsVO vo = new UserStatisticsVO();
    vo.setTotal(total);
    vo.setToday(todayCount);
    vo.setYesterday(yesterdayCount);
    vo.setWeek(weekCount);
    vo.setMonth(monthCount);
    vo.setMonthLabels(trend.monthLabels);
    vo.setMonthData(trend.monthData);
    vo.setWeekLabels(trend.weekLabels);
    vo.setWeekData(trend.weekData);
    putToCache("userStats", vo);
    return vo;
  }

  // ==================== 工具方法 ====================

  /** 计算日期范围内的总和 */
  private long sumRange(Map<LocalDate, Long> map, LocalDate from, LocalDate to) {
    long sum = 0;
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      sum += map.getOrDefault(d, 0L);
    }
    return sum;
  }

  /** 构建周/月趋势数据 */
  private TrendData buildTrend(Map<LocalDate, Long> map, LocalDate today,
                               LocalDate lastMonthStart, LocalDate lastWeekStart) {
    TrendData t = new TrendData();
    t.monthLabels = new ArrayList<>();
    t.monthData = new ArrayList<>();
    t.weekLabels = new ArrayList<>();
    t.weekData = new ArrayList<>();

    for (LocalDate d = lastMonthStart; !d.isAfter(today); d = d.plusDays(1)) {
      String label = d.format(MM_DD);
      long count = map.getOrDefault(d, 0L);
      t.monthLabels.add(label);
      t.monthData.add(count);
      if (!d.isBefore(lastWeekStart)) {
        t.weekLabels.add(label);
        t.weekData.add(count);
      }
    }
    return t;
  }

  /** 趋势数据内部类 */
  private static class TrendData {
    List<String> monthLabels;
    List<Long> monthData;
    List<String> weekLabels;
    List<Long> weekData;
  }

  // ==================== 热门文章排行 ====================

  /** 获取热门文章 Top 10（总榜） */
  @SuppressWarnings("unchecked")
  public List<HotPostVO> getHotPosts() {
    List<HotPostVO> cached = getFromCache("hotPosts", List.class);
    if (cached != null) return cached;

    List<Map<String, Object>> rows = postRankingMapper.selectRankingWithPost(4, null, null, 10, 0);
    List<HotPostVO> result = rows.stream().map(row -> {
      HotPostVO vo = new HotPostVO();
      vo.setRank(((Number) row.get("rank_num")).intValue());
      vo.setPostId(((Number) row.get("post_id")).longValue());
      vo.setTitle((String) row.get("title"));
      vo.setCategoryName((String) row.get("category_name"));
      vo.setSubCategoryName((String) row.get("sub_category_name"));
      vo.setTagNames((String) row.get("tag_names"));
      vo.setViewCount(row.get("view_count") != null ? ((Number) row.get("view_count")).intValue() : 0);
      vo.setLikeCount(row.get("like_count") != null ? ((Number) row.get("like_count")).intValue() : 0);
      vo.setCollectCount(row.get("collect_count") != null ? ((Number) row.get("collect_count")).intValue() : 0);
      vo.setCommentCount(row.get("comment_count") != null ? ((Number) row.get("comment_count")).intValue() : 0);
      vo.setScore(((Number) row.get("score")).intValue());
      return vo;
    }).collect(Collectors.toList());
    putToCache("hotPosts", result);
    return result;
  }

  // ==================== 待处理事项 ====================

  /** 获取待处理事项统计 */
  public PendingVO getPending() {
    PendingVO cached = getFromCache("pending", PendingVO.class);
    if (cached != null) return cached;

    // 草稿文章数
    long draftPosts = postMapper.selectCount(
        Wrappers.<Post>lambdaQuery().eq(Post::getStatus, "draft"));
    // 待审核评论数
    long pendingComments = commentMapper.selectCount(
        Wrappers.<Comment>lambdaQuery().eq(Comment::getStatus, "pending"));
    // 待审核广告数
    long pendingAds = advertisementMapper.selectCount(
        Wrappers.<Advertisement>lambdaQuery().eq(Advertisement::getStatus, "pending"));
    // 待审核个人信息
    long pendingProfileReviews = userProfileReviewMapper.selectCount(
        Wrappers.<UserProfileReview>lambdaQuery().eq(UserProfileReview::getStatus, "pending"));
    // 待审核友链
    long pendingFriendLinks = friendLinkMapper.selectCount(
        Wrappers.<FriendLink>lambdaQuery().eq(FriendLink::getStatus, "pending"));

    PendingVO vo = new PendingVO();
    vo.setDraftPosts(draftPosts);
    vo.setPendingComments(pendingComments);
    vo.setPendingAds(pendingAds);
    vo.setPendingProfileReviews(pendingProfileReviews);
    vo.setPendingFriendLinks(pendingFriendLinks);
    putToCache("pending", vo);
    return vo;
  }

  // ==================== 分类文章分布 ====================

  /** 获取各分类的文章数量 */
  @SuppressWarnings("unchecked")
  public List<CategoryDistVO> getCategoryDistribution() {
    List<CategoryDistVO> cached = getFromCache("categoryDist", List.class);
    if (cached != null) return cached;

    // SQL 聚合，避免全表加载到内存
    // Category 使用 BaseEntityNoDelete（物理删除），无 is_deleted 列
    String sql = """
        SELECT c.name, COUNT(p.id) AS cnt
        FROM t_category c
        LEFT JOIN t_post p ON c.id = p.category_id AND p.is_deleted = 0 AND p.is_disabled = 0
        GROUP BY c.id, c.name
        HAVING cnt > 0
        ORDER BY cnt DESC
        """;
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
    List<CategoryDistVO> result = rows.stream().map(row -> {
      CategoryDistVO vo = new CategoryDistVO();
      vo.setName((String) row.get("name"));
      vo.setCount(((Number) row.get("cnt")).longValue());
      return vo;
    }).collect(Collectors.toList());
    putToCache("categoryDist", result);
    return result;
  }

  // ==================== 评论统计 ====================

  /** 获取评论统计 */
  public CommentStatsVO getCommentStats() {
    CommentStatsVO cached = getFromCache("commentStats", CommentStatsVO.class);
    if (cached != null) return cached;

    long total = commentMapper.selectCount(null);
    long pending = commentMapper.selectCount(
        Wrappers.<Comment>lambdaQuery().eq(Comment::getStatus, "pending"));
    long approved = commentMapper.selectCount(
        Wrappers.<Comment>lambdaQuery().eq(Comment::getStatus, "approved"));

    // 今日新增评论
    LocalDate today = LocalDate.now();
    long todayNew = commentMapper.selectCount(
        Wrappers.<Comment>lambdaQuery()
            .ge(Comment::getCreateTime, today.atStartOfDay())
            .le(Comment::getCreateTime, today.plusDays(1).atStartOfDay()));

    CommentStatsVO vo = new CommentStatsVO();
    vo.setTotal(total);
    vo.setPending(pending);
    vo.setApproved(approved);
    vo.setTodayNew(todayNew);
    putToCache("commentStats", vo);
    return vo;
  }

  // ==================== 缓存工具 ====================

  /** 从缓存读取，命中则返回，否则返回 null */
  private <T> T getFromCache(String key, Class<T> clazz) {
    try {
      String json = redisTemplate.opsForValue().get(CACHE_PREFIX + key);
      if (json != null) {
        return objectMapper.readValue(json, clazz);
      }
    } catch (Exception e) {
      log.warn("读取仪表盘缓存失败：key={}", key, e);
    }
    return null;
  }

  /** 批量从缓存读取，使用 Pipeline 减少网络往返（7 次请求 → 1 次） */
  private Map<String, String> getMultipleFromCache(List<String> keys) {
    try {
      List<String> fullKeys = keys.stream().map(k -> CACHE_PREFIX + k).toList();
      
      List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
        for (byte[] keyBytes : fullKeys.stream().map(String::getBytes).toList()) {
          connection.get(keyBytes);
        }
        return null;
      });
      
      Map<String, String> resultMap = new HashMap<>();
      for (int i = 0; i < keys.size(); i++) {
        if (results.get(i) != null) {
          resultMap.put(keys.get(i), (String) results.get(i));
        }
      }
      return resultMap;
    } catch (Exception e) {
      log.warn("批量读取仪表盘缓存失败", e);
      return new HashMap<>();
    }
  }

  /** 批量从缓存读取并反序列化 */
  private <T> Map<String, T> getMultipleFromCacheTyped(List<String> keys, Class<T> clazz) {
    Map<String, String> rawResults = getMultipleFromCache(keys);
    Map<String, T> results = new HashMap<>();
    for (Map.Entry<String, String> entry : rawResults.entrySet()) {
      try {
        results.put(entry.getKey(), objectMapper.readValue(entry.getValue(), clazz));
      } catch (Exception e) {
        log.warn("反序列化缓存失败：key={}", entry.getKey(), e);
      }
    }
    return results;
  }

  /** 写入缓存 */
  private <T> void putToCache(String key, T value) {
    try {
      String json = objectMapper.writeValueAsString(value);
      redisTemplate.opsForValue().set(CACHE_PREFIX + key, json, CACHE_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    } catch (JsonProcessingException e) {
      log.warn("写入仪表盘缓存失败: key={}", key, e);
    }
  }

  // ==================== VO 定义 ====================

  @Data
  public static class ArticleStatisticsVO {
    private long total;
    private long today;
    private long yesterday;
    private long week;
    private long month;
    private List<String> monthLabels;
    private List<Long> monthData;
    private List<String> weekLabels;
    private List<Long> weekData;
  }

  @Data
  public static class PvStatisticsVO {
    private long total;
    private long today;
    private long yesterday;
    private long week;
    private long month;
    private List<String> monthLabels;
    private List<Long> monthData;
    private List<String> weekLabels;
    private List<Long> weekData;
  }

  @Data
  public static class UserStatisticsVO {
    private long total;
    private long today;
    private long yesterday;
    private long week;
    private long month;
    private List<String> monthLabels;
    private List<Long> monthData;
    private List<String> weekLabels;
    private List<Long> weekData;
  }

  @Data
  public static class HotPostVO {
    private int rank;
    private long postId;
    private String title;
    private String categoryName;
    private String subCategoryName;
    private String tagNames;
    private int viewCount;
    private int likeCount;
    private int collectCount;
    private int commentCount;
    private int score;
  }

  @Data
  public static class PendingVO {
    private long draftPosts;
    private long pendingComments;
    private long pendingAds;
    private long pendingProfileReviews;
    private long pendingFriendLinks;
  }

  @Data
  public static class CategoryDistVO {
    private String name;
    private long count;
  }

  @Data
  public static class CommentStatsVO {
    private long total;
    private long pending;
    private long approved;
    private long todayNew;
  }
}
