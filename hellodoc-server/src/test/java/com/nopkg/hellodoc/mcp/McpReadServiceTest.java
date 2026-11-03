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
    final McpReadService service = new McpReadService(search, docs, permissions);

    @Test void readsUnicodePagesAndChecksPermissionEachTime() {
        var kb = new KbKnowledgeBase(); kb.setId(9L); kb.setTitle("项目");
        var doc = new KbDocument(); doc.setId(4L); doc.setKb(kb); doc.setName("草稿"); doc.setStatus(DocStatus.DRAFT);
        String body = "a".repeat(11999) + "😀" + "正文";
        when(docs.getById(4L)).thenReturn(doc); when(docs.getContent(4L)).thenReturn(body);
        var first = service.read(7, 4, 0);
        assertEquals(12000, first.get("nextOffset")); assertEquals(true, first.get("hasMore"));
        assertEquals("draft", first.get("status"));
        var second = service.read(7, 4, 12000);
        assertEquals(body, first.get("content").toString() + second.get("content"));
        assertEquals(false, second.get("hasMore")); assertNull(second.get("nextOffset"));
        assertEquals("", service.read(7, 4, Integer.MAX_VALUE).get("content"));
        verify(permissions, times(3)).checkDocRole(7L, 4L, DocRole.VIEWER);
        doc.setIsEncrypted(true);
        assertThrows(IllegalStateException.class, () -> service.read(7, 4, 0));
        doc.setIsEncrypted(false);
        doThrow(new IllegalStateException()).when(permissions).checkDocRole(7L, 4L, DocRole.VIEWER);
        assertEquals("Document unavailable", assertThrows(IllegalStateException.class, () -> service.read(7, 4, 0)).getMessage());
    }

    @Test void validatesArgumentsAndNormalizesUnavailableDocuments() {
        assertThrows(IllegalArgumentException.class, () -> service.search(7, "  ", 20));
        assertThrows(IllegalArgumentException.class, () -> service.search(7, "test", 101));
        assertThrows(IllegalArgumentException.class, () -> service.read(7, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.read(7, 4, -1));
        when(docs.getById(4L)).thenThrow(new IllegalStateException("not found"));
        assertEquals("Document unavailable", assertThrows(IllegalStateException.class, () -> service.read(7, 4, 0)).getMessage());
    }
}
