package com.blog.content.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 图片 URL 提取工具
 * 从 Markdown 文本中提取 ![alt](url) 和 <img src="url"> 格式的图片 URL
 */
public final class MarkdownImageExtractor {

  private MarkdownImageExtractor() {}

  // Markdown 图片语法：![alt](url)
  private static final Pattern MD_IMAGE_PATTERN =
      Pattern.compile("!\\[.*?]\\((.*?)\\)");

  // HTML img 标签：<img src="url"> 或 <img src='url'>
  private static final Pattern HTML_IMG_PATTERN =
      Pattern.compile("<img\\s+[^>]*src=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE);

  /**
   * 从 Markdown 文本中提取所有图片 URL
   *
   * @param markdown Markdown 原始内容，可为 null
   * @return 去重后的 URL 集合，内容为 null 或空时返回空集合
   */
  public static Set<String> extractImageUrls(String markdown) {
    if (markdown == null || markdown.isEmpty()) {
      return Set.of();
    }

    Set<String> urls = new HashSet<>();
    extractByPattern(markdown, MD_IMAGE_PATTERN, urls);
    extractByPattern(markdown, HTML_IMG_PATTERN, urls);
    return urls;
  }

  /**
   * 使用指定正则从文本中提取第一个捕获组的值
   */
  private static void extractByPattern(String text, Pattern pattern, Set<String> result) {
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      String url = matcher.group(1);
      if (url != null && !url.isEmpty()) {
        result.add(url);
      }
    }
  }
}
