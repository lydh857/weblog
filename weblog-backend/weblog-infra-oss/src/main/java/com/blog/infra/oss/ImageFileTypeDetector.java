package com.blog.infra.oss;

import java.util.Locale;
import java.util.Map;

final class ImageFileTypeDetector {

    private static final Map<String, byte[]> MAGIC_NUMBERS = Map.of(
            "jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
            "gif", new byte[]{0x47, 0x49, 0x46, 0x38},
            "webp", new byte[]{0x52, 0x49, 0x46, 0x46}
    );

    private ImageFileTypeDetector() {
    }

    static boolean isAllowedExtension(String ext) {
        return ext != null && MAGIC_NUMBERS.containsKey(ext.toLowerCase(Locale.ROOT));
    }

    static String detectExtension(byte[] header) {
        if (header == null || header.length < 4) {
            return null;
        }
        if (matches(header, MAGIC_NUMBERS.get("png"))) {
            return "png";
        }
        if (matches(header, MAGIC_NUMBERS.get("gif"))) {
            return "gif";
        }
        if (matches(header, MAGIC_NUMBERS.get("webp")) && header.length >= 12
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
            return "webp";
        }
        if (matches(header, MAGIC_NUMBERS.get("jpg"))) {
            return "jpg";
        }
        return null;
    }

    static boolean isCompatibleWithDeclaredExtension(String detectedExt, String declaredExt) {
        if (detectedExt == null || declaredExt == null) {
            return false;
        }
        String normalizedDetected = detectedExt.toLowerCase(Locale.ROOT);
        String normalizedDeclared = declaredExt.toLowerCase(Locale.ROOT);
        if ("jpeg".equals(normalizedDeclared)) {
            normalizedDeclared = "jpg";
        }
        return normalizedDetected.equals(normalizedDeclared);
    }

    private static boolean matches(byte[] header, byte[] expected) {
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
}
