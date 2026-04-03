package com.blog.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.PageParamUtil;
import com.blog.content.dto.CarouselCreateRequest;
import com.blog.content.dto.CarouselUpdateRequest;
import com.blog.content.dto.CarouselVO;
import com.blog.content.entity.Carousel;
import com.blog.content.entity.Post;
import com.blog.content.mapper.CarouselMapper;
import com.blog.content.mapper.PostMapper;
import com.blog.infra.security.util.XssUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 轮播配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarouselService {

  private final CarouselMapper carouselMapper;
  private final PostMapper postMapper;

  /**
   * 门户端查询：启用且在生效时间范围内的轮播项，按 sort_order DESC 排序
   * article 类型自动合并文章的 title/summary/coverImage/slug 作为默认值
   */
  public List<CarouselVO> listPortal() {
    LocalDateTime now = LocalDateTime.now();
    List<Carousel> list = carouselMapper.selectList(
      new LambdaQueryWrapper<Carousel>()
        .eq(Carousel::getIsEnabled, true)
        .and(w -> w
          .isNull(Carousel::getStartTime)
          .or()
          .le(Carousel::getStartTime, now))
        .and(w -> w
          .isNull(Carousel::getEndTime)
          .or()
          .ge(Carousel::getEndTime, now))
        .orderByDesc(Carousel::getSortOrder));

    // 批量查询关联文章，避免 N+1
    List<Long> articleIds = list.stream()
      .filter(c -> "article".equals(c.getType()) && c.getArticleId() != null)
      .map(Carousel::getArticleId)
      .distinct()
      .toList();
    Map<Long, Post> postMap = articleIds.isEmpty()
      ? Map.of()
      : postMapper.selectByIds(articleIds).stream()
          .collect(Collectors.toMap(Post::getId, p -> p));

    return list.stream().map(c -> toVO(c, postMap)).toList();
  }

  /**
   * 管理端分页查询（合并文章信息）
   */
  public IPage<CarouselVO> pageAdmin(int pageNum, int pageSize) {
    PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
    LambdaQueryWrapper<Carousel> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByDesc(Carousel::getSortOrder);
    IPage<Carousel> page = carouselMapper.selectPage(new Page<>(pageParams.pageNum(), pageParams.pageSize()), wrapper);

    // 批量查询关联文章
    List<Long> articleIds = page.getRecords().stream()
      .filter(c -> "article".equals(c.getType()) && c.getArticleId() != null)
      .map(Carousel::getArticleId)
      .distinct()
      .toList();
    Map<Long, Post> postMap = articleIds.isEmpty()
      ? Map.of()
      : postMapper.selectByIds(articleIds).stream()
          .collect(Collectors.toMap(Post::getId, p -> p));

    // 转换为 VO 并保留分页信息
    IPage<CarouselVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
    voPage.setRecords(page.getRecords().stream().map(c -> toVO(c, postMap)).toList());
    return voPage;
  }

  /**
   * 创建轮播配置
   */
  public Carousel create(CarouselCreateRequest request) {
    String safeLinkUrl = sanitizeLinkUrl(request.getLinkUrl());

    // image 类型校验
    if ("image".equals(request.getType())) {
      if (safeLinkUrl == null || safeLinkUrl.isBlank()) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "图片类型必须填写跳转链接");
      }
      if (request.getTitle() == null || request.getTitle().isBlank()) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "图片类型必须填写标题");
      }
      if (request.getImageUrl() == null || request.getImageUrl().isBlank()) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "图片类型必须上传背景图");
      }
    }
    // article 类型校验 articleId 有效性
    if ("article".equals(request.getType())) {
      if (request.getArticleId() == null) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "文章类型必须关联文章ID");
      }
      Post post = postMapper.selectById(request.getArticleId());
      if (post == null || !"published".equals(post.getStatus())) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "关联文章不存在或未发布");
      }
    }

    Carousel carousel = new Carousel();
    carousel.setType(request.getType());
    carousel.setTitle(request.getTitle());
    carousel.setDescription(request.getDescription());
    carousel.setImageUrl(request.getImageUrl());
    carousel.setLinkUrl(safeLinkUrl);
    carousel.setArticleId(request.getArticleId());
    carousel.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
    carousel.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true);
    carousel.setStartTime(request.getStartTime());
    carousel.setEndTime(request.getEndTime());
    carouselMapper.insert(carousel);
    return carousel;
  }

  /**
   * 更新轮播配置
   */
  public void update(Long id, CarouselUpdateRequest request) {
    Carousel existing = carouselMapper.selectById(id);
    if (existing == null) {
      throw new BusinessException(ResultCode.NOT_FOUND, "轮播配置不存在");
    }

    String effectiveLinkUrl = request.getLinkUrl() != null
      ? sanitizeLinkUrl(request.getLinkUrl())
      : existing.getLinkUrl();

    // 确定更新后的 type（如果请求中未指定则使用现有值）
    String effectiveType = request.getType() != null ? request.getType() : existing.getType();

    // image 类型校验 linkUrl
    if ("image".equals(effectiveType)) {
      if (effectiveLinkUrl == null || effectiveLinkUrl.isBlank()) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "图片类型必须填写跳转链接");
      }
    }
    // article 类型校验 articleId 有效性
    if ("article".equals(effectiveType)) {
      Long effectiveArticleId = request.getArticleId() != null ? request.getArticleId() : existing.getArticleId();
      if (effectiveArticleId == null) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "文章类型必须关联文章ID");
      }
      // 仅当 articleId 发生变化时才校验
      if (request.getArticleId() != null && !request.getArticleId().equals(existing.getArticleId())) {
        Post post = postMapper.selectById(request.getArticleId());
        if (post == null || !"published".equals(post.getStatus())) {
          throw new BusinessException(ResultCode.BAD_REQUEST, "关联文章不存在或未发布");
        }
      }
    }

    if (request.getType() != null) existing.setType(request.getType());
    if (request.getTitle() != null) existing.setTitle(request.getTitle());
    if (request.getDescription() != null) existing.setDescription(request.getDescription());
    if (request.getImageUrl() != null) existing.setImageUrl(request.getImageUrl());
    if (request.getLinkUrl() != null) existing.setLinkUrl(effectiveLinkUrl);
    if (request.getArticleId() != null) existing.setArticleId(request.getArticleId());
    if (request.getSortOrder() != null) existing.setSortOrder(request.getSortOrder());
    if (request.getIsEnabled() != null) existing.setIsEnabled(request.getIsEnabled());
    if (request.getStartTime() != null) existing.setStartTime(request.getStartTime());
    if (request.getEndTime() != null) existing.setEndTime(request.getEndTime());
    carouselMapper.updateById(existing);
  }

  /**
   * 软删除轮播配置
   */
  public void delete(Long id) {
    Carousel existing = carouselMapper.selectById(id);
    if (existing == null) {
      throw new BusinessException(ResultCode.NOT_FOUND, "轮播配置不存在");
    }
    carouselMapper.deleteById(id);
  }

  /**
   * 实体转 VO（合并文章默认信息）
   * article 类型：轮播自身未设置的字段从关联文章获取默认值
   */
  private CarouselVO toVO(Carousel carousel, Map<Long, Post> postMap) {
    CarouselVO vo = new CarouselVO();
    vo.setId(carousel.getId());
    vo.setType(carousel.getType());
    vo.setTitle(carousel.getTitle());
    vo.setDescription(carousel.getDescription());
    vo.setImageUrl(carousel.getImageUrl());
    vo.setLinkUrl(carousel.getLinkUrl());
    vo.setArticleId(carousel.getArticleId());
    vo.setSortOrder(carousel.getSortOrder());
    vo.setStartTime(carousel.getStartTime());
    vo.setEndTime(carousel.getEndTime());

    // article 类型：用文章信息填充未设置的字段
    if ("article".equals(carousel.getType()) && carousel.getArticleId() != null) {
      Post post = postMap.get(carousel.getArticleId());
      if (post != null) {
        vo.setSlug(post.getSlug());
        if (vo.getTitle() == null || vo.getTitle().isBlank()) {
          vo.setTitle(post.getTitle());
        }
        if (vo.getDescription() == null || vo.getDescription().isBlank()) {
          vo.setDescription(post.getSummary());
        }
        if (vo.getImageUrl() == null || vo.getImageUrl().isBlank()) {
          vo.setImageUrl(post.getCoverImage());
        }
      }
    }

    return vo;
  }

  private String sanitizeLinkUrl(String linkUrl) {
    if (linkUrl == null || linkUrl.isBlank()) {
      return null;
    }

    String trimmed = linkUrl.trim();
    if (trimmed.startsWith("/")) {
      return XssUtil.cleanText(trimmed);
    }

    URI uri;
    try {
      uri = URI.create(trimmed);
    } catch (Exception e) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接格式不正确");
    }

    String scheme = uri.getScheme();
    if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接仅支持 http/https 协议");
    }

    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接缺少有效主机名");
    }

    String normalizedHost = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
    if (!isPublicHost(normalizedHost)) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "不允许使用本地或内网跳转链接");
    }

    return XssUtil.cleanText(uri.toString());
  }

  private boolean isPublicHost(String host) {
    if ("localhost".equals(host) || host.endsWith(".localhost") || host.endsWith(".local")) {
      return false;
    }

    try {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      if (addresses.length == 0) {
        return false;
      }
      for (InetAddress address : addresses) {
        if (address.isAnyLocalAddress()
          || address.isLoopbackAddress()
          || address.isLinkLocalAddress()
          || address.isSiteLocalAddress()
          || address.isMulticastAddress()) {
          return false;
        }
        if (address instanceof Inet6Address inet6Address) {
          byte first = inet6Address.getAddress()[0];
          if ((first & (byte) 0xFE) == (byte) 0xFC) {
            return false;
          }
        }
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
