package com.blog.infra.security.xss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 配置：注册 XSS 反序列化器
 * 所有 JSON 请求中的 String 字段都会经过 XSS 清理
 */
@Configuration
public class XssJacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer xssCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("XssModule");
            module.addDeserializer(String.class, new XssJsonDeserializer());
            builder.modulesToInstall(module);
        };
    }
}
