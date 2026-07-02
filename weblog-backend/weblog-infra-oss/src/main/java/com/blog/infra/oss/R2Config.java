package com.blog.infra.oss;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.Locale;

/**
 * Cloudflare R2 S3 兼容客户端配置。
 */
@Configuration
@ConditionalOnProperty(prefix = "storage", name = "provider", havingValue = "r2")
public class R2Config {

    @Bean(destroyMethod = "close")
    public S3Client r2S3Client(R2Properties properties) {
        validateRequiredProperties(properties);
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());

        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(resolveRegion(properties.getRegion())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner r2S3Presigner(R2Properties properties) {
        validateRequiredProperties(properties);
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());

        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(resolveRegion(properties.getRegion())))
                .build();
    }

    private String resolveRegion(String region) {
        if (region == null || region.isBlank()) {
            return "auto";
        }
        return region.trim().toLowerCase(Locale.ROOT);
    }

    private void validateRequiredProperties(R2Properties properties) {
        if (properties.getEndpoint() == null || properties.getEndpoint().isBlank()) {
            throw new IllegalStateException("R2_ENDPOINT 未配置");
        }
        if (properties.getAccessKeyId() == null || properties.getAccessKeyId().isBlank()) {
            throw new IllegalStateException("R2_ACCESS_KEY_ID 未配置");
        }
        if (properties.getAccessKeySecret() == null || properties.getAccessKeySecret().isBlank()) {
            throw new IllegalStateException("R2_ACCESS_KEY_SECRET 未配置");
        }
        if (properties.getBucketName() == null || properties.getBucketName().isBlank()) {
            throw new IllegalStateException("R2_BUCKET_NAME 未配置");
        }
        if (properties.getPublicBaseUrl() == null || properties.getPublicBaseUrl().isBlank()) {
            throw new IllegalStateException("R2_PUBLIC_BASE_URL 未配置");
        }
    }
}
