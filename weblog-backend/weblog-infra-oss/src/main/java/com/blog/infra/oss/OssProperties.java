package com.blog.infra.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置属性
 * 所有密钥从环境变量注入，禁止硬编码
 */
@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    /** 内网 Endpoint（生产环境使用内网，节省流量费） */
    private String endpoint;

    /** AccessKey ID（环境变量注入） */
    private String accessKeyId;

    /** AccessKey Secret（环境变量注入） */
    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucketName;

    /** CDN 域名（用于拼接访问 URL） */
    private String cdnDomain;

    /** 签名 URL 有效期（秒），默认 5 分钟 */
    private long signUrlExpireSeconds = 300;

    /** 是否启用内容安全检测（阿里云绿网） */
    private boolean contentModerationEnabled = false;
}
