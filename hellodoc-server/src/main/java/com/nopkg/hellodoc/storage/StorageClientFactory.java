package com.nopkg.hellodoc.storage;

import com.nopkg.hellodoc.entities.KbStorageConfig;
import com.nopkg.hellodoc.enums.StorageProvider;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.services.StorageConfigService;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@RequiredArgsConstructor
public class StorageClientFactory {

    private final StorageConfigService storageConfigService;
    private final StorageUrlSigner storageUrlSigner;
    private final SecretCrypto secretCrypto;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public StorageClient getDefault() {
        return create(storageConfigService.getDefault());
    }

    public StorageClient create(KbStorageConfig config) {
        StorageProvider provider = StorageProvider.fromCode(config.getProvider());
        if (provider == StorageProvider.LOCAL) {
            Path root = Paths.get(uploadDir, "storage");
            try {
                Files.createDirectories(root);
            } catch (Exception e) {
                throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("legacy.storage.local_dir_create_failed", e.getMessage()));
            }
            return new LocalStorageClient(root, storageUrlSigner);
        }
        if (provider == StorageProvider.S3 || provider == StorageProvider.MINIO) {
            if (!StringUtils.hasText(config.getBucket())) {
                throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("legacy.field.required", "bucket"));
            }
            if (!StringUtils.hasText(config.getAccessKeyId())) {
                throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("legacy.field.required", "accessKeyId"));
            }
            if (!StringUtils.hasText(config.getSecretKeyEncrypted())) {
                throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("legacy.field.required", "secretKey"));
            }
            String secretKey = secretCrypto.decrypt(config.getSecretKeyEncrypted());
            if (!StringUtils.hasText(secretKey)) {
                secretKey = config.getSecretKeyEncrypted();
            }
            return new S3StorageClient(
                    config.getBucket(),
                    config.getRegion(),
                    config.getEndpoint(),
                    provider == StorageProvider.MINIO,
                    config.getAccessKeyId(),
                    secretKey
            );
        }
        throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("legacy.storage.unsupported_provider", provider.getCode()));
    }
}
