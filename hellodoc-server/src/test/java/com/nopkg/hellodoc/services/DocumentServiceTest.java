package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbDocumentContent;
import com.nopkg.hellodoc.entities.KbDocumentRevision;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.repositories.KbDocumentContentRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.repositories.KbDocRelationRepository;
import com.nopkg.hellodoc.services.impl.DocumentServiceImpl;
import com.nopkg.hellodoc.web.dto.kb.DocCreateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DocumentServiceTest {

    @Mock
    private KbDocumentRepository documentRepository;
    @Mock
    private KbDocumentContentRepository contentRepository;
    @Mock
    private KbService kbService;
    @Mock
    private PermissionChecker permissionChecker;
    @Mock
    private RevisionService revisionService;
    @Mock
    private SearchService searchService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private KbDocRelationRepository docRelationRepository;

    @InjectMocks
    private DocumentServiceImpl documentService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testCreateDocument() {
        Long userId = 1L;
        Long kbId = 1L;
        DocCreateDTO dto = new DocCreateDTO();
        dto.setName("Test Doc");
        dto.setType(DocType.FILE);
        dto.setContent("Hello world");

        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setId(kbId);

        when(kbService.getKnowledgeBase(kbId)).thenReturn(kb);
        when(contentRepository.findById(any(Long.class))).thenReturn(Optional.empty());
        when(documentRepository.findByKbIdAndParentIdAndDeletedAtIsNull(kbId, null)).thenReturn(List.of());
        KbDocumentRevision mockRevision = new KbDocumentRevision();
        mockRevision.setVersion(1);
        when(revisionService.autoSave(any(), any(), any())).thenReturn(mockRevision);
        when(documentRepository.save(any(KbDocument.class))).thenAnswer(i -> {
            KbDocument d = i.getArgument(0);
            d.setId(100L);
            return d;
        });

        KbDocument doc = documentService.create(userId, kbId, dto);

        assertNotNull(doc);
        assertEquals("Test Doc", doc.getName());
        assertEquals(DocType.FILE, doc.getType());
        verify(contentRepository, times(1)).save(any());
        verify(documentRepository, times(2)).save(any(KbDocument.class));
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    public void testMoveDocument() {
        Long userId = 1L;
        Long docId = 100L;
        Long newParentId = 200L;

        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setId(1L);

        KbDocument doc = new KbDocument();
        doc.setId(docId);
        doc.setKb(kb);

        KbDocument newParent = new KbDocument();
        newParent.setId(newParentId);
        newParent.setPath("200");

        when(documentRepository.findByIdAndDeletedAtIsNull(docId)).thenReturn(Optional.of(doc));
        when(documentRepository.findByIdAndDeletedAtIsNull(newParentId)).thenReturn(Optional.of(newParent));

        documentService.move(userId, docId, newParentId);

        assertEquals(newParent, doc.getParent());
        verify(documentRepository, times(1)).save(doc);
    }

    @Test
    public void testDuplicateFolderRecursivelyCopiesChildren() {
        Long userId = 1L;
        Long kbId = 1L;
        Long rootId = 10L;
        Long subFolderId = 11L;
        Long fileAId = 12L;
        Long nestedFileId = 13L;

        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setId(kbId);

        KbDocument root = new KbDocument();
        root.setId(rootId);
        root.setKb(kb);
        root.setName("Root");
        root.setType(DocType.FOLDER);
        root.setParent(null);
        root.setOrderNum(10000);
        root.setIsOpen(true);

        KbDocument subFolder = new KbDocument();
        subFolder.setId(subFolderId);
        subFolder.setKb(kb);
        subFolder.setName("SubFolder");
        subFolder.setType(DocType.FOLDER);
        subFolder.setParent(root);
        subFolder.setOrderNum(10000);
        subFolder.setIsOpen(true);

        KbDocument fileA = new KbDocument();
        fileA.setId(fileAId);
        fileA.setKb(kb);
        fileA.setName("FileA");
        fileA.setType(DocType.FILE);
        fileA.setParent(root);
        fileA.setOrderNum(20000);
        fileA.setIsOpen(true);

        KbDocument nestedFile = new KbDocument();
        nestedFile.setId(nestedFileId);
        nestedFile.setKb(kb);
        nestedFile.setName("NestedFile");
        nestedFile.setType(DocType.FILE);
        nestedFile.setParent(subFolder);
        nestedFile.setOrderNum(10000);
        nestedFile.setIsOpen(true);

        KbDocumentContent fileAContent = new KbDocumentContent();
        fileAContent.setDocId(fileAId);
        fileAContent.setContent("content-a");
        KbDocumentContent nestedFileContent = new KbDocumentContent();
        nestedFileContent.setDocId(nestedFileId);
        nestedFileContent.setContent("content-nested");

        when(kbService.getKnowledgeBase(kbId)).thenReturn(kb);
        when(documentRepository.findByIdAndDeletedAtIsNull(rootId)).thenReturn(Optional.of(root));
        when(documentRepository.findByIdAndDeletedAtIsNull(fileAId)).thenReturn(Optional.of(fileA));
        when(documentRepository.findByIdAndDeletedAtIsNull(nestedFileId)).thenReturn(Optional.of(nestedFile));
        when(documentRepository.findMaxOrderNumByKbIdAndParentIsNull(kbId)).thenReturn(30000);
        when(documentRepository.findByKbIdAndParentIdAndDeletedAtIsNull(kbId, rootId))
                .thenReturn(new ArrayList<>(List.of(fileA, subFolder)));
        when(documentRepository.findByKbIdAndParentIdAndDeletedAtIsNull(kbId, subFolderId))
                .thenReturn(new ArrayList<>(List.of(nestedFile)));
        when(contentRepository.findById(any(Long.class))).thenReturn(Optional.empty());
        when(contentRepository.findById(fileAId)).thenReturn(Optional.of(fileAContent));
        when(contentRepository.findById(nestedFileId)).thenReturn(Optional.of(nestedFileContent));

        AtomicLong idGen = new AtomicLong(1000L);
        when(documentRepository.save(any(KbDocument.class))).thenAnswer(invocation -> {
            KbDocument d = invocation.getArgument(0);
            if (d.getId() == null) {
                d.setId(idGen.incrementAndGet());
            }
            return d;
        });

        KbDocument duplicatedRoot = documentService.duplicate(userId, kbId, rootId);

        assertNotNull(duplicatedRoot);
        assertEquals("Root (副本)", duplicatedRoot.getName());
        assertEquals(DocType.FOLDER, duplicatedRoot.getType());
        assertEquals(30001, duplicatedRoot.getOrderNum());

        verify(documentRepository, times(4)).save(any(KbDocument.class));
        verify(contentRepository, times(2)).save(any(KbDocumentContent.class));
        verify(searchService, times(4)).updateIndex(any(Long.class));
    }
}
