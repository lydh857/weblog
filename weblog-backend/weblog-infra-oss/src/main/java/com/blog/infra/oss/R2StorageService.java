package com.blog.infra.oss;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Cloudflare R2 对象存储服务（S3 兼容）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "storage", name = "provider", havingValue = "r2")
@ConditionalOnBean(S3Client.class)
public class R2StorageService {

    private static final Map<String, byte[]> MAGIC_NUMBERS = Map.of(
            "jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
            "gif", new byte[]{0x47, 0x49, 0x46, 0x38},
            "webp", new byte[]{0x52, 0x49, 0x46, 0x46}
    );

    private static final long MAX_SIZE = 5 * 1024 * 1024;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final R2Properties r2Properties;

    public String upload(InputStream inputStream, String originalFilename, long fileSize) {
        UploadPayload payload = validateAndRead(inputStream, originalFilename, fileSize);
        String ext = getExtension(originalFilename).toLowerCase(Locale.ROOT);
        String objectKey = buildObjectKey(ext);
        putObject(objectKey, payload.bytes());
        log.info("R2 文件上传成功: {}", objectKey);
        return buildPublicUrl(objectKey);
    }

    public String uploadTemp(InputStream inputStream, String originalFilename, long fileSize) {
        UploadPayload payload = validateAndRead(inputStream, originalFilename, fileSize);
        String ext = getExtension(originalFilename).toLowerCase(Locale.ROOT);
        String objectKey = buildTempObjectKey(ext);
        putObject(objectKey, payload.bytes());
        log.info("R2 临时文件上传成功: {}", objectKey);
        return buildPublicUrl(objectKey);
    }

    public String moveToFormal(String tempObjectKey) {
        String ext = getExtension(tempObjectKey).toLowerCase(Locale.ROOT);
        String formalKey = buildObjectKey(ext);

        CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                .sourceBucket(r2Properties.getBucketName())
                .sourceKey(tempObjectKey)
                .destinationBucket(r2Properties.getBucketName())
                .destinationKey(formalKey)
                .build();
        s3Client.copyObject(copyRequest);
        delete(tempObjectKey);

        log.info("R2 文件迁移成功: {} -> {}", tempObjectKey, formalKey);
        return buildPublicUrl(formalKey);
    }

