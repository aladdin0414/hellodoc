package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.services.DocumentService;
import com.nopkg.hellodoc.services.PermissionChecker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpMoveServiceTest {
    final KbDocumentRepository repository = mock(KbDocumentRepository.class);
    final DocumentService documents = mock(DocumentService.class);
    final PermissionChecker permissions = mock(PermissionChecker.class);
    final AiItemService items = mock(AiItemService.class);
    final DirectoryRevision revisions = mock(DirectoryRevision.class);
    final EntityManager entityManager = mock(EntityManager.class);
    final McpMoveService service = new McpMoveService(repository, documents, permissions, items, revisions);
    final KbKnowledgeBase kb = new KbKnowledgeBase();
    final KbDocument doc = new KbDocument();
    final KbDocument old = new KbDocument();
    final KbDocument target = new KbDocument();
    McpMoveServiceTest() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        kb.setId(9L); old.setId(3L); target.setId(4L); target.setKb(kb); target.setType(DocType.FOLDER);
        doc.setId(2L); doc.setKb(kb); doc.setType(DocType.FILE); doc.setParent(old);
        when(repository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(doc));
        when(revisions.lock(9L)).thenReturn(kb);
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(doc));
        when(repository.findByIdForUpdate(4L)).thenReturn(Optional.of(target));
        when(permissions.hasDocRole(7L,2L,DocRole.EDITOR)).thenReturn(true);
        when(documents.getById(4L)).thenReturn(target);
    }
    @Test void moveChecksPermissionsAndPreservesBodyVersion() {
        doc.setCurrentVersion(5);
        var result = service.move(7, 2, 3, 4);
        assertEquals(4L, doc.getParent().getId()); assertEquals(5, doc.getCurrentVersion());
        assertEquals(4L, result.get("parentId"));
        verify(items).validateWritableFolder(7,9,4L); verify(repository).save(doc);
        verify(documents, never()).update(any(), any(), any(), any());
    }
    @Test void staleLocationDoesNotMoveAndRetryIsIdempotent() {
        assertEquals("LOCATION_CONFLICT", assertThrows(McpMoveException.class, () -> service.move(7,2,99,4)).getMessage());
        verify(repository, never()).save(any());
        service.move(7,2,3,4); service.move(7,2,3,4);
        verify(repository, times(1)).save(doc);
        verify(revisions, times(1)).bump(kb);
    }
    @Test void movingToRootRequiresPermissionAndAcceptsZero() {
        service.move(7,2,3,0); assertNull(doc.getParent()); verify(items).validateWritableFolder(7,9,null);
    }
    @Test void rejectsNonFileDeletedEncryptedAndUnauthorized() {
        doc.setType(DocType.FOLDER); assertThrows(McpMoveException.class, () -> service.move(7,2,3,4));
        doc.setType(DocType.FILE); doc.setIsEncrypted(true); assertThrows(McpMoveException.class, () -> service.move(7,2,3,4));
        doc.setIsEncrypted(false); when(permissions.hasDocRole(7L,2L,DocRole.EDITOR)).thenReturn(false);
        assertThrows(McpMoveException.class, () -> service.move(7,2,3,4));
        verify(repository, never()).save(any());
    }
}
