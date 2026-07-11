package com.blog.infra.oss;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 统一存储门面，屏蔽 local / aliyun-oss / r2 差异。
 */
@Service
public class StorageFacade {

    private final ObjectProvider<OssService> ossServiceProvider;
    private final ObjectProvider<LocalFileService> localFileServiceProvider;
    private final ObjectProvider<R2StorageService> r2StorageServiceProvider;

    @Value("${storage.provider:local}")
    private String storageProvider;

    public StorageFacade(ObjectProvider<OssService> ossServiceProvider,
                         ObjectProvider<LocalFileService> localFileServiceProvider,
                         ObjectProvider<R2StorageService> r2StorageServiceProvider) {
        this.ossServiceProvider = ossServiceProvider;
        this.localFileServiceProvider = localFileServiceProvider;
        this.r2StorageServiceProvider = r2StorageServiceProvider;
    }

    public boolean isStorageEnabled() {
        try {
            requireProvider();
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    public String upload(InputStream inputStream, String originalFilename, long fileSize) {
        Provider provider = requireProvider();
        return switch (provider) {
            case ALIYUN_OSS -> requireOssService().upload(inputStream, originalFilename, fileSize);
            case R2 -> requireR2Service().upload(inputStream, originalFilename, fileSize);
            case LOCAL -> requireLocalService().upload(inputStream, originalFilename, fileSize);
        };
    }

    public String uploadTemp(InputStream inputStream, String originalFilename, long fileSize) {
        Provider provider = requireProvider();
        return switch (provider) {
            case ALIYUN_OSS -> requireOssService().uploadTemp(inputStream, originalFilename, fileSize);
            case R2 -> requireR2Service().uploadTemp(inputStream, originalFilename, fileSize);
            case LOCAL -> requireLocalService().uploadTemp(inputStream, originalFilename, fileSize);
        };
    }

    public String moveToFormal(String tempObjectKey) {
        Provider provider = requireProvider();
        return switch (provider) {
            case ALIYUN_OSS -> requireOssService().moveToFormal(tempObjectKey);
            case R2 -> requireR2Service().moveToFormal(tempObjectKey);
            case LOCAL -> requireLocalService().moveToFormal(tempObjectKey);
        };
    }

    public String extractObjectKey(String url) {
        Provider provider = requireProvider();
        return switch (provider) {
            case ALIYUN_OSS -> requireOssService().extractObjectKey(url);
            case R2 -> requireR2Service().extractObjectKey(url);
            case LOCAL -> requireLocalService().extractObjectKey(url);
        };
    }

    public boolean isTempUrl(String url) {
        Provider provider = requireProvider();
        return switch (provider) {
            case ALIYUN_OSS -> requireOssService().isTempUrl(url);
            case R2 -> requireR2Service().isTempUrl(url);
            case LOCAL -> requireLocalService().isTempUrl(url);
        };
    }

    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        Provider provider = requireProvider();
        switch (provider) {
            case ALIYUN_OSS -> requireOssService().delete(objectKey);
            case R2 -> requireR2Service().delete(objectKey);
            case LOCAL -> requireLocalService().delete(objectKey);
        }
    }

    public List<String> listObjects(String prefix) {
        Provider provider = requireProvider();
        return switch (provider) {
            case ALIYUN_OSS -> requireOssService().listObjects(prefix);
            case R2 -> requireR2Service().listObjects(prefix);
            case LOCAL -> requireLocalService().listObjects(prefix);
        };
    }

    public void deleteObjects(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        Provider provider = requireProvider();
        switch (provider) {
            case ALIYUN_OSS -> requireOssService().deleteObjects(objectKeys);
            case R2 -> requireR2Service().deleteObjects(objectKeys);
            case LOCAL -> requireLocalService().deleteObjects(objectKeys);
        }
    }

    public boolean supportsDirectUpload() {
        Provider provider = requireProvider();
        return provider == Provider.ALIYUN_OSS || provider == Provider.R2;
    }

    public Map<String, String> generateUploadPolicy(String ext) {
        Provider provider = requireProvider();
        return switch (provider) {
            case ALIYUN_OSS -> requireOssService().generateUploadPolicy(ext);
            case R2 -> requireR2Service().generateUploadPolicy(ext);
            case LOCAL -> throw new BusinessException(ResultCode.FAIL, "当前存储后端不支持直传签名");
        };
    }

    public VerifiedUploadResult verifyUploadedObject(String objectKey) {
        Provider provider = requireProvider();
        return switch (provider) {
            case ALIYUN_OSS -> {
                OssService.VerifiedUploadResult result = requireOssService().verifyUploadedObject(objectKey);
                yield new VerifiedUploadResult(
                        result.objectKey(),
                        result.fileSize(),
                        result.mimeType(),
                        result.fileType(),
                        result.url());
            }
            case R2 -> requireR2Service().verifyUploadedObject(objectKey);
            case LOCAL -> throw new BusinessException(ResultCode.FAIL, "当前存储后端不支持直传校验");
        };
    }

    public boolean supportsImageProcessing() {
        return requireProvider() == Provider.ALIYUN_OSS;
    }

    public String getThumbnailUrl(String objectKey) {
        Provider provider = requireProvider();
        return switch (provider) {
            case ALIYUN_OSS -> requireOssService().getThumbnailUrl(objectKey);
            case R2 -> requireR2Service().buildPublicUrl(objectKey);
            case LOCAL -> null;
        };
    }

    private Provider requireProvider() {
        Provider provider = parseProvider();
        return switch (provider) {
            case ALIYUN_OSS -> {
                requireOssService();
                yield provider;
            }
            case R2 -> {
                requireR2Service();
                yield provider;
            }
            case LOCAL -> {
                requireLocalService();
                yield provider;
            }
        };
    }

    private Provider parseProvider() {
        String value = storageProvider == null ? "local" : storageProvider.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "local" -> Provider.LOCAL;
            case "aliyun-oss" -> Provider.ALIYUN_OSS;
            case "r2" -> Provider.R2;
            default -> throw new BusinessException(ResultCode.FAIL, "不支持的存储后端: " + value);
        };
    }

    private OssService requireOssService() {
        OssService service = ossServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(ResultCode.FAIL, "阿里云 OSS 存储未启用或配置缺失");
        }
        return service;
    }

    private LocalFileService requireLocalService() {
        LocalFileService service = localFileServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(ResultCode.FAIL, "本地存储未启用或配置缺失");
        }
        return service;
    }

    private R2StorageService requireR2Service() {
        R2StorageService service = r2StorageServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(ResultCode.FAIL, "R2 存储未启用或配置缺失");
        }
        return service;
    }

    private enum Provider {
        LOCAL,
        ALIYUN_OSS,
        R2
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
