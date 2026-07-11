package com.blog.infra.security.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XssUtilTest {

    @Test
    void shouldKeepMarkdownBlockquoteMarker() {
        String markdown = "> 警告内容\n\n正文";

        String cleaned = XssUtil.cleanMarkdown(markdown);

        assertEquals(markdown, cleaned);
    }

    @Test
    void shouldNotDecodeNonBlockquoteGreaterThanEntities() {
        String markdown = "正文 &gt; 示例";

        String cleaned = XssUtil.cleanMarkdown(markdown);

        assertEquals(markdown, cleaned);
    }
}
