package com.nopkg.hellodoc.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.services.DocumentService;
import com.nopkg.hellodoc.services.PermissionChecker;
import com.nopkg.hellodoc.web.dto.kb.DocUpdateDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.OffsetDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class McpUpdateServiceTest {
    final KbDocumentRepository docs = mock(KbDocumentRepository.class);
    final DocumentService documentService = mock(DocumentService.class);
    final PermissionChecker permissions = mock(PermissionChecker.class);
    final McpDocumentUpdateOperationRepository operations = mock(McpDocumentUpdateOperationRepository.class);
    final McpUpdateService service = new McpUpdateService(docs, documentService, permissions, operations, new ObjectMapper());
    final KbDocument doc = new KbDocument();
    McpUpdateServiceTest() {
        ReflectionTestUtils.setField(service, "entityManager", mock(jakarta.persistence.EntityManager.class));
        var kb = new KbKnowledgeBase(); kb.setId(99L); kb.setTitle("other");
        doc.setId(5L); doc.setKb(kb); doc.setType(DocType.FILE); doc.setCurrentVersion(1); doc.setUpdatedAt(OffsetDateTime.now());
        lenient().when(docs.findByIdForUpdate(5L)).thenReturn(Optional.of(doc));
        lenient().when(permissions.hasDocRole(7L, 5L, DocRole.EDITOR)).thenReturn(true);
        lenient().when(operations.findByUserIdAndDocument_IdAndRequestId(anyLong(), anyLong(), anyString())).thenReturn(Optional.empty());
    }
    @Test void replaceEmptyBodyIsAllowedAcrossKnowledgeBases() {
        when(documentService.update(eq(7L), eq(99L), eq(5L), any(DocUpdateDTO.class))).thenAnswer(invocation -> {
            var dto = invocation.getArgument(3, DocUpdateDTO.class);
            assertEquals("", dto.getContent()); assertEquals(1, dto.getExpectedVersion());
            doc.setCurrentVersion(2); return doc;
        });
        var result = service.update(7, 5, 1, "replace", "", "request");
        assertEquals(2, result.get("version")); assertEquals("/kb/99/5", result.get("path"));
        verify(operations).save(any(McpDocumentUpdateOperation.class));
    }
    @Test void rejectsInvalidInputsAndUtf8ByteLimit() {
        assertEquals("INVALID_ARGUMENT", assertThrows(McpUpdateException.class, () -> service.update(7, 5, 1, "append", "", "r")).getMessage());
        assertEquals("INVALID_ARGUMENT", assertThrows(McpUpdateException.class, () -> service.update(7, 5, 1, "other", "x", "r")).getMessage());
        assertEquals("INVALID_ARGUMENT", assertThrows(McpUpdateException.class, () -> service.update(7, 5, 1, "replace", "x", " ")).getMessage());
        assertEquals("TOO_LARGE", assertThrows(McpUpdateException.class, () -> service.update(7, 5, 1, "replace", "中".repeat(700000), "r")).getMessage());
        verifyNoInteractions(documentService);
    }
    @Test void rejectsUnavailableAndStaleDocuments() {
        doc.setIsEncrypted(true);
        assertEquals("DOCUMENT_UNAVAILABLE", assertThrows(McpUpdateException.class, () -> service.update(7, 5, 1, "replace", "x", "r")).getMessage());
        doc.setIsEncrypted(false); doc.setType(DocType.FOLDER);
        assertEquals("DOCUMENT_UNAVAILABLE", assertThrows(McpUpdateException.class, () -> service.update(7, 5, 1, "replace", "x", "r")).getMessage());
        doc.setType(DocType.FILE); doc.setDeletedAt(OffsetDateTime.now());
        assertEquals("DOCUMENT_UNAVAILABLE", assertThrows(McpUpdateException.class, () -> service.update(7, 5, 1, "replace", "x", "r")).getMessage());
        doc.setDeletedAt(null);
        when(permissions.hasDocRole(7L, 5L, DocRole.EDITOR)).thenReturn(false);
        assertEquals("DOCUMENT_UNAVAILABLE", assertThrows(McpUpdateException.class, () -> service.update(7, 5, 1, "replace", "x", "r")).getMessage());
        when(permissions.hasDocRole(7L, 5L, DocRole.EDITOR)).thenReturn(true);
        assertEquals("VERSION_CONFLICT", assertThrows(McpUpdateException.class, () -> service.update(7, 5, 0, "replace", "x", "r")).getMessage());
        verifyNoInteractions(documentService);
    }

    @Test void rejectsAppendWhenFinalBodyExceedsLimit() {
        when(documentService.getContent(5L)).thenReturn("a".repeat(2 * 1024 * 1024));
        assertEquals("TOO_LARGE", assertThrows(McpUpdateException.class,
                () -> service.update(7, 5, 1, "append", "中", "r")).getMessage());
        verify(documentService, never()).update(anyLong(), anyLong(), anyLong(), any());
        verify(operations, never()).save(any());
    }
    @Test void exactTwoMiBIsAllowed() {
        when(documentService.update(eq(7L), eq(99L), eq(5L), any(DocUpdateDTO.class))).thenAnswer(invocation -> {
            doc.setCurrentVersion(2); return doc;
        });
        assertEquals(2, service.update(7, 5, 1, "replace", "a".repeat(2 * 1024 * 1024), "r").get("version"));
    }
}
