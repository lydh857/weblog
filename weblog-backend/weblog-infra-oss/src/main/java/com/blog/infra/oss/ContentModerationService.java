package com.blog.infra.oss;

import com.aliyun.oss.OSS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
 * 阿里云内容安全（绿网）服务
 * 用于检测上传图片是否包含违规内容（涉黄/暴恐/广告等）
 *
 * 需要在 application.yml 中配置 oss.content-moderation-enabled=true 启用
 * 使用与 OSS 相同的 AccessKey（需要开通内容安全服务）
 */
@Slf4j
@Service
@ConditionalOnBean(OSS.class)
@ConditionalOnProperty(prefix = "oss", name = "content-moderation-enabled", havingValue = "true")
public class ContentModerationService {

    private static final String GREEN_API_ENDPOINT = "https://green.cn-shanghai.aliyuncs.com";

    @Autowired
    private OssProperties ossProperties;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 异步检测图片内容安全
     * 检测不通过时记录日志（后续可扩展为自动删除或标记）
     *
     * @param imageUrl 图片的公网可访问 URL
     * @param resourceId 资源 ID（用于日志追踪）
     */
    @Async
    public void checkImage(String imageUrl, Long resourceId) {
        try {
            String body = buildImageScanBody(imageUrl);
            Map<String, String> headers = buildHeaders("/green/image/scan", body);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(GREEN_API_ENDPOINT + "/green/image/scan"))
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                if (responseBody.contains("\"suggestion\":\"block\"")) {
                    log.warn("图片内容安全检测不通过: resourceId={}, url={}", resourceId, imageUrl);
                    // TODO: 可扩展为自动删除或标记资源
                } else {
                    log.debug("图片内容安全检测通过: resourceId={}", resourceId);
                }
            } else {
                log.warn("内容安全 API 调用失败: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("内容安全检测异常: resourceId={}, error={}", resourceId, e.getMessage());
        }
    }

    private String buildImageScanBody(String imageUrl) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode scenes = root.putArray("scenes");
            scenes.add("porn");
            scenes.add("terrorism");
            scenes.add("ad");
            ArrayNode tasks = root.putArray("tasks");
            ObjectNode task = tasks.addObject();
            task.put("dataId", UUID.randomUUID().toString());
            task.put("url", imageUrl);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("构建内容审核请求体失败", e);
        }
    }

    private Map<String, String> buildHeaders(String path, String body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = sdf.format(new Date());
        headers.put("Date", date);
        headers.put("x-acs-version", "2018-05-09");
        headers.put("x-acs-signature-nonce", UUID.randomUUID().toString());
        headers.put("x-acs-signature-version", "1.0");
        headers.put("x-acs-signature-method", "HMAC-SHA1");

        // 签名
        try {
            String stringToSign = "POST\napplication/json\n\napplication/json\n" + date + "\n"
                    + "x-acs-signature-method:HMAC-SHA1\n"
                    + "x-acs-signature-nonce:" + headers.get("x-acs-signature-nonce") + "\n"
                    + "x-acs-signature-version:1.0\n"
                    + "x-acs-version:2018-05-09\n"
                    + path;

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(
                    ossProperties.getAccessKeySecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA1"));
            String signature = Base64.getEncoder().encodeToString(
                    mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));

            headers.put("Authorization", "acs " + ossProperties.getAccessKeyId() + ":" + signature);
        } catch (Exception e) {
            log.error("绿网 API 签名失败", e);
        }

        return headers;
    }
}
