package com.blog.infra.oss;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 本地文件存储服务（OSS 未启用时的 fallback）
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalFileService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Map<String, byte[]> MAGIC_NUMBERS = Map.of(
            "jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
            "gif", new byte[]{0x47, 0x49, 0x46, 0x38},
            "webp", new byte[]{0x52, 0x49, 0x46, 0x46}
    );
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    @Value("${blog.upload.local-dir:./uploads}")
    private String uploadDir;

    @Value("${blog.upload.base-url:http://localhost:9091/uploads}")
    private String baseUrl;

    public String upload(InputStream inputStream, String originalFilename, long fileSize) {
        InputStream safeInput = validateAndWrap(inputStream, originalFilename, fileSize);
        String objectKey = buildObjectKey(originalFilename);
        saveFile(safeInput, objectKey);
        return baseUrl + "/" + objectKey;
    }

    public String uploadTemp(InputStream inputStream, String originalFilename, long fileSize) {
        InputStream safeInput = validateAndWrap(inputStream, originalFilename, fileSize);
        String objectKey = buildTempObjectKey(originalFilename);
        saveFile(safeInput, objectKey);
        return baseUrl + "/" + objectKey;
    }

    public String moveToFormal(String tempObjectKey) {
        String ext = getExtension(tempObjectKey);
        String formalKey = buildObjectKey("file." + ext);
        Path src = Paths.get(uploadDir, tempObjectKey);
        Path dest = Paths.get(uploadDir, formalKey);
        try {
            Files.createDirectories(dest.getParent());
            Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("本地文件迁移: {} -> {}", tempObjectKey, formalKey);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FAIL, "文件迁移失败");
        }
        return baseUrl + "/" + formalKey;
    }

    public String extractObjectKey(String url) {
        if (url == null) return null;
        if (url.startsWith(baseUrl)) {
            return url.substring(baseUrl.length() + 1);
        }
        return null;
    }

    public boolean isTempUrl(String url) {
        String key = extractObjectKey(url);
        return key != null && key.startsWith("temp/");
    }

    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(Paths.get(uploadDir, objectKey));
            log.info("本地文件删除: {}", objectKey);
        } catch (IOException e) {
            log.warn("本地文件删除失败: {}", objectKey, e);
        }
    }

    public List<String> listObjects(String prefix) {
        Path dir = Paths.get(uploadDir, prefix);
        if (!Files.exists(dir)) return Collections.emptyList();
        try (Stream<Path> walk = Files.walk(dir)) {
            Path base = Paths.get(uploadDir);
            return walk.filter(Files::isRegularFile)
                    .map(p -> base.relativize(p).toString().replace('\\', '/'))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public void deleteObjects(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) return;
        for (String key : objectKeys) {
            delete(key);
        }
        log.info("批量删除 {} 个本地文件", objectKeys.size());
    }

    private InputStream validateAndWrap(InputStream inputStream, String filename, long fileSize) {
        if (fileSize > MAX_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE);
        }
        String ext = getExtension(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }

        try {
            java.io.BufferedInputStream bis = inputStream instanceof java.io.BufferedInputStream
                    ? (java.io.BufferedInputStream) inputStream
                    : new java.io.BufferedInputStream(inputStream);
            bis.mark(8);
            byte[] header = new byte[8];
            int read = bis.read(header);
            if (read < 4) {
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件内容无效");
            }
            bis.reset();
            if (!verifyMagicNumber(ext, header)) {
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件内容与扩展名不匹配");
            }
            return bis;
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件读取失败");
        }
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

    private void saveFile(InputStream inputStream, String objectKey) {
        Path path = Paths.get(uploadDir, objectKey);
        try {
            Files.createDirectories(path.getParent());
            Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
            log.info("本地文件上传成功: {}", objectKey);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FAIL, "文件保存失败: " + e.getMessage());
        }
    }

    private String buildObjectKey(String filename) {
        String ext = getExtension(filename).toLowerCase();
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return "images/" + datePath + "/" + UUID.randomUUID() + "." + ext;
    }

    private String buildTempObjectKey(String filename) {
        String ext = getExtension(filename).toLowerCase();
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return "temp/" + datePath + "/" + UUID.randomUUID() + "." + ext;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件名无扩展名");
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
