package com.blog.infra.ai.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI 自动配置类
 * <p>
 * 始终加载 AiProperties（管理端配置页面需要读写），
 * 始终扫描 com.blog.infra.ai 包下的组件。
 * <p>
 * Spring AI 的 ChatModel / EmbeddingModel 是否创建，
 * 由 {@link AiEnvironmentPostProcessor} 通过设置
 * spring.ai.model.chat=none 来控制。
 * <p>
 * AiClientService 内部通过 Optional 注入 ChatModel / EmbeddingModel，
 * 当 bean 不存在时方法调用会抛出 AI_DISABLED 异常。
 */
@AutoConfiguration
@EnableConfigurationProperties(AiProperties.class)
@ComponentScan(basePackages = "com.blog.infra.ai")
public class AiAutoConfiguration {
}
