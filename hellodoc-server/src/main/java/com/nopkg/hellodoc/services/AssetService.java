package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.KbAsset;
import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.entities.KbStorageFile;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.repositories.KbAssetRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.nopkg.hellodoc.storage.StorageClient;
import com.nopkg.hellodoc.storage.StorageClientFactory;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final KbAssetRepository assetRepository;
    private final KbDocumentRepository documentRepository;
    private final KbService kbService;
    private final PermissionChecker permissionChecker;
    private final StorageFileService storageFileService;
    private final StorageClientFactory storageClientFactory;

    @Transactional
    public KbAsset uploadAsset(Long kbId, Long docId, MultipartFile file, Long userId, String fileName,
            String description) {
        if (kbId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "kbId 不能为空");
        }
        if (userId == null) {
            throw new BusinessException(ApiResponse.Code.UNAUTHORIZED, "未登录");
        }
        permissionChecker.checkKbRole(userId, kbId, KbRole.EDITOR);

        KbKnowledgeBase kb = kbService.getKnowledgeBase(kbId);
        KbDocument doc = null;
        if (docId != null) {
            doc = documentRepository.findById(docId)
                    .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, "文档不存在"));
            if (!Objects.equals(doc.getKb().getId(), kbId)) {
                throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "文档不属于该知识库");
            }
            permissionChecker.checkDocRole(userId, docId, DocRole.EDITOR);
        }

        KbStorageFile storageFile = storageFileService.upload(file);

        KbAsset asset = new KbAsset();
        asset.setKb(kb);
        asset.setDoc(doc);
        asset.setStorageFile(storageFile);
        asset.setUploaderId(userId);
        asset.setFileName(normalizeFileName(fileName, file.getOriginalFilename()));
        asset.setDescription(normalize(description));
        asset.setCreatedAt(OffsetDateTime.now());
        asset.setDeletedAt(null);
        return assetRepository.save(asset);
    }

    public List<KbAsset> getDocumentAssets(Long docId, Long userId) {
        permissionChecker.checkDocRole(userId, docId, DocRole.VIEWER);
        return assetRepository.findByDoc_IdAndDeletedAtIsNullOrderByCreatedAtDesc(docId);
    }

    public List<KbAsset> getKbAssets(Long kbId, Long userId) {
        permissionChecker.checkKbRole(userId, kbId, KbRole.VIEWER);
        return assetRepository.findByKb_IdAndDeletedAtIsNullOrderByCreatedAtDesc(kbId);
    }

    public KbAsset getAsset(Long assetId, Long userId) {
        KbAsset asset = assetRepository.findByIdAndDeletedAtIsNull(assetId)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, "附件不存在"));
        if (asset.getDoc() != null) {
            permissionChecker.checkDocRole(userId, asset.getDoc().getId(), DocRole.VIEWER);
        } else {
            permissionChecker.checkKbRole(userId, asset.getKb().getId(), KbRole.VIEWER);
        }
        return asset;
    }

    public KbAsset getAssetPublic(Long assetId) {
        return assetRepository.findByIdAndDeletedAtIsNull(assetId)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, "附件不存在"));
    }

    public String getAssetUrl(Long assetId, Long userId) {
        KbAsset asset = getAsset(assetId, userId);
        return storageFileService.generateAccessUrl(asset.getStorageFile().getId(), asset.getFileName());
    }

    public String getAssetUrlPublic(Long assetId) {
        KbAsset asset = getAssetPublic(assetId);
        return storageFileService.generateAccessUrl(asset.getStorageFile().getId(), asset.getFileName());
    }

    public InputStream getAssetContent(Long assetId, Long userId) {
        KbAsset asset = getAsset(assetId, userId);
        StorageClient client = storageClientFactory.create(asset.getStorageFile().getStorageConfig());
        return client.download(asset.getStorageFile().getStorageKey());
    }

    public InputStream getAssetContentPublic(Long assetId) {
        KbAsset asset = assetRepository.findByIdAndDeletedAtIsNull(assetId)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, "附件不存在"));
        StorageClient client = storageClientFactory.create(asset.getStorageFile().getStorageConfig());
        return client.download(asset.getStorageFile().getStorageKey());
    }

    @Transactional
    public void deleteAsset(Long assetId, Long userId) {
        KbAsset asset = assetRepository.findByIdAndDeletedAtIsNull(assetId)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, "附件不存在"));
        permissionChecker.checkKbRole(userId, asset.getKb().getId(), KbRole.EDITOR);

        asset.setDeletedAt(OffsetDateTime.now());
        assetRepository.save(asset);
        storageFileService.decrementRefCount(asset.getStorageFile().getId());
    }

    private static String normalize(String s) {
        if (s == null) {
            return null;
        }
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }

    private static String normalizeFileName(String name, String fallback) {
        String v = normalize(name);
        if (v != null) {
            return v;
        }
        v = normalize(fallback);
        return v == null ? "file" : v;
    }
}
