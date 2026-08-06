package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbDocumentRevision;
import com.nopkg.hellodoc.enums.RevisionType;
import com.nopkg.hellodoc.repositories.KbDocumentContentRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRevisionRepository;
import com.nopkg.hellodoc.services.SearchService;
import com.nopkg.hellodoc.services.impl.RevisionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RevisionServiceTest {

    @Mock
    private KbDocumentRevisionRepository revisionRepository;
    @Mock
    private KbDocumentRepository documentRepository;
    @Mock
    private KbDocumentContentRepository contentRepository;
    @Mock
    private SearchService searchService;

    @InjectMocks
    private RevisionServiceImpl revisionService;

    // Remove MockitoAnnotations.openMocks(this) in setUp since @ExtendWith handles
    // it
    // but keeping setUp for other inits if needed
    @BeforeEach
    public void setup() {
    }

    @Test
    public void testCreateRevision_FirstVersion() {
        Long docId = 1L;
        Long userId = 100L;
        String content = "Initial content";

        KbDocument doc = new KbDocument();
        doc.setId(docId);

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(revisionRepository.findTopByDocIdOrderByVersionDesc(docId)).thenReturn(Optional.empty());
        when(revisionRepository.save(any(KbDocumentRevision.class))).thenAnswer(i -> {
            KbDocumentRevision r = i.getArgument(0);
            r.setId(1L);
            return r;
        });

        KbDocumentRevision result = revisionService.createRevision(docId, content, RevisionType.MANUAL, "Init", userId);

        assertNotNull(result);
        assertEquals(1, result.getVersion());
        assertEquals(content, result.getContent());
        assertNull(result.getDiffContent());
        assertEquals(RevisionType.MANUAL, result.getRevisionType());
    }

    @Test
    public void testCreateRevision_SecondVersionWithDiff() {
        Long docId = 1L;
        Long userId = 100L;
        String contentV1 = "Hello World";
        String contentV2 = "Hello Java World";

        KbDocument doc = new KbDocument();
        doc.setId(docId);

        KbDocumentRevision rev1 = new KbDocumentRevision();
        rev1.setVersion(1);
        rev1.setContent(contentV1);

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(revisionRepository.findTopByDocIdOrderByVersionDesc(docId)).thenReturn(Optional.of(rev1));
        when(revisionRepository.save(any(KbDocumentRevision.class))).thenAnswer(i -> i.getArgument(0));

        KbDocumentRevision result = revisionService.createRevision(docId, contentV2, RevisionType.MANUAL, "Update",
                userId);

        assertNotNull(result);
        assertEquals(2, result.getVersion());
        assertEquals(contentV2, result.getContent());
        assertNotNull(result.getDiffContent());
        assertTrue(result.getDiffContent().contains("Java"));
    }

    @Test
    public void testAutoSave() {
        Long docId = 1L;
        Long userId = 100L;
        String content = "Auto saved content";

        KbDocument doc = new KbDocument();
        doc.setId(docId);

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(revisionRepository.findTopByDocIdOrderByVersionDesc(docId)).thenReturn(Optional.empty());
        when(revisionRepository.save(any(KbDocumentRevision.class))).thenAnswer(i -> i.getArgument(0));

        revisionService.autoSave(docId, content, userId);

        verify(revisionRepository).save(argThat(rev -> rev.getRevisionType() == RevisionType.AUTO &&
                rev.getContent().equals(content) &&
                rev.getMessage().equals("Auto save")));
    }

    @Test
    public void testCreateMilestone() {
        Long docId = 1L;
        Long userId = 100L;
        String message = "Release 1.0";
        String content = "Milestone Content";

        KbDocument doc = new KbDocument();
        doc.setId(docId);

        KbDocumentRevision lastRev = new KbDocumentRevision();
        lastRev.setVersion(5);
        lastRev.setContent(content);

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(revisionRepository.findTopByDocIdOrderByVersionDesc(docId)).thenReturn(Optional.of(lastRev));
        // Note: createMilestone calls createRevision internally, which calls
        // findTopByDocId again.
        // Since mocks are stateful/strict, usually okay, but let's ensure behavior
        // consistency.

        when(revisionRepository.save(any(KbDocumentRevision.class))).thenAnswer(i -> i.getArgument(0));

        revisionService.createMilestone(docId, message, userId);

        verify(revisionRepository).save(argThat(rev -> rev.getRevisionType() == RevisionType.MILESTONE &&
                rev.getContent().equals(content) &&
                rev.getVersion() == 6 &&
                rev.getMessage().equals(message)));
    }

    @Test
    public void testRestoreRevision() {
        Long docId = 1L;
        Long userId = 100L;
        Integer targetVersion = 3;
        String restoredContent = "Old Content";

        KbDocument doc = new KbDocument();
        doc.setId(docId);

        KbDocumentRevision targetRev = new KbDocumentRevision();
        targetRev.setVersion(targetVersion);
        targetRev.setContent(restoredContent);

        KbDocumentRevision currentRev = new KbDocumentRevision();
        currentRev.setVersion(10);
        currentRev.setContent("Current Content");

        when(revisionRepository.findByDocIdAndVersion(docId, targetVersion)).thenReturn(Optional.of(targetRev));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(revisionRepository.findTopByDocIdOrderByVersionDesc(docId)).thenReturn(Optional.of(currentRev));
        when(revisionRepository.save(any(KbDocumentRevision.class))).thenAnswer(i -> i.getArgument(0));
        when(contentRepository.findById(docId)).thenReturn(Optional.empty());
        when(contentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        revisionService.restoreRevision(docId, targetVersion, userId);

        verify(revisionRepository).save(argThat(rev -> rev.getRevisionType() == RevisionType.RESTORE &&
                rev.getContent().equals(restoredContent) &&
                rev.getMessage().contains("Restored from version " + targetVersion)));
    }

    @Test
    public void testCleanupAndArchiveRevisions() {
        Long docId = 99L;
        // Create 60 revisions, we expect 10 to be deleted (keeping 50)
        List<KbDocumentRevision> revisions = IntStream.rangeClosed(1, 60)
                .mapToObj(i -> {
                    KbDocumentRevision r = new KbDocumentRevision();
                    r.setId((long) i);
                    r.setVersion(61 - i); // Descending order
                    return r;
                })
                .collect(Collectors.toList());

        when(revisionRepository.findDocsExceedingAutoLimit(50)).thenReturn(Collections.singletonList(docId));
        when(revisionRepository.findByDocIdAndRevisionTypeOrderByVersionDesc(docId, RevisionType.AUTO))
                .thenReturn(revisions);

        revisionService.cleanupAndArchiveRevisions();

        // Should delete sublist from index 50 to 60 (size 10)
        verify(revisionRepository).deleteAll(argThat(list -> ((List) list).size() == 10));
    }

    @Test
    public void testGetRevisionContent() {
        Long docId = 1L;
        Integer version = 2;
        String content = "Some content";

        KbDocumentRevision rev = new KbDocumentRevision();
        rev.setContent(content);

        when(revisionRepository.findByDocIdAndVersion(docId, version)).thenReturn(Optional.of(rev));

        String result = revisionService.getRevisionContent(docId, version);

        assertEquals(content, result);
    }

    @Test
    public void testCompareRevisions() {
        Long docId = 1L;

        KbDocumentRevision r1 = new KbDocumentRevision();
        r1.setContent("Cat");
        KbDocumentRevision r2 = new KbDocumentRevision();
        r2.setContent("Cat Dog");

        when(revisionRepository.findByDocIdAndVersion(docId, 1)).thenReturn(Optional.of(r1));
        when(revisionRepository.findByDocIdAndVersion(docId, 2)).thenReturn(Optional.of(r2));

        String diff = revisionService.compareRevisions(docId, 1, 2);

        assertNotNull(diff);
        assertTrue(diff.contains("Dog"));
    }
}
