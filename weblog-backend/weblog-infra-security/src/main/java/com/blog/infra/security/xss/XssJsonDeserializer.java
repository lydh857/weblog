package com.blog.infra.security.xss;

import com.blog.infra.security.util.XssUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * JSON 字符串反序列化时自动 XSS 清理
 * 处理 @RequestBody JSON 请求中的字符串字段
 */
public class XssJsonDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        return value == null ? null : XssUtil.cleanText(value);
    }
}
