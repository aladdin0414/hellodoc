package com.nopkg.hellodoc.services.impl;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbDocumentContent;
import com.nopkg.hellodoc.entities.KbDocumentRevision;
import com.nopkg.hellodoc.enums.RevisionType;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.exceptions.ResourceNotFoundException;
import com.nopkg.hellodoc.repositories.KbDocumentContentRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRevisionRepository;
import com.nopkg.hellodoc.services.RevisionService;
import com.nopkg.hellodoc.services.SearchService;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RevisionServiceImpl implements RevisionService {

    private final KbDocumentRevisionRepository revisionRepository;
    private final KbDocumentRepository documentRepository;
    private final KbDocumentContentRepository contentRepository;
    private final SearchService searchService;
    private final DiffMatchPatch dmp = new DiffMatchPatch();

    @Override
    @Transactional
    public KbDocumentRevision createRevision(Long docId, String content, RevisionType type, String message,
            Long userId) {
        if (docId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("doc.id_cannot_be_empty", "docId cannot be empty"));
        }
        KbDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId));

        Integer maxVersion = revisionRepository.findTopByDocIdOrderByVersionDesc(docId)
                .map(KbDocumentRevision::getVersion)
                .orElse(0);

        KbDocumentRevision revision = new KbDocumentRevision();
        revision.setDoc(doc);
        revision.setVersion(maxVersion + 1);
        revision.setContent(content);
        revision.setAuthorUserId(userId);
        revision.setMessage(message);
        revision.setRevisionType(type);
        revision.setWordCount(content != null ? content.length() : 0);
        revision.setCreatedAt(OffsetDateTime.now());

        // 如果不是第一个版本，则计算差异（diff）
        if (maxVersion > 0) {
            revisionRepository.findTopByDocIdOrderByVersionDesc(docId).ifPresent(lastRev -> {
                String lastContent = lastRev.getContent();
                if (lastContent == null) {
                    // 如果上一个内容已归档或为空，则直接存储完整内容或进行相应处理
                } else {
                    LinkedList<DiffMatchPatch.Diff> diffs = dmp.diffMain(lastContent, content);
                    dmp.diffCleanupSemantic(diffs);
                    String delta = dmp.diffToDelta(diffs);
                    revision.setDiffContent(delta);
                }
            });
        }

        return revisionRepository.save(revision);
    }

    @Override
    @Transactional
    public KbDocumentRevision autoSave(Long docId, String content, Long userId) {
        // 可以在此处或控制器中实现简单的频率限制
        return createRevision(docId, content, RevisionType.AUTO, "Auto save", userId);
    }

    @Override
    @Transactional
    public void createMilestone(Long docId, String message, Long userId) {
        KbDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId));

        var latest = revisionRepository.findTopByDocIdOrderByVersionDesc(docId);
        String content = "";
        if (latest.isPresent()) {
            content = latest.get().getContent();
        }

        createRevision(docId, content, RevisionType.MILESTONE, message, userId);
    }

    @Override
    public Page<KbDocumentRevision> getRevisionHistory(Long docId, Pageable pageable) {
        return revisionRepository.findByDocId(docId, pageable);
    }

    @Override
    public KbDocumentRevision getRevision(Long docId, Integer version) {
        return revisionRepository.findByDocIdAndVersion(docId, version)
                .orElseThrow(() -> new ResourceNotFoundException("Revision", version.longValue()));
    }

    @Override
    public String getRevisionContent(Long docId, Integer version) {
        KbDocumentRevision revision = getRevision(docId, version);
        if (revision.getContent() == null) {
            return "";
        }
        return revision.getContent();
    }

    @Override
    @Transactional
    public void restoreRevision(Long docId, Integer version, Long userId) {
        String content = getRevisionContent(docId, version);
        KbDocumentRevision restored = createRevision(docId, content, RevisionType.RESTORE,
                "Restored from version " + version, userId);

        KbDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId));

        OffsetDateTime now = OffsetDateTime.now();
        KbDocumentContent docContent = contentRepository.findById(docId).orElseGet(() -> {
            KbDocumentContent c = new KbDocumentContent();
            c.setDocId(docId);
            return c;
        });
        docContent.setContent(content);
        docContent.setUpdatedAt(now);
        contentRepository.save(docContent);

        doc.setCurrentVersion(restored.getVersion());
        doc.setLastEditorId(userId);
        doc.setUpdatedAt(now);
        documentRepository.save(doc);
        searchService.updateIndex(docId);
    }

    @Override
    public String compareRevisions(Long docId, Integer v1, Integer v2) {
        String c1 = getRevisionContent(docId, v1);
        String c2 = getRevisionContent(docId, v2);

        LinkedList<DiffMatchPatch.Diff> diffs = dmp.diffMain(c1, c2);
        dmp.diffCleanupSemantic(diffs);
        return dmp.diffToDelta(diffs);
    }

    @Override
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupAndArchiveRevisions() {
        log.info("开始清理修订版本...");
        List<Long> docIds = revisionRepository.findDocsExceedingAutoLimit(50);
        for (Long docId : docIds) {
            List<KbDocumentRevision> revisions = revisionRepository
                    .findByDocIdAndRevisionTypeOrderByVersionDesc(docId, RevisionType.AUTO);

            if (revisions.size() > 50) {
                List<KbDocumentRevision> toDelete = revisions.subList(50, revisions.size());
                revisionRepository.deleteAll(toDelete);
                log.info("为文档 {} 删除了 {} 个旧的自动保存修订版本", docId, toDelete.size());
            }
        }
    }
}
