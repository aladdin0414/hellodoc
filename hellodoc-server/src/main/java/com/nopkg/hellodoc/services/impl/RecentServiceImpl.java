package com.nopkg.hellodoc.services.impl;

import com.nopkg.hellodoc.entities.KbDocRecent;
import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.repositories.KbDocRecentRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.services.RecentService;
import com.nopkg.hellodoc.web.dto.ux.RecentDocVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecentServiceImpl implements RecentService {

    private final KbDocRecentRepository recentRepository;
    private final KbDocumentRepository documentRepository;

    @Override
    @Transactional
    public void recordVisit(Long userId, Long docId) {
        OffsetDateTime now = OffsetDateTime.now();
        KbDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        long current = doc.getViewCount() == null ? 0 : doc.getViewCount();
        doc.setViewCount(current + 1);
        documentRepository.save(doc);

        recentRepository.findByUserIdAndDocId(userId, docId)
                .ifPresentOrElse(
                        recent -> {
                            recent.setVisitedAt(now);
                            recentRepository.save(recent);
                        },
                        () -> {
                            KbDocRecent recent = new KbDocRecent();
                            recent.setUserId(userId);
                            recent.setDoc(doc);
                            recent.setVisitedAt(now);
                            recentRepository.save(recent);
                        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecentDocVO> getRecentDocuments(Long userId, int limit) {
        return recentRepository.findByUserIdOrderByVisitedAtDesc(userId, PageRequest.of(0, limit))
                .stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void clearRecentHistory(Long userId) {
        recentRepository.deleteByUserId(userId);
    }

    private RecentDocVO convertToVO(KbDocRecent recent) {
        RecentDocVO vo = new RecentDocVO();
        KbDocument doc = recent.getDoc();
        vo.setId(doc.getId());
        vo.setKbId(doc.getKb().getId());
        vo.setKbTitle(doc.getKb().getTitle());
        vo.setName(doc.getName());
        vo.setType(doc.getType().name());
        vo.setVisitedAt(recent.getVisitedAt());
        return vo;
    }
}
