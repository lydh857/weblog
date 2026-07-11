package com.blog.infra.security.xss;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * 跳过 XSS 清理的反序列化器
 * 用于文章内容等需要保留原始 Markdown/HTML 的字段
 * 在 Service 层手动调用 XssUtil.cleanContent() 做安全清理
 */
public class XssRawDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return p.getValueAsString();
    }
}
