package com.blog.api.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * 全局 Jackson 时间格式配置
 * 统一 LocalDateTime 序列化/反序列化格式为 yyyy-MM-dd HH:mm:ss
 */
@Configuration
public class JacksonConfig {

  private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer jsonDateTimeCustomizer() {
    return builder -> builder
        .serializers(
            new LocalDateTimeSerializer(DATETIME_FMT),
            new LocalDateSerializer(DATE_FMT))
        .deserializers(
            new LocalDateTimeDeserializer(DATETIME_FMT),
            new LocalDateDeserializer(DATE_FMT));
  }
}
