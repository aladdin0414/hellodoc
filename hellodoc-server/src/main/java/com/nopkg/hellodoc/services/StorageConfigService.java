package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.KbStorageConfig;
import com.nopkg.hellodoc.enums.StorageProvider;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.repositories.KbStorageConfigRepository;
import com.nopkg.hellodoc.storage.SecretCrypto;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StorageConfigService {

    private final KbStorageConfigRepository storageConfigRepository;
    private final SecretCrypto secretCrypto;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public record StorageConfigUpdate(
            String name,
            String provider,
            String bucket,
            String region,
            String endpoint,
            String accessKeyId,
            String secretKey,
            String cdnDomain,
            Boolean isActive) {
    }

    @Transactional
    public KbStorageConfig create(StorageConfigUpdate dto) {
        if (!StringUtils.hasText(dto.name())) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("storage.name_required", "name cannot be empty"));
        }
        if (!StringUtils.hasText(dto.provider())) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("storage.provider_required", "provider cannot be empty"));
        }
        if (storageConfigRepository.existsByName(dto.name().trim())) {
            throw new BusinessException(ApiResponse.Code.USERNAME_CONFLICT, com.nopkg.hellodoc.utils.MessageUtils.get("storage.name_exists", "Storage configuration name already exists"));
        }

        StorageProvider provider = StorageProvider.fromCode(dto.provider().trim());
        OffsetDateTime now = OffsetDateTime.now();

        KbStorageConfig config = new KbStorageConfig();
        config.setName(dto.name().trim());
        config.setProvider(provider.getCode());
        config.setBucket(trimToNull(dto.bucket()));
        config.setRegion(trimToNull(dto.region()));
        config.setEndpoint(trimToNull(dto.endpoint()));
        config.setAccessKeyId(trimToNull(dto.accessKeyId()));
        config.setSecretKeyEncrypted(secretCrypto.encrypt(dto.secretKey()));
        config.setCdnDomain(trimToNull(dto.cdnDomain()));
        config.setIsActive(dto.isActive() == null ? Boolean.TRUE : dto.isActive());
        config.setCreatedAt(now);
        config.setUpdatedAt(now);

        boolean hasDefault = storageConfigRepository.findFirstByIsDefaultTrue().isPresent();
        config.setIsDefault(!hasDefault);

        return storageConfigRepository.save(config);
    }

    public List<KbStorageConfig> list() {
        return storageConfigRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional
    public KbStorageConfig update(Long id, StorageConfigUpdate dto) {
        KbStorageConfig existing = storageConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("storage.config_not_found", "Storage configuration not found")));

        if (StringUtils.hasText(dto.name())) {
            String name = dto.name().trim();
            if (!name.equals(existing.getName()) && storageConfigRepository.existsByName(name)) {
                throw new BusinessException(ApiResponse.Code.USERNAME_CONFLICT, com.nopkg.hellodoc.utils.MessageUtils.get("storage.name_exists", "Storage configuration name already exists"));
            }
            existing.setName(name);
        }

        if (StringUtils.hasText(dto.provider())) {
            StorageProvider provider = StorageProvider.fromCode(dto.provider().trim());
            existing.setProvider(provider.getCode());
        }

        if (dto.bucket() != null) {
            existing.setBucket(trimToNull(dto.bucket()));
        }
        if (dto.region() != null) {
            existing.setRegion(trimToNull(dto.region()));
        }
        if (dto.endpoint() != null) {
            existing.setEndpoint(trimToNull(dto.endpoint()));
        }
        if (dto.accessKeyId() != null) {
            existing.setAccessKeyId(trimToNull(dto.accessKeyId()));
        }
        if (dto.secretKey() != null) {
            existing.setSecretKeyEncrypted(secretCrypto.encrypt(dto.secretKey()));
        }
        if (dto.cdnDomain() != null) {
            existing.setCdnDomain(trimToNull(dto.cdnDomain()));
        }
        if (dto.isActive() != null) {
            existing.setIsActive(dto.isActive());
            if (!Boolean.TRUE.equals(dto.isActive()) && Boolean.TRUE.equals(existing.getIsDefault())) {
                throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("storage.cannot_disable_default", "Default storage configuration cannot be disabled"));
            }
        }

        existing.setUpdatedAt(OffsetDateTime.now());
        return storageConfigRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        KbStorageConfig existing = storageConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("storage.config_not_found", "Storage configuration not found")));
        if (Boolean.TRUE.equals(existing.getIsDefault())) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("storage.cannot_delete_default", "Default storage configuration cannot be deleted"));
        }
        storageConfigRepository.delete(existing);
    }

    @Transactional
    public KbStorageConfig getDefault() {
        return storageConfigRepository.findFirstByIsDefaultTrue()
                .orElseGet(() -> {
                    // If no default, try finding any active one and make it default
                    KbStorageConfig anyActive = storageConfigRepository.findByIsActiveTrueOrderByIdAsc()
                            .stream().findFirst().orElse(null);
                    if (anyActive != null) {
                        anyActive.setIsDefault(true);
                        anyActive.setUpdatedAt(OffsetDateTime.now());
                        return storageConfigRepository.save(anyActive);
                    }

                    // If still none, create a new "Default Local Storage" config
                    OffsetDateTime now = OffsetDateTime.now();
                    KbStorageConfig config = new KbStorageConfig();
                    config.setName(com.nopkg.hellodoc.utils.MessageUtils.get("storage.default_local_name", "Default Local Storage"));
                    config.setProvider(StorageProvider.LOCAL.getCode());
                    config.setIsActive(true);
                    config.setIsDefault(true);
                    config.setCreatedAt(now);
                    config.setUpdatedAt(now);
                    return storageConfigRepository.save(config);
                });
    }

    @Transactional
    public void setDefault(Long id) {
        KbStorageConfig target = storageConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("storage.config_not_found", "Storage configuration not found")));
        if (!Boolean.TRUE.equals(target.getIsActive())) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("storage.cannot_set_inactive_default", "Inactive storage configuration cannot be set as default"));
        }

        storageConfigRepository.findFirstByIsDefaultTrue().ifPresent(cfg -> {
            if (!cfg.getId().equals(target.getId())) {
                cfg.setIsDefault(false);
                cfg.setUpdatedAt(OffsetDateTime.now());
                storageConfigRepository.save(cfg);
            }
        });

        target.setIsDefault(true);
        target.setUpdatedAt(OffsetDateTime.now());
        storageConfigRepository.save(target);
    }

    public boolean testConnection(Long id) {
        KbStorageConfig cfg = storageConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("storage.config_not_found", "Storage configuration not found")));
        StorageProvider provider = StorageProvider.fromCode(cfg.getProvider());
        if (provider == StorageProvider.LOCAL) {
            Path root = Paths.get(uploadDir, "storage");
            try {
                Files.createDirectories(root);
                return Files.isWritable(root);
            } catch (Exception e) {
                return false;
            }
        }
        if (provider == StorageProvider.S3 || provider == StorageProvider.MINIO) {
            if (!StringUtils.hasText(cfg.getBucket())) {
                return false;
            }
            if (!StringUtils.hasText(cfg.getAccessKeyId())) {
                return false;
            }
            if (!StringUtils.hasText(cfg.getSecretKeyEncrypted())) {
                return false;
            }
            String secret = secretCrypto.decrypt(cfg.getSecretKeyEncrypted());
            if (!StringUtils.hasText(secret)) {
                secret = cfg.getSecretKeyEncrypted();
            }
            Region region = Region.of(StringUtils.hasText(cfg.getRegion()) ? cfg.getRegion().trim() : "us-east-1");
            var creds = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(cfg.getAccessKeyId().trim(), secret));
            var s3Config = S3Configuration.builder()
                    .pathStyleAccessEnabled(provider == StorageProvider.MINIO)
                    .build();
            var builder = S3Client.builder()
                    .region(region)
                    .credentialsProvider(creds)
                    .serviceConfiguration(s3Config);
            if (StringUtils.hasText(cfg.getEndpoint())) {
                builder = builder.endpointOverride(URI.create(cfg.getEndpoint().trim()));
            }
            try (S3Client s3 = builder.build()) {
                s3.headBucket(HeadBucketRequest.builder().bucket(cfg.getBucket().trim()).build());
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }
}
