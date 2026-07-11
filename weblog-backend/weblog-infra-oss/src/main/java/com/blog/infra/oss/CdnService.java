package com.blog.infra.oss;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.aliyun.oss.OSS;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 阿里云 CDN 缓存刷新服务
 * 文章更新/删除时刷新对应 CDN 缓存，确保用户看到最新内容
 */
@Slf4j
@Service
@ConditionalOnBean(OSS.class)
public class CdnService {

    @Autowired
    private OssProperties ossProperties;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * 异步刷新 CDN 缓存（URL 刷新）
     * @param urls 需要刷新的 URL 列表
     */
    @Async
    public void refreshUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) return;
        String cdnDomain = ossProperties.getCdnDomain();
        if (cdnDomain == null || cdnDomain.isEmpty()) {
            log.debug("CDN 域名未配置，跳过刷新");
            return;
        }

        String objectPath = String.join("\n", urls);
        try {
            Map<String, String> params = buildCommonParams("RefreshObjectCaches");
            params.put("ObjectPath", objectPath);
            params.put("ObjectType", "File");

            String signature = sign(params);
            params.put("Signature", signature);

            String queryString = buildQueryString(params);
            String requestUrl = "https://cdn.aliyuncs.com/?" + queryString;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("CDN 缓存刷新成功: {} 个 URL", urls.size());
            } else {
                log.warn("CDN 缓存刷新失败: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("CDN 缓存刷新异常: {}", e.getMessage());
        }
    }

    /**
     * 刷新文章相关的 CDN 缓存
     * @param postSlug 文章 slug
     */
    @Async
    public void refreshPost(String postSlug) {
        String cdnDomain = ossProperties.getCdnDomain();
        if (cdnDomain == null || cdnDomain.isEmpty()) return;

        List<String> urls = List.of(
                cdnDomain + "/post/" + postSlug,
                cdnDomain + "/"  // 首页也刷新
        );
        refreshUrls(urls);
    }

    /**
     * 刷新 CDN 目录缓存
     * @param dirPath 目录路径（如 /images/2026/02/）
     */
    @Async
    public void refreshDirectory(String dirPath) {
        String cdnDomain = ossProperties.getCdnDomain();
        if (cdnDomain == null || cdnDomain.isEmpty()) return;

        try {
            Map<String, String> params = buildCommonParams("RefreshObjectCaches");
            params.put("ObjectPath", cdnDomain + dirPath);
            params.put("ObjectType", "Directory");

            String signature = sign(params);
            params.put("Signature", signature);

            String queryString = buildQueryString(params);
            String requestUrl = "https://cdn.aliyuncs.com/?" + queryString;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("CDN 目录刷新成功: {}", dirPath);
            } else {
                log.warn("CDN 目录刷新失败: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("CDN 目录刷新异常: {}", e.getMessage());
        }
    }

    // ========== 阿里云 API 签名 ==========

    private Map<String, String> buildCommonParams(String action) {
        Map<String, String> params = new TreeMap<>();
        params.put("Action", action);
        params.put("AccessKeyId", ossProperties.getAccessKeyId());
        params.put("Format", "JSON");
        params.put("Version", "2018-05-10");
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureVersion", "1.0");
        params.put("SignatureNonce", UUID.randomUUID().toString());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        params.put("Timestamp", sdf.format(new Date()));

        return params;
    }

    private String sign(Map<String, String> params) throws Exception {
        StringBuilder canonicalized = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            if (canonicalized.length() > 0) canonicalized.append("&");
            canonicalized.append(percentEncode(entry.getKey()))
                    .append("=")
                    .append(percentEncode(entry.getValue()));
        }

        String stringToSign = "GET&" + percentEncode("/") + "&" + percentEncode(canonicalized.toString());

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(
                (ossProperties.getAccessKeySecret() + "&").getBytes(StandardCharsets.UTF_8),
                "HmacSHA1"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signData);
    }

    private String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(percentEncode(entry.getKey()))
                    .append("=")
                    .append(percentEncode(entry.getValue()));
        }
        return sb.toString();
    }

    private String percentEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
}
