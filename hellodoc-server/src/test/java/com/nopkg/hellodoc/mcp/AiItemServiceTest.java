package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.services.DocumentService;
import com.nopkg.hellodoc.services.PermissionChecker;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.web.dto.kb.DocCreateDTO;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiItemServiceTest {
    @Test
    void listsOnlyReadableMarkedRootFoldersInRequestedKnowledgeBase() {
        DocumentService documents = mock(DocumentService.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        KbKnowledgeBase kb = new KbKnowledgeBase(); kb.setId(7L);
        KbDocument item = folder(1L, kb, Map.of("hellodoc_ai", Map.of("item", true, "description", "开发事项", "aliases", List.of("MCP"))));
        KbDocument unmarked = folder(2L, kb, Map.of());
        KbDocument nested = folder(3L, kb, Map.of("hellodoc_ai", Map.of("item", true))); nested.setParent(item);
        KbDocument hidden = folder(4L, kb, Map.of("hellodoc_ai", Map.of("item", true)));
        when(documents.getTree(7L)).thenReturn(List.of(item, unmarked, nested, hidden));
        when(permissions.hasDocRole(9L, 1L, DocRole.VIEWER)).thenReturn(true);
        var result = new AiItemService(documents, permissions).listItems(9L, 7L);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals("开发事项", result.get(0).description());
        assertEquals(List.of("MCP"), result.get(0).aliases());
        verify(permissions).checkKbRole(9L, 7L, KbRole.VIEWER);
    }

    @Test
    void createsItemUsingExistingDocumentService() {
        DocumentService documents = mock(DocumentService.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        when(documents.create(eq(9L), eq(7L), any())).thenAnswer(invocation -> {
            DocCreateDTO dto = invocation.getArgument(2);
            KbKnowledgeBase kb = new KbKnowledgeBase(); kb.setId(7L);
            KbDocument doc = folder(42L, kb, dto.getExtraMeta()); doc.setName(dto.getName()); return doc;
        });
        var result = new AiItemService(documents, permissions).createItem(9L, 7L, " MCP 接入 ", "开发", List.of("MCP"));
        assertEquals(42L, result.id());
        assertEquals("MCP 接入", result.name());
        var dto = ArgumentCaptor.forClass(DocCreateDTO.class);
        verify(documents).create(eq(9L), eq(7L), dto.capture());
        assertEquals(DocType.FOLDER, dto.getValue().getType());
        assertNull(dto.getValue().getParentId());
        assertEquals(true, ((Map<?, ?>) dto.getValue().getExtraMeta().get("hellodoc_ai")).get("item"));
        verify(permissions).checkKbRole(9L, 7L, KbRole.EDITOR);
    }

    @Test
    void rejectsDocumentSaveToFolderInAnotherKnowledgeBase() {
        DocumentService documents = mock(DocumentService.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        KbKnowledgeBase other = new KbKnowledgeBase(); other.setId(8L);
        when(documents.getById(10L)).thenReturn(folder(10L, other, Map.of("hellodoc_ai", Map.of("item", true))));
        var service = new AiItemService(documents, permissions);
        assertThrows(BusinessException.class, () -> service.createDocument(9L, 7L, 10L, "标题", "# 内容"));
        verify(documents, never()).create(any(), any(), any());
    }

    @Test
    void createsDocumentOnlyUnderEditableMarkedRoot() {
        DocumentService documents = mock(DocumentService.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        KbKnowledgeBase kb = new KbKnowledgeBase(); kb.setId(7L);
        when(documents.getById(10L)).thenReturn(folder(10L, kb, Map.of("hellodoc_ai", Map.of("item", true))));
        when(documents.create(eq(9L), eq(7L), any())).thenAnswer(invocation -> {
            KbDocument doc = new KbDocument(); doc.setId(99L); return doc;
        });
        KbDocument result = new AiItemService(documents, permissions).createDocument(9L, 7L, 10L, "标题", "# 内容");
        assertEquals(99L, result.getId());
        verify(permissions).checkKbRole(9L, 7L, KbRole.EDITOR);
        verify(permissions).checkDocRole(9L, 10L, DocRole.EDITOR);
        var dto = ArgumentCaptor.forClass(DocCreateDTO.class);
        verify(documents).create(eq(9L), eq(7L), dto.capture());
        assertEquals(10L, dto.getValue().getParentId());
        assertEquals("# 内容", dto.getValue().getContent());
        assertEquals(DocType.FILE, dto.getValue().getType());
    }

    @Test
    void rejectsNestedOrUnmarkedFoldersAsSaveTargets() {
        DocumentService documents = mock(DocumentService.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        KbKnowledgeBase kb = new KbKnowledgeBase(); kb.setId(7L);
        KbDocument nested = folder(10L, kb, Map.of("hellodoc_ai", Map.of("item", true)));
        nested.setParent(folder(20L, kb, Map.of()));
        KbDocument unmarked = folder(11L, kb, Map.of());
        when(documents.getById(10L)).thenReturn(nested);
        when(documents.getById(11L)).thenReturn(unmarked);
        var service = new AiItemService(documents, permissions);
        assertThrows(BusinessException.class, () -> service.createDocument(9L, 7L, 10L, "标题", "内容"));
        assertThrows(BusinessException.class, () -> service.createDocument(9L, 7L, 11L, "标题", "内容"));
        verify(documents, never()).create(any(), any(), any());
    }

    @Test
    void rejectsWritesWhenFolderEditPermissionIsDenied() {
        DocumentService documents = mock(DocumentService.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        KbKnowledgeBase kb = new KbKnowledgeBase(); kb.setId(7L);
        when(documents.getById(10L)).thenReturn(folder(10L, kb, Map.of("hellodoc_ai", Map.of("item", true))));
        doThrow(new BusinessException(com.nopkg.hellodoc.web.ApiResponse.Code.NO_PERMISSION))
                .when(permissions).checkDocRole(9L, 10L, DocRole.EDITOR);
        assertThrows(BusinessException.class, () -> new AiItemService(documents, permissions)
                .createDocument(9L, 7L, 10L, "标题", "内容"));
        verify(documents, never()).create(any(), any(), any());
    }

    @Test
    void createsUnderOrdinaryNestedFolderAndKnowledgeBaseRoot() {
        DocumentService documents = mock(DocumentService.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        KbKnowledgeBase kb = new KbKnowledgeBase(); kb.setId(7L);
        KbDocument nested = folder(10L, kb, Map.of()); nested.setParent(folder(20L, kb, Map.of()));
        when(documents.getById(10L)).thenReturn(nested);
        when(documents.create(eq(9L), eq(7L), any())).thenAnswer(invocation -> { var doc = new KbDocument(); doc.setId(99L); return doc; });
        var service = new AiItemService(documents, permissions);
        service.createDocumentInFolder(9L, 7L, 10L, "nested", "text", Map.of());
        service.createDocumentInFolder(9L, 7L, null, "root", "text", Map.of());
        var dto = ArgumentCaptor.forClass(DocCreateDTO.class);
        verify(documents, times(2)).create(eq(9L), eq(7L), dto.capture());
        assertEquals(10L, dto.getAllValues().get(0).getParentId());
        assertNull(dto.getAllValues().get(1).getParentId());
        verify(permissions).checkDocRole(9L, 10L, DocRole.EDITOR);
    }

    private static KbDocument folder(long id, KbKnowledgeBase kb, Map<String, Object> meta) {
        KbDocument doc = new KbDocument(); doc.setId(id); doc.setName("事项" + id);
        doc.setKb(kb); doc.setType(DocType.FOLDER); doc.setExtraMeta(meta); return doc;
    }
}
