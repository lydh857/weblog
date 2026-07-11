package com.blog.infra.security.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XSS 清理工具类
 * 基于 JSoup 的白名单策略过滤 HTML，防止 XSS 攻击
 */
public final class XssUtil {

    private XssUtil() {}

    private static final Pattern MARKDOWN_BLOCKQUOTE_MARKER = Pattern.compile("(?m)^(\\s{0,3}(?:&gt;\\s*)+)");

    /**
     * 文章内容白名单（允许 Markdown 渲染后的常见 HTML 标签）
     */
    private static final Safelist CONTENT_SAFELIST = Safelist.relaxed()
            // 代码块
            .addTags("pre", "code", "span")
            .addAttributes("code", "class")
            .addAttributes("span", "class")
            .addAttributes("pre", "class")
            // 表格
            .addAttributes("th", "align")
            .addAttributes("td", "align")
            // 图片限制 src 协议
            .addProtocols("img", "src", "http", "https")
            // 链接限制协议（禁止 javascript:）
            .addProtocols("a", "href", "http", "https", "mailto")
            .addAttributes("a", "target", "rel")
            // 移除所有 on* 事件属性（JSoup 默认行为）
            ;

    /**
     * 纯文本白名单（评论、标题等，不允许任何 HTML 标签）
     */
    private static final Safelist TEXT_SAFELIST = Safelist.none();

    /**
     * Markdown 中内嵌 HTML 的结构化白名单
     */
    private static final Safelist MARKDOWN_HTML_SAFELIST = Safelist.relaxed()
            .addTags("pre", "code", "span", "details", "summary")
            .addAttributes("code", "class")
            .addAttributes("span", "class")
            .addAttributes("pre", "class")
            .addAttributes("a", "target", "rel", "title")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true);

    /**
     * 清理 Markdown 源码（最小化清理，保留原始格式）
     * 只去除 script/style 标签和 on* 事件属性，保留其他所有内容
     * 适用于文章内容等需要保留 Markdown 格式和内嵌 HTML 的场景
     *
     * @param markdown 原始 Markdown 内容
     * @return 清理后的 Markdown
     */
    public static String cleanMarkdown(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }

        String normalized = markdown.replace("\u0000", "").replace("\r\n", "\n").replace("\r", "\n");
        String newlineToken = "__MD_NL_" + java.util.UUID.randomUUID().toString().replace("-", "") + "__";
        String preserved = normalized.replace("\n", newlineToken);
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String cleaned = Jsoup.clean(preserved, "", MARKDOWN_HTML_SAFELIST, outputSettings);
        return restoreMarkdownBlockquoteMarkers(cleaned.replace(newlineToken, "\n"));
    }

    private static String restoreMarkdownBlockquoteMarkers(String markdown) {
        Matcher matcher = MARKDOWN_BLOCKQUOTE_MARKER.matcher(markdown);
        return matcher.replaceAll(match -> match.group().replace("&gt;", ">"));
    }

    /**
     * 清理文章内容（保留安全的 HTML 标签）
     *
     * @param html 原始 HTML 内容
     * @return 清理后的安全 HTML
     */
    public static String cleanContent(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        return Jsoup.clean(html, CONTENT_SAFELIST);
    }

    /**
     * 清理为纯文本（去除所有 HTML 标签，用于标题、评论等）
     *
     * @param input 原始输入
     * @return 纯文本
     */
    public static String cleanText(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return Jsoup.clean(input, TEXT_SAFELIST);
    }

    /**
     * 检测是否包含潜在 XSS 内容
     *
     * @param input 原始输入
     * @return true 表示包含危险内容
     */
    public static boolean containsXss(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        String cleaned = Jsoup.clean(input, TEXT_SAFELIST);
        return !input.equals(cleaned);
    }
}
