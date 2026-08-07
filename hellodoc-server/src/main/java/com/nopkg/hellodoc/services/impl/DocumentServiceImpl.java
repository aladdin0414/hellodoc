package com.nopkg.hellodoc.services.impl;

import com.nopkg.hellodoc.audit.AuditAction;
import com.nopkg.hellodoc.audit.AuditTargetType;
import com.nopkg.hellodoc.audit.Auditable;
import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbDocumentContent;
import com.nopkg.hellodoc.entities.KbDocumentRevision;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.DocStatus;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.exceptions.ResourceNotFoundException;
import com.nopkg.hellodoc.repositories.KbDocRelationRepository;
import com.nopkg.hellodoc.repositories.KbDocumentContentRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.services.DocumentService;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.services.PermissionChecker;
import com.nopkg.hellodoc.services.RevisionService;
import com.nopkg.hellodoc.services.SearchService;
import com.nopkg.hellodoc.services.relation.DocumentSavedEvent;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.kb.DocCreateDTO;
import com.nopkg.hellodoc.web.dto.kb.DocUpdateDTO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final KbDocumentRepository documentRepository;
    private final KbDocumentContentRepository contentRepository;
    private final KbService kbService;
    private final PermissionChecker permissionChecker;
    private final RevisionService revisionService;
    private final SearchService searchService;
    private final ApplicationEventPublisher eventPublisher;
    private final KbDocRelationRepository docRelationRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    @Auditable(targetType = AuditTargetType.DOCUMENT, action = AuditAction.CREATE, targetIdExpression = "#result.id")
    public KbDocument create(Long userId, Long kbId, DocCreateDTO dto) {
        if (kbId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "kbId is required");
        }
        permissionChecker.checkKbRole(userId, java.util.Objects.requireNonNull(kbId),
                com.nopkg.hellodoc.enums.KbRole.EDITOR);
        KbKnowledgeBase kb = kbService.getKnowledgeBase(kbId);

        KbDocument doc = new KbDocument();
        doc.setKb(kb);
        doc.setName(dto.getName());
        doc.setType(dto.getType());
        doc.setSlug(dto.getSlug());
        doc.setPaperBgColor(dto.getPaperBgColor());
        if (dto.getExtraMeta() != null) {
            doc.setExtraMeta(new java.util.HashMap<>(dto.getExtraMeta()));
        }
        doc.setPaperBgImage(dto.getPaperBgImage());
        if (dto.getParentId() != null) {
            KbDocument parent = documentRepository.findByIdAndDeletedAtIsNull(dto.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Document", dto.getParentId()));
            doc.setParent(parent);
        }

        if (dto.getOrderNum() != null) {
            doc.setOrderNum(dto.getOrderNum());
        } else {
            // Find min orderNum among siblings to place new item at the top
            Integer minOrderNum;
            if (dto.getParentId() != null) {
                minOrderNum = documentRepository.findMinOrderNumByKbIdAndParentId(kbId, dto.getParentId());
            } else {
                minOrderNum = documentRepository.findMinOrderNumByKbIdAndParentIsNull(kbId);
            }
            doc.setOrderNum(minOrderNum == null ? 10000 : minOrderNum - 10000);
        }
        doc.setStatus(DocStatus.DRAFT);
        doc.setIsOpen(true);
        doc.setIsCover(false);
        doc.setAuthorId(userId);
        doc.setLastEditorId(userId);
        if (StringUtils.hasText(dto.getPassword())) {
            doc.setIsEncrypted(true);
            doc.setPassword(passwordEncoder.encode(dto.getPassword()));
        } else {
            doc.setIsEncrypted(false);
        }
        doc.setCreatedAt(OffsetDateTime.now());
        doc.setUpdatedAt(OffsetDateTime.now());

        KbDocument saved = documentRepository.save(doc);

        if (StringUtils.hasText(dto.getContent())) {
            updateContent(userId, saved.getId(), dto.getContent());
            KbDocumentRevision revision = revisionService.autoSave(saved.getId(), dto.getContent(), userId);
            saved.setCurrentVersion(revision.getVersion());
            saved = documentRepository.save(saved);
            eventPublisher.publishEvent(new DocumentSavedEvent(saved.getId(), dto.getContent()));
        }

        searchService.updateIndex(saved.getId());
        return saved;
    }

    @Override
    @Transactional
    @Auditable(targetType = AuditTargetType.DOCUMENT, action = AuditAction.UPDATE, targetIdExpression = "#docId")
    public KbDocument update(Long userId, Long kbId, Long docId, DocUpdateDTO dto) {
        if (docId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "docId is required");
        }
        permissionChecker.checkDocRole(userId, docId, com.nopkg.hellodoc.enums.DocRole.EDITOR);
        KbKnowledgeBase kb = kbService.getKnowledgeBase(java.util.Objects.requireNonNull(kbId));

        KbDocument doc = documentRepository.findByIdAndDeletedAtIsNull(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId));
        if (!doc.getKb().getId().equals(kb.getId())) {
            throw new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, "Document not found in kb");
        }

        if (StringUtils.hasText(dto.getName())) {
            doc.setName(dto.getName());
        }
        if (dto.getType() != null) {
            doc.setType(dto.getType());
        }
        if (dto.getStatus() != null) {
            doc.setStatus(dto.getStatus());
        }
        if (dto.getSlug() != null) {
            doc.setSlug(dto.getSlug());
        }
        if (dto.getPaperBgColor() != null) {
            doc.setPaperBgColor(dto.getPaperBgColor());
        }
        if (dto.getExtraMeta() != null) {
            Map<String, Object> currentMeta = doc.getExtraMeta() != null ? new java.util.HashMap<>(doc.getExtraMeta()) : new java.util.HashMap<>();
            currentMeta.putAll(dto.getExtraMeta());
            doc.setExtraMeta(currentMeta);
        }
        if (dto.getPaperBgImage() != null) {
            doc.setPaperBgImage(dto.getPaperBgImage());
        }
        if (dto.getParentId() != null) {
            if (dto.getParentId().equals(-1L)) {
                // 哨兵值 -1 表示移动到根目录
                doc.setParent(null);
            } else {
                if (dto.getParentId().equals(docId)) {
                    throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "parentId cannot be self");
                }
                KbDocument parent = documentRepository.findByIdAndDeletedAtIsNull(dto.getParentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Document", dto.getParentId()));
                if (!parent.getKb().getId().equals(kb.getId())) {
                    throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "parentId not in kb");
                }
                doc.setParent(parent);
            }
        }
        if (dto.getOrderNum() != null) {
            doc.setOrderNum(dto.getOrderNum());
        }
        if (dto.getIsOpen() != null) {
            doc.setIsOpen(dto.getIsOpen());
        }
        if (dto.getIsCover() != null) {
            if (dto.getIsCover()) {
                // Ensure mutual exclusivity for cover
                documentRepository.findByKbIdAndIsCoverTrue(kbId).forEach(existingCover -> {
                    if (!existingCover.getId().equals(docId)) {
                        existingCover.setIsCover(false);
                        documentRepository.save(existingCover);
                    }
                });
            }
            doc.setIsCover(dto.getIsCover());
        }

        doc.setLastEditorId(userId);
        if (dto.getPassword() != null) {
            if (StringUtils.hasText(dto.getPassword())) {
                doc.setIsEncrypted(true);
                doc.setPassword(passwordEncoder.encode(dto.getPassword()));
            } else {
                doc.setIsEncrypted(false);
                doc.setPassword(null);
            }
        }
        doc.setUpdatedAt(OffsetDateTime.now());

        if (dto.getContent() != null) {
            updateContent(userId, docId, dto.getContent());
            KbDocumentRevision revision = revisionService.autoSave(docId, dto.getContent(), userId);
            doc.setCurrentVersion(revision.getVersion());
            eventPublisher.publishEvent(new DocumentSavedEvent(docId, dto.getContent()));
        }

        KbDocument saved = documentRepository.save(doc);
        searchService.updateIndex(saved.getId());
        return saved;
    }

    @Override
    @Transactional
    @Auditable(targetType = AuditTargetType.DOCUMENT, action = AuditAction.DELETE, targetIdExpression = "#docId")
    public void delete(Long userId, Long kbId, Long docId) {
        if (docId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "docId is required");
        }
        permissionChecker.checkDocRole(userId, docId, com.nopkg.hellodoc.enums.DocRole.EDITOR);
        kbService.getKnowledgeBase(kbId);

        OffsetDateTime now = OffsetDateTime.now();
        java.util.ArrayDeque<Long> stack = new java.util.ArrayDeque<>();
        stack.push(docId);

        while (!stack.isEmpty()) {
            Long currentId = stack.pop();
            KbDocument doc = documentRepository.findByIdAndDeletedAtIsNull(currentId).orElse(null);
            if (doc == null) {
                continue;
            }
            if (!doc.getKb().getId().equals(kbId)) {
                throw new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, "Document not found in kb");
            }

            doc.setDeletedAt(now);
            documentRepository.save(doc);
            searchService.deleteIndex(currentId);
            docRelationRepository.deleteByIdSourceDocIdOrIdTargetDocId(currentId, currentId);

            List<KbDocument> children = documentRepository.findByKbIdAndParentIdAndDeletedAtIsNull(kbId, currentId);
            for (KbDocument child : children) {
                if (child != null && child.getId() != null) {
                    stack.push(child.getId());
                }
            }
        }
    }

    @Override
    @Transactional
    @Auditable(targetType = AuditTargetType.DOCUMENT, action = AuditAction.RESTORE, targetIdExpression = "#docId")
    public void restore(Long userId, Long kbId, Long docId) {
        if (kbId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "kbId is required");
        }
        permissionChecker.checkKbRole(userId, kbId, com.nopkg.hellodoc.enums.KbRole.EDITOR);

        KbDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId));

        if (!doc.getKb().getId().equals(kbId)) {
            throw new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, "Document not found in kb");
        }

        if (doc.getParent() != null && doc.getParent().getDeletedAt() != null) {
            doc.setParent(null);
        }

        java.util.ArrayDeque<Long> stack = new java.util.ArrayDeque<>();
        stack.push(docId);

        while (!stack.isEmpty()) {
            Long currentId = stack.pop();
            KbDocument current = documentRepository.findById(currentId).orElse(null);
            if (current == null) continue;

            current.setDeletedAt(null);
            documentRepository.save(current);
            searchService.rebuildIndexForRestoredDoc(currentId);

            List<KbDocument> children = documentRepository.findByKbIdAndParentId(kbId, currentId);
            for (KbDocument child : children) {
                if (child != null && child.getDeletedAt() != null) {
                    stack.push(child.getId());
                }
            }
        }
    }

    @Override
    public List<KbDocument> getTrashList(Long userId, Long kbId) {
        if (kbId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "kbId is required");
        }
        permissionChecker.checkKbRole(userId, kbId, com.nopkg.hellodoc.enums.KbRole.VIEWER);
        return documentRepository.findTrashTopItems(kbId);
    }

    @Override
    @Transactional
    @Auditable(targetType = AuditTargetType.DOCUMENT, action = AuditAction.DELETE, targetIdExpression = "#docId")
    public void permanentlyDelete(Long userId, Long kbId, Long docId) {
        if (docId == null || kbId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "docId and kbId are required");
        }
        permissionChecker.checkKbRole(userId, kbId, com.nopkg.hellodoc.enums.KbRole.ADMIN);

        java.util.ArrayDeque<Long> stack = new java.util.ArrayDeque<>();
        stack.push(docId);
        java.util.List<Long> idsToDelete = new java.util.ArrayList<>();

        while (!stack.isEmpty()) {
            Long currentId = stack.pop();
            KbDocument doc = documentRepository.findById(currentId).orElse(null);
            if (doc == null || !doc.getKb().getId().equals(kbId)) continue;

            idsToDelete.add(currentId);

            List<KbDocument> children = documentRepository.findByKbIdAndParentId(kbId, currentId);
            for (KbDocument child : children) {
                if (child != null && child.getId() != null) {
                    stack.push(child.getId());
                }
            }
        }

        if (!idsToDelete.isEmpty()) {
            documentRepository.clearParentByIds(idsToDelete);
            for (Long id : idsToDelete) {
                searchService.deleteIndex(id);
                docRelationRepository.deleteByIdSourceDocIdOrIdTargetDocId(id, id);
                contentRepository.deleteById(id);
                documentRepository.deleteById(id);
            }
        }
    }

    @Override
    @Transactional
    public void clearTrash(Long userId, Long kbId) {
        if (kbId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "kbId is required");
        }
        permissionChecker.checkKbRole(userId, kbId, com.nopkg.hellodoc.enums.KbRole.ADMIN);

        List<KbDocument> trashDocs = documentRepository.findAllTrashItems(kbId);
        if (trashDocs.isEmpty()) return;

        documentRepository.clearTrashParent(kbId);

        for (KbDocument doc : trashDocs) {
            Long id = doc.getId();
            searchService.deleteIndex(id);
            docRelationRepository.deleteByIdSourceDocIdOrIdTargetDocId(id, id);
            contentRepository.deleteById(id);
            documentRepository.deleteById(id);
        }
    }

    @Override
    public KbDocument getById(Long docId) {
        return documentRepository.findByIdAndDeletedAtIsNull(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId));
    }

    @Override
    public KbDocument getBySlug(Long kbId, String slug) {
        return documentRepository.findByKbIdAndSlugAndDeletedAtIsNull(kbId, slug)
                .orElseThrow(() -> new ResourceNotFoundException("Document slug", slug));
    }

    @Override
    public List<KbDocument> getTree(Long kbId) {
        return documentRepository.findByKbIdAndDeletedAtIsNull(java.util.Objects.requireNonNull(kbId));
    }

    @Override
    @Transactional
    public void move(Long userId, Long docId, Long newParentId) {
        if (docId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "docId is required");
        }
        permissionChecker.checkDocRole(userId, docId, com.nopkg.hellodoc.enums.DocRole.EDITOR);
        KbDocument doc = getById(docId);

        if (newParentId != null) {
            KbDocument newParent = getById(newParentId);
            doc.setParent(newParent);
        } else {
            doc.setParent(null);
        }

        doc.setUpdatedAt(OffsetDateTime.now());
        documentRepository.save(doc);
    }

    @Override
    @Transactional
    public void reorder(Long userId, Long parentId, List<Long> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);
            KbDocument doc = documentRepository.findByIdAndDeletedAtIsNull(id).orElse(null);
            if (doc != null) {
                doc.setOrderNum(i);
                documentRepository.save(doc);
            }
        }
    }

    @Override
    @Transactional
    public void updateContent(Long userId, Long docId, String content) {
        int updated = contentRepository.updateContent(docId, content, OffsetDateTime.now());
        if (updated == 0) {
            KbDocumentContent c = new KbDocumentContent();
            c.setDocId(docId);
            c.setContent(content);
            c.setUpdatedAt(OffsetDateTime.now());
            contentRepository.save(c);
        }
    }

    @Override
    public String getContent(Long docId) {
        KbDocument doc = getById(docId);
        if (Boolean.TRUE.equals(doc.getIsEncrypted())) {
            return "";
        }
        return getUnprotectedContent(docId);
    }

    @Override
    public String getUnprotectedContent(Long docId) {
        return contentRepository.findById(docId)
                .map(KbDocumentContent::getContent)
                .orElse("");
    }

    @Override
    @Transactional(readOnly = true)
    public KbDocument unlockDocument(Long userId, Long docId, String password) {
        KbDocument doc = getById(docId);
        if (!Boolean.TRUE.equals(doc.getIsEncrypted())) {
            return doc;
        }
        if (passwordEncoder.matches(password, doc.getPassword())) {
            // Success
            return doc;
        } else {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("auth.password_incorrect", "Incorrect password"));
        }
    }

    @Override
    public List<KbDocumentRevision> listRevisions(Long userId, Long kbId, Long docId) {
        if (docId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "docId is required");
        }
        permissionChecker.checkDocRole(userId, docId, com.nopkg.hellodoc.enums.DocRole.VIEWER);
        KbDocument doc = getById(docId);
        if (!doc.getKb().getId().equals(kbId)) {
            throw new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, "Document not found in kb");
        }
        return revisionService.getRevisionHistory(docId, Pageable.unpaged()).getContent();
    }

    @Override
    @Transactional
    @Auditable(targetType = AuditTargetType.DOCUMENT, action = AuditAction.UPDATE, targetIdExpression = "#docId")
    public KbDocumentRevision createRevision(Long userId, Long kbId, Long docId, String content, String message) {
        if (docId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "docId is required");
        }
        permissionChecker.checkDocRole(userId, docId, com.nopkg.hellodoc.enums.DocRole.EDITOR);

        KbDocument doc = getById(docId);
        if (!doc.getKb().getId().equals(kbId)) {
            throw new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, "Document not found in kb");
        }

        KbDocumentRevision saved = revisionService.createRevision(docId, content,
                com.nopkg.hellodoc.enums.RevisionType.MANUAL, message, userId);

        updateContent(userId, docId, content);
        doc.setCurrentVersion(saved.getVersion());
        doc.setLastEditorId(userId);
        doc.setUpdatedAt(OffsetDateTime.now());
        documentRepository.save(doc);

        searchService.updateIndex(docId);
        return saved;
    }

    @Override
    @Transactional
    @Auditable(targetType = AuditTargetType.DOCUMENT, action = AuditAction.CREATE, targetIdExpression = "#result.id")
    public KbDocument duplicate(Long userId, Long kbId, Long docId) {
        if (docId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "docId is required");
        }
        permissionChecker.checkDocRole(java.util.Objects.requireNonNull(userId), docId,
                com.nopkg.hellodoc.enums.DocRole.EDITOR);
        KbKnowledgeBase kb = kbService.getKnowledgeBase(java.util.Objects.requireNonNull(kbId));

        KbDocument original = getById(docId);
        if (!original.getKb().getId().equals(kb.getId())) {
            throw new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, "Document not found in kb");
        }

        // Find max orderNum among siblings to place duplicate at the end
        Integer maxOrderNum;
        if (original.getParent() != null) {
            maxOrderNum = documentRepository.findMaxOrderNumByKbIdAndParentId(kbId, original.getParent().getId());
        } else {
            maxOrderNum = documentRepository.findMaxOrderNumByKbIdAndParentIsNull(kbId);
        }

        return recursiveDuplicate(
                userId,
                original,
                kb,
                original.getParent(),
                original.getName() + " (" + com.nopkg.hellodoc.utils.MessageUtils.get("doc.copy_suffix", "Copy") + ")",
                maxOrderNum == null ? 0 : maxOrderNum + 1);
    }

    private KbDocument recursiveDuplicate(Long userId, KbDocument source, KbKnowledgeBase kb, KbDocument targetParent,
            String targetName, Integer targetOrderNum) {
        KbDocument copy = new KbDocument();
        copy.setKb(kb);
        copy.setName(targetName);
        copy.setType(source.getType());
        copy.setParent(targetParent);
        copy.setOrderNum(targetOrderNum == null ? 0 : targetOrderNum);
        copy.setStatus(DocStatus.DRAFT);
        copy.setPaperBgColor(source.getPaperBgColor());
        if (source.getExtraMeta() != null) {
            copy.setExtraMeta(new java.util.HashMap<>(source.getExtraMeta()));
        }
        copy.setPaperBgImage(source.getPaperBgImage());
        copy.setIsOpen(source.getIsOpen());
        copy.setIsCover(false);
        copy.setAuthorId(userId);
        copy.setLastEditorId(userId);
        copy.setCreatedAt(OffsetDateTime.now());
        copy.setUpdatedAt(OffsetDateTime.now());

        KbDocument saved = documentRepository.save(copy);

        if (source.getType() == DocType.FOLDER) {
            List<KbDocument> children = documentRepository
                    .findByKbIdAndParentIdAndDeletedAtIsNull(source.getKb().getId(), source.getId());
            children.sort(Comparator
                    .comparing((KbDocument d) -> d.getOrderNum() == null ? Integer.MAX_VALUE : d.getOrderNum())
                    .thenComparing((KbDocument d) -> d.getType() == DocType.FOLDER ? 0 : 1)
                    .thenComparing(KbDocument::getName, Comparator.nullsFirst(String::compareToIgnoreCase))
                    .thenComparing(KbDocument::getId));
            for (KbDocument child : children) {
                Integer childOrderNum = child.getOrderNum();
                recursiveDuplicate(userId, child, kb, saved, child.getName(), childOrderNum);
            }
        } else {
            String sourceContent = getContent(source.getId());
            if (StringUtils.hasText(sourceContent)) {
                updateContent(userId, saved.getId(), sourceContent);
            }
        }

        searchService.updateIndex(saved.getId());
        return saved;
    }

    @Override
    @Transactional
    @Auditable(targetType = AuditTargetType.DOCUMENT, action = AuditAction.CREATE, targetIdExpression = "#result.id")
    public KbDocument copyToKb(Long userId, Long sourceDocId, Long targetKbId) {
        if (sourceDocId == null || targetKbId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "sourceDocId and targetKbId are required");
        }
        // 校验目标知识库编辑权限
        permissionChecker.checkKbRole(userId, targetKbId, com.nopkg.hellodoc.enums.KbRole.EDITOR);
        KbKnowledgeBase targetKb = kbService.getKnowledgeBase(targetKbId);

        // 获取源文档
        KbDocument sourceDoc = getById(sourceDocId);
        String sourceKbTitle = sourceDoc.getKb().getTitle();

        // 递归拷贝逻辑
        return recursiveCopy(userId, sourceDoc, targetKb, null, sourceKbTitle, false);
    }

    private KbDocument recursiveCopy(Long userId, KbDocument source, KbKnowledgeBase targetKb, KbDocument targetParent,
            String sourceKbTitle, boolean preserveSourceOrderNum) {
        KbDocument copy = new KbDocument();
        copy.setKb(targetKb);
        copy.setName(source.getName());
        copy.setType(source.getType());
        copy.setParent(targetParent);
        copy.setStatus(source.getStatus());
        copy.setPaperBgColor(source.getPaperBgColor());
        if (source.getExtraMeta() != null) {
            copy.setExtraMeta(new java.util.HashMap<>(source.getExtraMeta()));
        }
        copy.setPaperBgImage(source.getPaperBgImage());
        copy.setIsOpen(source.getIsOpen());
        copy.setIsCover(false); // 拷贝不作为封面
        copy.setAuthorId(userId);
        copy.setLastEditorId(userId);
        copy.setCreatedAt(OffsetDateTime.now());
        copy.setUpdatedAt(OffsetDateTime.now());

        // 计算 orderNum：如果是根目录，取目标知识库根目录最大值；如果是子目录，取父节点下最大值
        Integer sourceOrderNum = source.getOrderNum();
        if (preserveSourceOrderNum && sourceOrderNum != null) {
            copy.setOrderNum(sourceOrderNum);
        } else {
            Integer maxOrderNum;
            if (targetParent != null) {
                maxOrderNum = documentRepository.findMaxOrderNumByKbIdAndParentId(targetKb.getId(), targetParent.getId());
            } else {
                maxOrderNum = documentRepository.findMaxOrderNumByKbIdAndParentIsNull(targetKb.getId());
            }
            copy.setOrderNum(maxOrderNum == null ? 0 : maxOrderNum + 1);
        }

        KbDocument saved = documentRepository.save(copy);

        // 如果是文件，拷贝内容
        if (source.getType() == com.nopkg.hellodoc.enums.DocType.FILE) {
            String content = contentRepository.findById(source.getId())
                    .map(KbDocumentContent::getContent)
                    .orElse("");
            if (StringUtils.hasText(content)) {
                updateContent(userId, saved.getId(), content);
                // 增加修订记录
                revisionService.createRevision(saved.getId(), content, com.nopkg.hellodoc.enums.RevisionType.MANUAL,
                        com.nopkg.hellodoc.utils.MessageUtils.get("doc.copy_from_kb", "Copy from Knowledge Base: ") + "【" + sourceKbTitle + "】", userId);
            }
        } else {
            // 如果是文件夹，递归拷贝子节点
            List<KbDocument> children = documentRepository
                    .findByKbIdAndParentIdAndDeletedAtIsNull(source.getKb().getId(), source.getId());
            children.sort(Comparator
                    .comparing((KbDocument d) -> d.getOrderNum() == null ? Integer.MAX_VALUE : d.getOrderNum())
                    .thenComparing((KbDocument d) -> d.getType() == DocType.FOLDER ? 0 : 1)
                    .thenComparing(KbDocument::getName, Comparator.nullsFirst(String::compareToIgnoreCase))
                    .thenComparing(KbDocument::getId));
            for (KbDocument child : children) {
                recursiveCopy(userId, child, targetKb, saved, sourceKbTitle, true);
            }
        }

        searchService.updateIndex(saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public void incrementViewCount(Long docId) {
        documentRepository.incrementViewCount(docId);
    }

    @Override
    @Transactional(readOnly = true)
    public void exportToZip(Long userId, Long kbId, Long docId, java.io.OutputStream outputStream) {
        permissionChecker.checkDocRole(userId, docId, com.nopkg.hellodoc.enums.DocRole.EDITOR);

        List<KbDocument> docs = documentRepository.findSubtree(kbId, docId);
        if (docs.isEmpty()) {
            throw new ResourceNotFoundException("Document", docId);
        }

        // Find root document to calculate relative paths
        KbDocument rootDoc = docs.stream()
                .filter(d -> d.getId().equals(docId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId));

        // For root folder/file, path is empty string (at root of zip)
        // If export root is a folder, we might want its name as root folder?
        // Usually export folder 'foo' -> zip contains 'foo/...' or just contents?
        // Let's assume zip contains the item itself at root if it's a folder, or just
        // the file.
        // Actually, improved logic:
        // If root is folder, we base everything relative to root's parent (so root
        // folder name is in zip).
        // If root is file, just file in zip.

        // Let's use simple logic: paths are relative to the *parent* of the root doc.
        // effectively root doc name is the first path segment.

        // We need to build the hierarchy map.
        // Pre-fill paths for all docs (they are consistent with ltree path ideally but
        // we might not have 'path' field mapped in entity fully reachable or readable
        // easily if it's string)
        // Since we fetched subtree, we can reconstruct paths based on parent
        // relationships.
        // The list is not guaranteed to be ordered by depth, so we sort or multi-pass.

        // Better: recursively build paths starting from root.

        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(outputStream)) {
            // Helper to build paths
            buildZipEntries(docs, rootDoc, "", zos);
        } catch (java.io.IOException e) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "Failed to generate zip");
        }
    }

    private void buildZipEntries(List<KbDocument> allDocs, KbDocument current, String currentPath,
            java.util.zip.ZipOutputStream zos) throws java.io.IOException {
        String name = current.getName();
        // Safe filename
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");

        String entryPath = StringUtils.hasText(currentPath) ? currentPath + "/" + name : name;

        if (current.getType() == com.nopkg.hellodoc.enums.DocType.FILE) {
            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(entryPath + ".md");
            try {
                zos.putNextEntry(entry);
                String content = contentRepository.findByDocId(current.getId())
                        .map(KbDocumentContent::getContent)
                        .orElse("");
                zos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();
            } catch (java.util.zip.ZipException e) {
                // duplicate entry, ignore or log
                log.warn("Duplicate zip entry: {}", entryPath);
            }
        } else {
            // Folder
            // Zip entry for folder (optional mostly, but good for empty folders)
            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(entryPath + "/");
            try {
                zos.putNextEntry(entry);
                zos.closeEntry();
            } catch (java.util.zip.ZipException e) {
                // duplicate
            }

            // Find children
            for (KbDocument doc : allDocs) {
                if (doc.getParent() != null && doc.getParent().getId().equals(current.getId())) {
                    buildZipEntries(allDocs, doc, entryPath, zos);
                }
            }
        }
    }
}
