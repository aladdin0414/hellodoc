package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.KbDocRecent;
import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.repositories.KbDocRecentRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.services.impl.RecentServiceImpl;
import com.nopkg.hellodoc.web.dto.ux.RecentDocVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RecentServiceTest {

    @Mock
    private KbDocRecentRepository recentRepository;
    @Mock
    private KbDocumentRepository documentRepository;

    @InjectMocks
    private RecentServiceImpl recentService;

    private Long userId = 1L;
    private Long docId = 100L;
    private KbDocument mockDoc;

    @BeforeEach
    void setUp() {
        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setId(10L);
        kb.setTitle("Test KB");

        mockDoc = new KbDocument();
        mockDoc.setId(docId);
        mockDoc.setName("Test Doc");
        mockDoc.setType(DocType.FILE);
        mockDoc.setKb(kb);
    }

    @Test
    void recordVisit_ShouldCreateNew_WhenNotExists() {
        when(recentRepository.findByUserIdAndDocId(userId, docId)).thenReturn(Optional.empty());
        when(documentRepository.findById(docId)).thenReturn(Optional.of(mockDoc));

        recentService.recordVisit(userId, docId);

        verify(recentRepository).save(any(KbDocRecent.class));
    }

    @Test
    void recordVisit_ShouldUpdate_WhenExists() {
        KbDocRecent existing = new KbDocRecent();
        existing.setVisitedAt(OffsetDateTime.now().minusDays(1));

        when(recentRepository.findByUserIdAndDocId(userId, docId)).thenReturn(Optional.of(existing));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(mockDoc));
        when(documentRepository.save(any(KbDocument.class))).thenAnswer(i -> i.getArgument(0));

        recentService.recordVisit(userId, docId);

        verify(recentRepository).save(existing);
        assertTrue(existing.getVisitedAt().isAfter(OffsetDateTime.now().minusMinutes(1)));
    }

    @Test
    void getRecentDocuments_ShouldReturnVOList() {
        KbDocRecent recent = new KbDocRecent();
        recent.setDoc(mockDoc);
        recent.setVisitedAt(OffsetDateTime.now());

        when(recentRepository.findByUserIdOrderByVisitedAtDesc(eq(userId), any(PageRequest.class)))
                .thenReturn(List.of(recent));

        List<RecentDocVO> result = recentService.getRecentDocuments(userId, 10);

        assertEquals(1, result.size());
        assertEquals(docId, result.get(0).getId());
        assertEquals("Test Doc", result.get(0).getName());
    }

    @Test
    void clearRecentHistory_ShouldDeleteAll() {
        recentService.clearRecentHistory(userId);
        verify(recentRepository).deleteByUserId(userId);
    }
}