    public String extractObjectKey(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        String normalizedBase = normalizeBaseUrl(r2Properties.getPublicBaseUrl());
        if (normalizedBase != null && url.startsWith(normalizedBase + "/")) {
            return url.substring(normalizedBase.length() + 1);
        }

        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            if (path.startsWith("/" + r2Properties.getBucketName() + "/")) {
                return path.substring(r2Properties.getBucketName().length() + 2);
            }
        } catch (Exception e) {
            log.warn("URI 解析失败: {}", url, e);
        }
        return null;
    }

    public boolean isTempUrl(String url) {
        String key = extractObjectKey(url);
        return key != null && key.startsWith("temp/");
    }

    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(r2Properties.getBucketName())
                .key(objectKey)
                .build());
    }

    public List<String> listObjects(String prefix) {
        List<String> keys = new ArrayList<>();
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(r2Properties.getBucketName())
                .prefix(prefix)
                .build();
        s3Client.listObjectsV2Paginator(request).stream().forEach(page ->
                page.contents().forEach(item -> keys.add(item.key())));
        return keys;
    }

    public void deleteObjects(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }

        final int batchSize = 1000;
        for (int i = 0; i < objectKeys.size(); i += batchSize) {
            int end = Math.min(i + batchSize, objectKeys.size());
            List<ObjectIdentifier> identifiers = objectKeys.subList(i, end).stream()
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();
            Delete delete = Delete.builder().objects(identifiers).build();
            DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                    .bucket(r2Properties.getBucketName())
                    .delete(delete)
                    .build();
            s3Client.deleteObjects(request);
        }
        log.info("R2 批量删除完成: {} 个对象", objectKeys.size());
    }

    public Map<String, String> generateUploadPolicy(String ext) {
        String normalizedExt = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        if (!MAGIC_NUMBERS.containsKey(normalizedExt)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }

        String objectKey = buildObjectKey(normalizedExt);
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(r2Properties.getBucketName())
                .key(objectKey)
                .build();
        Duration signatureDuration = Duration.ofSeconds(r2Properties.getSignUrlExpireSeconds());
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(signatureDuration)
                .putObjectRequest(putRequest)
                .build();
        PresignedPutObjectRequest signedRequest = s3Presigner.presignPutObject(presignRequest);

        long expireAt = System.currentTimeMillis() + signatureDuration.toMillis();
        return Map.of(
                "uploadUrl", signedRequest.url().toString(),
                "objectKey", objectKey,
                "cdnUrl", buildPublicUrl(objectKey),
                "expireAt", String.valueOf(expireAt)
        );
    }

    public StorageFacade.VerifiedUploadResult verifyUploadedObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "objectKey 不能为空");
        }
        if (!objectKey.startsWith("images/")) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "非法对象路径");
        }

        String ext = getExtension(objectKey).toLowerCase(Locale.ROOT);
        if (!MAGIC_NUMBERS.containsKey(ext)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件扩展名不允许");
        }

        HeadObjectResponse metadata;
        try {
            metadata = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(r2Properties.getBucketName())
                    .key(objectKey)
                    .build());
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.NOT_FOUND, "上传对象不存在或已过期");
        }

        long contentLength = metadata.contentLength();
        if (contentLength <= 0 || contentLength > MAX_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE, "文件大小超出限制");
        }

        String contentType = metadata.contentType();
        if (contentType != null && !contentType.isBlank()
                && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件 MIME 类型不合法");
        }

        byte[] header;
        try {
            ResponseBytes<?> responseBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(r2Properties.getBucketName())
                    .key(objectKey)
                    .range("bytes=0-7")
                    .build());
            header = responseBytes.asByteArray();
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件内容校验失败");
        }

        if (header.length < 4 || !verifyMagicNumber(ext, header)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件内容与扩展名不匹配");
        }

        return new StorageFacade.VerifiedUploadResult(
                objectKey,
                contentLength,
                contentType,
                ext,
                buildPublicUrl(objectKey)
        );
    }

    public String buildPublicUrl(String objectKey) {
        String base = normalizeBaseUrl(r2Properties.getPublicBaseUrl());
        if (base == null) {
            throw new BusinessException(ResultCode.FAIL, "R2 public-base-url 未配置");
        }
        return base + "/" + objectKey;
    }

    private void putObject(String objectKey, byte[] bytes) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(r2Properties.getBucketName())
                .key(objectKey)
                .contentType("image/" + getExtension(objectKey).toLowerCase(Locale.ROOT))
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
    }

    private UploadPayload validateAndRead(InputStream inputStream, String originalFilename, long fileSize) {
        if (fileSize > MAX_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE);
        }

        String ext = getExtension(originalFilename).toLowerCase(Locale.ROOT);
        if (!MAGIC_NUMBERS.containsKey(ext)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }

        try {
            BufferedInputStream bis = inputStream instanceof BufferedInputStream
                    ? (BufferedInputStream) inputStream
                    : new BufferedInputStream(inputStream);
            byte[] data = readLimitedBytes(bis);
            if (data.length < 4) {
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件内容无效");
            }

            byte[] header = new byte[Math.min(8, data.length)];
            System.arraycopy(data, 0, header, 0, header.length);
            if (!verifyMagicNumber(ext, header)) {
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件内容与扩展名不匹配");
            }

            return new UploadPayload(data);
        } catch (IOException ex) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件读取失败");
        }
    }

    private byte[] readLimitedBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > MAX_SIZE) {
                throw new BusinessException(ResultCode.FILE_TOO_LARGE);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean verifyMagicNumber(String ext, byte[] header) {
        byte[] expected = MAGIC_NUMBERS.get(ext);
        if (expected == null || header.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (header[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private String buildObjectKey(String ext) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return "images/" + datePath + "/" + UUID.randomUUID() + "." + ext;
    }

    private String buildTempObjectKey(String ext) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return "temp/" + datePath + "/" + UUID.randomUUID() + "." + ext;
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            return null;
        }
        String normalized = rawBaseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件名无扩展名");
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private record UploadPayload(byte[] bytes) {
    }
}
