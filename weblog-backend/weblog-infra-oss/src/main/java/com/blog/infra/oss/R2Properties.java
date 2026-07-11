package com.blog.infra.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cloudflare R2 配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "r2")
public class R2Properties {

    /**
     * S3 兼容 Endpoint，例如：https://<accountid>.r2.cloudflarestorage.com
     */
    private String endpoint;

    /**
     * Access Key ID
     */
    private String accessKeyId;

    /**
     * Secret Access Key
     */
    private String accessKeySecret;

    /**
     * Bucket 名称
     */
    private String bucketName;

    /**
     * 对外访问基础域名，例如：https://img.example.com
     */
    private String publicBaseUrl;

    /**
     * R2 统一 region 建议使用 auto
     */
    private String region = "auto";

    /**
     * 直传签名有效期（秒）
     */
    private long signUrlExpireSeconds = 300;
}
