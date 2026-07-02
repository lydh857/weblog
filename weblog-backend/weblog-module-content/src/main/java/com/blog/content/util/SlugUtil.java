package com.blog.content.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/**
 * Slug 生成工具 - 支持中文转拼音
 */
public final class SlugUtil {

    private SlugUtil() {}

    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    /**
     * 根据标题生成 SEO 友好的 slug
     * 中文会转为拼音，英文保留，特殊字符转为连字符
     */
    public static String generate(String title) {
        if (title == null || title.isBlank()) {
            return "item-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }

        StringBuilder sb = new StringBuilder();
        for (char c : title.trim().toCharArray()) {
            if (Character.toString(c).matches("[\\u4e00-\\u9fa5]")) {
                // 中文字符转拼音
                try {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        sb.append(pinyinArray[0]).append('-');
                    }
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    // 忽略
                }
            } else if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append('-');
            }
        }

        String slug = sb.toString()
                .replaceAll("-{2,}", "-")   // 多个连字符合并
                .replaceAll("^-|-$", "");   // 去掉首尾连字符

        if (slug.isEmpty()) {
            slug = "item-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }

        // 限制长度
        if (slug.length() > 80) {
            slug = slug.substring(0, 80).replaceAll("-$", "");
        }

        return slug;
    }

    /**
     * 清理用户手动输入的 slug，只保留小写字母、数字和连字符
     */
    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return "item-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        String slug = input.trim().toLowerCase()
                .replaceAll("[^a-z0-9\\-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (slug.isEmpty()) {
            slug = "item-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        if (slug.length() > 80) {
            slug = slug.substring(0, 80).replaceAll("-$", "");
        }
        return slug;
    }
}
