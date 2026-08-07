package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.KbStorageConfig;
import com.nopkg.hellodoc.entities.KbStorageFile;
import com.nopkg.hellodoc.enums.StorageProvider;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.repositories.KbStorageFileRepository;
import com.nopkg.hellodoc.storage.StorageClient;
import com.nopkg.hellodoc.storage.StorageClientFactory;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageFileService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StorageFileService.class);

    private final KbStorageFileRepository storageFileRepository;
    private final StorageConfigService storageConfigService;
    private final StorageClientFactory storageClientFactory;
    private final DbService dbService;

    @Value("${storage.url-expiration-seconds:3600}")
    private long urlExpirationSeconds;

    @Value("${storage.orphan.grace-hours:24}")
    private long orphanGraceHours;

    public KbStorageFile upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("storage.file_required", "Please select a file to upload"));
        }
        String hash;
        try (InputStream is = file.getInputStream()) {
            hash = sha256Hex(is);
        } catch (Exception e) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("storage.calc_hash_failed", "Failed to calculate file hash: ") + e.getMessage());
        }

        OffsetDateTime now = OffsetDateTime.now();
        return storageFileRepository.findFirstByContentHashOrderByIdAsc(hash)
                .map(existing -> ensureObjectExistsAndUpdateRefCount(existing, file))
                .orElseGet(() -> {
                    // Double check in case of concurrent upload before heavy storage operation
                    KbStorageFile doubleCheck = storageFileRepository.findFirstByContentHashOrderByIdAsc(hash)
                            .orElse(null);
                    if (doubleCheck != null) {
                        return ensureObjectExistsAndUpdateRefCount(doubleCheck, file);
                    }

                    KbStorageConfig config = storageConfigService.getDefault();
                    StorageClient client = storageClientFactory.create(config);
                    String key = generateKey(file.getOriginalFilename());
                    try (InputStream is = file.getInputStream()) {
                        client.upload(is, key, file.getContentType());
                    } catch (Exception e) {
                        throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("storage.upload_failed", "Upload file failed: ") + e.getMessage());
                    }
                    if (!client.exists(key)) {
                        throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("storage.verify_not_found", "Upload file failed: object does not exist after writing to storage"));
                    }

                    KbStorageFile created = new KbStorageFile();
                    created.setStorageConfig(config);
                    created.setStorageKey(key);
                    created.setContentHash(hash);
                    String contentType = file.getContentType();
                    if (contentType != null && contentType.length() > 255) {
                        contentType = contentType.substring(0, 255);
                    }
                    created.setFileType(contentType);
                    created.setFileSize(file.getSize());
                    created.setRefCount(1);
                    created.setCreatedAt(now);
                    created.setDeletedAt(null);
                    created.setAccessUrl(null);
                    created.setUrlExpiresAt(null);
                    try {
                        // Use a NEW transaction to isolate the potential constraint violation
                        return dbService.saveStorageFileInNewTransaction(created);
                    } catch (Exception e) {
                        // If it fails with ANY exception (likely constraint violation),
                        // the new transaction rolled back, but this main transaction is still clean.
                        return storageFileRepository.findFirstByContentHashOrderByIdAsc(hash)
                                .map(existing -> updateRefCount(existing.getId()))
                                .orElseThrow(() -> new BusinessException(ApiResponse.Code.SYSTEM_ERROR,
                                        com.nopkg.hellodoc.utils.MessageUtils.get("storage.concurrent_conflict", "Concurrent upload conflict resolution failed: ") + e.getMessage()));
                    }
                });
    }

    private KbStorageFile ensureObjectExistsAndUpdateRefCount(KbStorageFile existing, MultipartFile file) {
        if (existing == null || existing.getId() == null) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("storage.record_abnormal", "Storage file record is abnormal"));
        }
        try {
            StorageClient client = storageClientFactory.create(existing.getStorageConfig());
            String key = existing.getStorageKey();
            if (client.exists(key)) {
                return updateRefCount(existing.getId());
            }
            log.warn("存储对象缺失，尝试自愈重新上传: fileId={}, key={}", existing.getId(), key);
            try (InputStream is = file.getInputStream()) {
                client.upload(is, key, file.getContentType());
            }
            if (!client.exists(key)) {
                throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("storage.verify_not_found", "Upload file failed: object does not exist after writing to storage"));
            }
            return updateRefCount(existing.getId());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("storage.upload_failed", "Upload file failed: ") + e.getMessage());
        }
    }

    @Transactional
    public KbStorageFile updateRefCount(Long fileId) {
        KbStorageFile file = storageFileRepository.findByIdForUpdate(fileId)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("common.file_not_found", "File not found")));
        int current = file.getRefCount() == null ? 0 : file.getRefCount();
        file.setRefCount(Math.max(0, current) + 1);
        file.setDeletedAt(null);
        return storageFileRepository.save(file);
    }

    public String generateAccessUrl(Long fileId) {
        return generateAccessUrl(fileId, null);
    }

    public String generateAccessUrl(Long fileId, String filename) {
        KbStorageFile file = storageFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("common.file_not_found", "File not found")));

        StorageProvider provider = null;
        try {
            KbStorageConfig cfg = file.getStorageConfig();
            provider = (cfg == null) ? null : StorageProvider.fromCode(cfg.getProvider());
        } catch (Exception ignored) {
            provider = null;
        }

        OffsetDateTime now = OffsetDateTime.now();
        // 如果是外部存储且已有缓存 URL 且未过期，直接返回。
        // 注意：如果 filename 变化了，目前的缓存策略可能不会感知，但对于同一个 asset，filename 是固定的。
        if (provider != StorageProvider.LOCAL
                && file.getAccessUrl() != null && file.getUrlExpiresAt() != null
                && file.getUrlExpiresAt().isAfter(now.plusMinutes(5))) {
            return file.getAccessUrl();
        }

        StorageClient client = storageClientFactory.create(file.getStorageConfig());
        String url = client.generatePresignedUrl(file.getStorageKey(), Duration.ofSeconds(urlExpirationSeconds), filename);

        if (provider != StorageProvider.LOCAL) {
            file.setAccessUrl(url);
            file.setUrlExpiresAt(now.plusSeconds(urlExpirationSeconds));
            storageFileRepository.save(file);
        }
        return url;
    }

    @Transactional
    public void incrementRefCount(Long fileId) {
        KbStorageFile file = storageFileRepository.findByIdForUpdate(fileId)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("common.file_not_found", "File not found")));
        int current = file.getRefCount() == null ? 0 : file.getRefCount();
        file.setRefCount(Math.max(0, current) + 1);
        file.setDeletedAt(null);
        storageFileRepository.save(file);
    }

    @Transactional
    public void decrementRefCount(Long fileId) {
        KbStorageFile file = storageFileRepository.findByIdForUpdate(fileId)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("common.file_not_found", "File not found")));
        int current = file.getRefCount() == null ? 0 : file.getRefCount();
        int next = Math.max(0, current - 1);
        file.setRefCount(next);
        if (next == 0) {
            if (file.getDeletedAt() == null) {
                file.setDeletedAt(OffsetDateTime.now());
            }
        }
        storageFileRepository.save(file);
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOrphanFiles() {
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minusHours(orphanGraceHours);
        List<KbStorageFile> orphans = storageFileRepository.findByRefCountAndDeletedAtBefore(0, threshold);
        for (KbStorageFile file : orphans) {
            KbStorageFile locked = storageFileRepository.findByIdForUpdate(file.getId()).orElse(null);
            if (locked == null) {
                continue;
            }
            if ((locked.getRefCount() == null ? 0 : locked.getRefCount()) != 0) {
                continue;
            }
            if (locked.getDeletedAt() == null || locked.getDeletedAt().isAfter(threshold)) {
                continue;
            }
            try {
                storageClientFactory.create(locked.getStorageConfig()).delete(locked.getStorageKey());
                storageFileRepository.delete(locked);
            } catch (Exception ignored) {
            }
        }
    }

    private static String generateKey(String originalFilename) {
        java.time.LocalDate now = java.time.LocalDate.now();
        String uuid = UUID.randomUUID().toString();
        String ext = null;
        if (originalFilename != null) {
            int idx = originalFilename.lastIndexOf('.');
            if (idx >= 0 && idx < originalFilename.length() - 1) {
                ext = originalFilename.substring(idx + 1).trim();
            }
        }
        String base = String.format("/%d/%02d/%02d/%s",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), uuid);
        if (ext != null && !ext.isBlank()) {
            return base + "." + ext;
        }
        return base;
    }

    private static String sha256Hex(InputStream is) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            if (n > 0) {
                md.update(buf, 0, n);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
