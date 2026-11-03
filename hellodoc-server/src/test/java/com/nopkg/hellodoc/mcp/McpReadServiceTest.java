package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.DocStatus;
import com.nopkg.hellodoc.services.DocumentService;
import com.nopkg.hellodoc.services.PermissionChecker;
import com.nopkg.hellodoc.services.SearchService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpReadServiceTest {
    final SearchService search = mock(SearchService.class);
    final DocumentService docs = mock(DocumentService.class);
    final PermissionChecker permissions = mock(PermissionChecker.class);
    final com.nopkg.hellodoc.repositories.KbDocumentRepository documentRepository = mock(com.nopkg.hellodoc.repositories.KbDocumentRepository.class);
    final McpReadService service = new McpReadService(search, docs, permissions, documentRepository);
    @org.junit.jupiter.api.BeforeEach void injectEntityManager() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "entityManager", mock(jakarta.persistence.EntityManager.class));
    }

    @Test void readsUnicodePagesAndChecksPermissionEachTime() {
        var kb = new KbKnowledgeBase(); kb.setId(9L); kb.setTitle("项目");
        var doc = new KbDocument(); doc.setId(4L); doc.setKb(kb); doc.setName("草稿"); doc.setStatus(DocStatus.DRAFT);
        String body = "a".repeat(11999) + "😀" + "正文";
        when(documentRepository.findByIdForUpdate(4L)).thenReturn(java.util.Optional.of(doc)); when(docs.getContent(4L)).thenReturn(body);
        var first = service.read(7, 4, 0, null);
        assertEquals(12000, first.get("nextOffset")); assertEquals(true, first.get("hasMore"));
        assertEquals("draft", first.get("status"));
        var second = service.read(7, 4, 12000, 0);
        assertEquals(body, first.get("content").toString() + second.get("content"));
        assertEquals(false, second.get("hasMore")); assertNull(second.get("nextOffset"));
        assertEquals("", service.read(7, 4, Integer.MAX_VALUE, 0).get("content"));
        verify(permissions, times(3)).checkDocRole(7L, 4L, DocRole.VIEWER);
        doc.setIsEncrypted(true);
        assertThrows(IllegalStateException.class, () -> service.read(7, 4, 0, null));
        doc.setIsEncrypted(false);
        doThrow(new IllegalStateException()).when(permissions).checkDocRole(7L, 4L, DocRole.VIEWER);
        assertEquals("Document unavailable", assertThrows(IllegalStateException.class, () -> service.read(7, 4, 0, null)).getMessage());
    }

    @Test void validatesArgumentsAndNormalizesUnavailableDocuments() {
        assertThrows(IllegalArgumentException.class, () -> service.search(7, "  ", 20));
        assertThrows(IllegalArgumentException.class, () -> service.search(7, "test", 101));
        assertThrows(IllegalArgumentException.class, () -> service.read(7, 0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> service.read(7, 4, -1, null));
        when(documentRepository.findByIdForUpdate(4L)).thenReturn(java.util.Optional.empty());
        assertEquals("Document unavailable", assertThrows(IllegalStateException.class, () -> service.read(7, 4, 0, null)).getMessage());
    }

    @Test void folderReturnsOnlyAccessibleChildrenWithPagination() {
        var kb = new KbKnowledgeBase(); kb.setId(9L); kb.setTitle("项目");
        var folder = new KbDocument(); folder.setId(4L); folder.setKb(kb); folder.setName("账号"); folder.setType(com.nopkg.hellodoc.enums.DocType.FOLDER);
        java.util.List<KbDocument> children = new java.util.ArrayList<>();
        for (long i = 10; i < 63; i++) {
            var child = new KbDocument(); child.setId(i); child.setName("文档" + i); child.setKb(kb);
            child.setType(com.nopkg.hellodoc.enums.DocType.FILE); child.setParent(folder); child.setOrderNum((int)i);
            children.add(child);
            when(permissions.hasDocRole(7L, i, DocRole.VIEWER)).thenReturn(i != 10);
        }
        when(documentRepository.findByIdForUpdate(4L)).thenReturn(java.util.Optional.of(folder)); when(docs.getTree(9L)).thenReturn(children);
        var first = service.read(7, 4, 0, null);
        assertEquals("folder", first.get("type"));
        assertEquals("这是文件夹，没有正文。", first.get("message"));
        var items = (java.util.List<?>) first.get("children");
        assertEquals(50, items.size());
        assertEquals(11L, ((java.util.Map<?, ?>) items.get(0)).get("documentId"));
        assertEquals("/kb/9/11", ((java.util.Map<?, ?>) items.get(0)).get("path"));
        assertEquals(true, first.get("hasMore")); assertEquals(50, first.get("nextOffset"));
        var second = service.read(7, 4, 50, null);
        assertEquals(2, ((java.util.List<?>) second.get("children")).size());
        assertEquals(false, second.get("hasMore")); assertNull(second.get("nextOffset"));
        verify(docs, never()).getContent(4L);
    }
}
