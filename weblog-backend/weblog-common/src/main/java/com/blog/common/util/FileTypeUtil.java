package com.blog.common.util;

import org.springframework.util.MimeType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件类型工具
 * 使用魔数（文件签名）验证文件真实类型，防止文件伪装
 */
public class FileTypeUtil {

    // 允许的图片类型
    public static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp"
    );

    // 允许的文档类型
    public static final List<String> ALLOWED_DOCUMENT_TYPES = Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt"
    );

    // 允许的视频类型
    public static final List<String> ALLOWED_VIDEO_TYPES = Arrays.asList(
            "mp4", "webm", "ogg", "mov", "avi"
    );

    // 允许的音频类型
    public static final List<String> ALLOWED_AUDIO_TYPES = Arrays.asList(
            "mp3", "wav", "ogg", "aac", "flac"
    );

    // 允许的所有类型
    public static final Set<String> ALLOWED_ALL_TYPES = new java.util.HashSet<>();
    static {
        ALLOWED_ALL_TYPES.addAll(ALLOWED_IMAGE_TYPES);
        ALLOWED_ALL_TYPES.addAll(ALLOWED_DOCUMENT_TYPES);
        ALLOWED_ALL_TYPES.addAll(ALLOWED_VIDEO_TYPES);
        ALLOWED_ALL_TYPES.addAll(ALLOWED_AUDIO_TYPES);
    }

    // 魔数签名映射（文件头）
    private static final Map<String, byte[]> MAGIC_NUMBERS = new ConcurrentHashMap<>();
    static {
        // JPEG
        MAGIC_NUMBERS.put("jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        // PNG
        MAGIC_NUMBERS.put("png", new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47});
        // GIF
        MAGIC_NUMBERS.put("gif", new byte[]{(byte) 0x47, (byte) 0x49, (byte) 0x46});
        // PDF
        MAGIC_NUMBERS.put("pdf", new byte[]{(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46});
        // ZIP
        MAGIC_NUMBERS.put("zip", new byte[]{(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04});
    }

    /**
     * 验证文件扩展名是否允许
     */
    public static boolean isExtensionAllowed(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        return ALLOWED_ALL_TYPES.contains(extension.toLowerCase());
    }

    /**
     * 验证 MIME 类型是否允许
     */
    public static boolean isMimeTypeAllowed(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return false;
        }
        mimeType = mimeType.toLowerCase();
        return mimeType.startsWith("image/") ||
               mimeType.equals("application/pdf") ||
               mimeType.equals("application/msword") ||
               mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
               mimeType.equals("application/vnd.ms-excel") ||
               mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
               mimeType.startsWith("video/") ||
               mimeType.startsWith("audio/");
    }

    /**
     * 验证文件魔数（文件签名）
     * 确保文件内容与扩展名匹配
     */
    public static boolean isMagicNumberValid(byte[] content, String extension) {
        if (content == null || content.length < 4 || extension == null) {
            return false;
        }

        byte[] magic = MAGIC_NUMBERS.get(extension.toLowerCase());
        if (magic == null) {
            return true; // 没有定义魔数的类型，跳过验证
        }

        // 验证文件头
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从文件扩展名获取 MIME 类型
     */
    public static String getMimeType(String extension) {
        if (extension == null) {
            return "application/octet-stream";
        }
        extension = extension.toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "bmp" -> "image/bmp";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            default -> "application/octet-stream";
        };
    }
}
