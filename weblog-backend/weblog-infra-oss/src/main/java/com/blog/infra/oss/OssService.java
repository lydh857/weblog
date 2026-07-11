package com.blog.infra.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OSS 文件操作服务
 */
@Slf4j
@Service
@ConditionalOnBean(OSS.class)
public class OssService {

    /** 允许的图片类型及对应魔数 */
    private static final Map<String, byte[]> MAGIC_NUMBERS = Map.of(
            "jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
            "gif", new byte[]{0x47, 0x49, 0x46, 0x38},
            "webp", new byte[]{0x52, 0x49, 0x46, 0x46}
    );

    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB

    @Autowired
    private OSS ossClient;

    @Autowired
    private OssProperties ossProperties;

    /**
     * 上传文件到 OSS
     * @param inputStream 文件流
     * @param originalFilename 原始文件名
     * @param fileSize 文件大小
     * @return CDN 访问路径
     */
    public String upload(InputStream inputStream, String originalFilename, long fileSize) {
        // 1. 验证大小
        if (fileSize > MAX_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE);
        }

        // 2. 验证扩展名
        String ext = getExtension(originalFilename).toLowerCase();
        if (!ImageFileTypeDetector.isAllowedExtension(ext)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }

        // 3. 魔数验证（读取文件头字节，防止伪装扩展名）
        byte[] header;
        java.io.BufferedInputStream bis = new java.io.BufferedInputStream(inputStream);
        try {
            bis.mark(12);
            header = new byte[12];
            int read = bis.read(header);
            if (read < 4) {
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件内容无效");
            }
            bis.reset();
        } catch (java.io.IOException e) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件读取失败");
        }
        String detectedExt = ImageFileTypeDetector.detectExtension(header);
        if (detectedExt == null) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件内容与扩展名不匹配");
        }

        // 4. 生成安全路径：images/yyyy/MM/uuid.ext
        String objectKey = buildObjectKey(detectedExt);

        // 5. 上传
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(fileSize);
        ossClient.putObject(ossProperties.getBucketName(), objectKey, bis, metadata);

        log.info("文件上传成功: {}", objectKey);
        return ossProperties.getCdnDomain() + "/" + objectKey;
    }

    /**
     * 生成前端直传签名（Post Policy）
     * 前端拿到签名后直接上传到 OSS，不经过后端
     */
    public Map<String, String> generateUploadPolicy(String ext) {
        if (!MAGIC_NUMBERS.containsKey(ext.toLowerCase())) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        String objectKey = buildObjectKey(ext.toLowerCase());
        Date expiration = new Date(System.currentTimeMillis()
                + ossProperties.getSignUrlExpireSeconds() * 1000);

        // 生成签名 URL（PUT 方式直传）
        URL signedUrl = ossClient.generatePresignedUrl(
                ossProperties.getBucketName(), objectKey, expiration,
                com.aliyun.oss.HttpMethod.PUT);

        Map<String, String> result = new java.util.HashMap<>();
        result.put("uploadUrl", signedUrl.toString());
        result.put("objectKey", objectKey);
        result.put("cdnUrl", ossProperties.getCdnDomain() + "/" + objectKey);
        result.put("expireAt", String.valueOf(expiration.getTime()));
        return result;
    }

    /**
     * 校验前端直传对象（扩展名、大小、MIME、魔数）
     */
    public VerifiedUploadResult verifyUploadedObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "objectKey 不能为空");
        }
        if (!objectKey.startsWith("images/")) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "非法对象路径");
        }

        String ext = getExtension(objectKey).toLowerCase();
        if (!MAGIC_NUMBERS.containsKey(ext)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件扩展名不允许");
        }

        ObjectMetadata metadata;
        try {
            metadata = ossClient.getObjectMetadata(ossProperties.getBucketName(), objectKey);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.NOT_FOUND, "上传对象不存在或已过期");
        }

        long contentLength = metadata.getContentLength();
        if (contentLength <= 0 || contentLength > MAX_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE, "文件大小超出限制");
        }

        String contentType = metadata.getContentType();
        if (contentType != null && !contentType.isBlank()
                && !contentType.toLowerCase().startsWith("image/")) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件 MIME 类型不合法");
        }

        byte[] header = new byte[12];
        try (OSSObject object = ossClient.getObject(ossProperties.getBucketName(), objectKey);
             InputStream in = object.getObjectContent()) {
            int read = in.read(header);
            String detectedExt = ImageFileTypeDetector.detectExtension(header);
            if (read < 4 || !ImageFileTypeDetector.isCompatibleWithDeclaredExtension(detectedExt, ext)) {
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件内容与扩展名不匹配");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件内容校验失败");
        }

        return new VerifiedUploadResult(
                objectKey,
                contentLength,
                contentType,
                ext,
                ossProperties.getCdnDomain() + "/" + objectKey
        );
    }

    /**
     * 生成临时签名 URL（私有 Bucket 访问）
     */
    public String generateSignedUrl(String objectKey) {
        Date expiration = new Date(System.currentTimeMillis()
                + ossProperties.getSignUrlExpireSeconds() * 1000);
        URL url = ossClient.generatePresignedUrl(
                ossProperties.getBucketName(), objectKey, expiration);
        return url.toString();
    }

    /**
     * 删除 OSS 文件
     */
    public void delete(String objectKey) {
        ossClient.deleteObject(ossProperties.getBucketName(), objectKey);
        log.info("文件删除成功: {}", objectKey);
    }

    /**
     * 生成带图片处理参数的 CDN URL
     * @param objectKey OSS 对象 key
     * @param width     目标宽度（0 表示不限制）
     * @param height    目标高度（0 表示不限制）
     * @param quality   质量（1-100）
     * @param format    目标格式（webp/jpg/png，null 表示不转换）
     */
    public String getProcessedUrl(String objectKey, int width, int height,
                                  int quality, String format) {
        StringBuilder process = new StringBuilder("?x-oss-process=image");
        if (width > 0 || height > 0) {
            process.append("/resize");
            if (width > 0) process.append(",w_").append(width);
            if (height > 0) process.append(",h_").append(height);
            process.append(",m_lfit"); // 等比缩放
        }
        if (quality > 0 && quality < 100) {
            process.append("/quality,q_").append(quality);
        }
        if (format != null && !format.isEmpty()) {
            process.append("/format,").append(format);
        }
        return ossProperties.getCdnDomain() + "/" + objectKey + process;
    }

    /**
     * 获取缩略图 URL（宽度 400px，WebP 格式，质量 80）
     */
    public String getThumbnailUrl(String objectKey) {
        return getProcessedUrl(objectKey, 400, 0, 80, "webp");
    }

    private String buildObjectKey(String ext) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return "images/" + datePath + "/" + UUID.randomUUID() + "." + ext;
    }

    private String buildTempObjectKey(String ext) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return "temp/" + datePath + "/" + UUID.randomUUID() + "." + ext;
    }

    /**
     * 上传到临时目录（编辑时使用，发布时再迁移到正式目录）
     */
    public String uploadTemp(InputStream inputStream, String originalFilename, long fileSize) {
        if (fileSize > MAX_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE);
        }
        String ext = getExtension(originalFilename).toLowerCase();
        if (!ImageFileTypeDetector.isAllowedExtension(ext)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        byte[] header;
        java.io.BufferedInputStream bis = new java.io.BufferedInputStream(inputStream);
        try {
            bis.mark(12);
            header = new byte[12];
            int read = bis.read(header);
            if (read < 4) {
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件内容无效");
            }
            bis.reset();
        } catch (java.io.IOException e) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件读取失败");
        }
        String detectedExt = ImageFileTypeDetector.detectExtension(header);
        if (detectedExt == null) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件内容与扩展名不匹配");
        }
        String objectKey = buildTempObjectKey(detectedExt);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(fileSize);
        ossClient.putObject(ossProperties.getBucketName(), objectKey, bis, metadata);
        log.info("临时文件上传成功: {}", objectKey);
        return ossProperties.getCdnDomain() + "/" + objectKey;
    }

    /**
     * 将临时文件迁移到正式目录
     * @param tempObjectKey 临时文件的 object key（如 temp/2026/02/xxx.jpg）
     * @return 正式路径的 CDN URL
     */
    public String moveToFormal(String tempObjectKey) {
        String ext = getExtension(tempObjectKey).toLowerCase();
        String formalKey = buildObjectKey(ext);
        ossClient.copyObject(ossProperties.getBucketName(), tempObjectKey,
                ossProperties.getBucketName(), formalKey);
        ossClient.deleteObject(ossProperties.getBucketName(), tempObjectKey);
        log.info("文件迁移: {} -> {}", tempObjectKey, formalKey);
        return ossProperties.getCdnDomain() + "/" + formalKey;
    }

    /**
     * 从 CDN URL 提取 object key
     */
    public String extractObjectKey(String cdnUrl) {
        if (cdnUrl == null) return null;
        String cdnDomain = ossProperties.getCdnDomain();
        if (cdnUrl.startsWith(cdnDomain)) {
            return cdnUrl.substring(cdnDomain.length() + 1); // +1 去掉 /
        }
        return null;
    }

    /**
     * 判断是否为临时文件 URL
     */
    public boolean isTempUrl(String cdnUrl) {
        String key = extractObjectKey(cdnUrl);
        return key != null && key.startsWith("temp/");
    }

    /**
     * 列出指定前缀下的所有对象 key（用于清理）
     */
    public List<String> listObjects(String prefix) {
        var listing = ossClient.listObjects(ossProperties.getBucketName(), prefix);
        return listing.getObjectSummaries().stream()
                .map(OSSObjectSummary::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 批量删除对象
     */
    public void deleteObjects(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) return;
        ossClient.deleteObjects(new DeleteObjectsRequest(ossProperties.getBucketName())
                .withKeys(objectKeys));
        log.info("批量删除 {} 个临时文件", objectKeys.size());
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件名无扩展名");
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    /**
     * 验证文件头魔数是否与声明的扩展名匹配
     */
    private boolean verifyMagicNumber(String ext, byte[] header) {
        byte[] expected = MAGIC_NUMBERS.get(ext);
        if (expected == null) return false;
        if (header.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if (header[i] != expected[i]) return false;
        }
        return true;
    }

    public record VerifiedUploadResult(
            String objectKey,
            long fileSize,
            String mimeType,
            String fileType,
            String url
    ) {
    }
}
