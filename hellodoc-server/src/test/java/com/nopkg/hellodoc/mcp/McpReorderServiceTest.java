package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.services.PermissionChecker;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpReorderServiceTest {
    @Test void sortsMoreThanOnePageInsideANestedFolderAndRejectsIncompleteLists() {
        var repo = mock(KbDocumentRepository.class);
        var permissions = mock(PermissionChecker.class);
        var revisions = mock(DirectoryRevision.class);
        var kb = new KbKnowledgeBase(); kb.setId(9L); kb.setTreeRevision(1L);
        var parent = new KbDocument(); parent.setId(20L); parent.setKb(kb); parent.setType(DocType.FOLDER);
        var children = new java.util.ArrayList<KbDocument>();
        for (long id = 100; id < 151; id++) {
            var child = new KbDocument(); child.setId(id); child.setKb(kb); child.setParent(parent); child.setOrderNum((int) id);
            child.setType(id % 2 == 0 ? DocType.FILE : DocType.FOLDER); children.add(child);
            when(permissions.hasDocRole(7L, id, DocRole.EDITOR)).thenReturn(true);
        }
        when(revisions.lock(9)).thenReturn(kb);
        when(repo.findByIdAndDeletedAtIsNull(20L)).thenReturn(java.util.Optional.of(parent));
        when(permissions.hasDocRole(7L, 20L, DocRole.EDITOR)).thenReturn(true);
        when(repo.findByKbIdAndParentIdAndDeletedAtIsNull(9L, 20L)).thenReturn(children);
        when(revisions.bump(kb)).thenReturn("2");
        var service = new McpReorderService(repo, permissions, revisions);
        assertEquals("ORDER_CONFLICT", assertThrows(IllegalStateException.class,
                () -> service.reorder(7, 9, 20, "0", idsForSingleChild())).getMessage());
        var ids = new java.util.ArrayList<>(DirectoryOrder.ids(children));
        assertEquals("INVALID_ORDER", assertThrows(IllegalArgumentException.class,
                () -> service.reorder(7, 9, 20, "1", ids.subList(0, 50))).getMessage());
        java.util.Collections.reverse(ids);
        assertEquals(true, service.reorder(7, 9, 20, "1", ids).get("changed"));
        assertEquals(ids, DirectoryOrder.ids(children));
    }

    private static List<Long> idsForSingleChild() { return List.of(100L); }
    @Test void rejectsHiddenOrEncryptedSiblingsWithoutChangingOrder() {
        var repo = mock(KbDocumentRepository.class);
        var permissions = mock(PermissionChecker.class);
        var revisions = mock(DirectoryRevision.class);
        var kb = new KbKnowledgeBase(); kb.setId(9L); kb.setTreeRevision(1L);
        var doc = new KbDocument(); doc.setId(10L); doc.setKb(kb); doc.setOrderNum(1);
        when(revisions.lock(9)).thenReturn(kb);
        when(permissions.hasKbRole(7L, 9L, com.nopkg.hellodoc.enums.KbRole.EDITOR)).thenReturn(true);
        when(repo.findByKbIdAndParentIsNull(9L)).thenReturn(List.of(doc));
        assertEquals("DIRECTORY_UNAVAILABLE", assertThrows(IllegalStateException.class,
                () -> new McpReorderService(repo, permissions, revisions).reorder(7, 9, 0, "1", List.of(10L))).getMessage());
        when(permissions.hasDocRole(7L, 10L, DocRole.EDITOR)).thenReturn(true);
        doc.setIsEncrypted(true);
        assertThrows(IllegalStateException.class,
                () -> new McpReorderService(repo, permissions, revisions).reorder(7, 9, 0, "1", List.of(10L)));
        verify(repo, never()).saveAll(any());
    }

    @Test void reordersMixedSiblingsAndRejectsStaleChanges() {
        var repo = mock(KbDocumentRepository.class);
        var permissions = mock(PermissionChecker.class);
        var revisions = mock(DirectoryRevision.class);
        var kb = new KbKnowledgeBase(); kb.setId(9L); kb.setTreeRevision(2L);
        var file = new KbDocument(); file.setId(10L); file.setKb(kb); file.setType(DocType.FILE); file.setOrderNum(1);
        var folder = new KbDocument(); folder.setId(11L); folder.setKb(kb); folder.setType(DocType.FOLDER); folder.setOrderNum(2);
        when(revisions.lock(9)).thenReturn(kb);
        when(permissions.hasKbRole(7L, 9L, com.nopkg.hellodoc.enums.KbRole.EDITOR)).thenReturn(true);
        when(repo.findByKbIdAndParentIsNull(9L)).thenReturn(List.of(file, folder));
        when(permissions.hasDocRole(7L, 10L, DocRole.EDITOR)).thenReturn(true);
        when(permissions.hasDocRole(7L, 11L, DocRole.EDITOR)).thenReturn(true);
        when(revisions.bump(kb)).thenAnswer(invocation -> { kb.setTreeRevision(3L); return "3"; });
        var service = new McpReorderService(repo, permissions, revisions);
        assertEquals("ORDER_CONFLICT", assertThrows(IllegalStateException.class,
                () -> service.reorderWithExpectedOrder(7, 9, 0, List.of(11L, 10L), List.of(11L, 10L))).getMessage());
        assertEquals("ORDER_CONFLICT", assertThrows(IllegalStateException.class,
                () -> service.reorder(7, 9, 0, "1", List.of(11L, 10L))).getMessage());
        var result = service.reorder(7, 9, 0, "2", List.of(11L, 10L));
        assertEquals(true, result.get("changed"));
        assertTrue(folder.getOrderNum() < file.getOrderNum());
        assertEquals(false, service.reorder(7, 9, 0, "2", List.of(11L, 10L)).get("changed"));
    }
}
