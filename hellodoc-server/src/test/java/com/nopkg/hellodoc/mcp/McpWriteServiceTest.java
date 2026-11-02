package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.services.DocumentService;
import com.nopkg.hellodoc.services.PermissionChecker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class McpWriteServiceTest {
    @Test
    void itemWriteIsScopedToUserAndRecordsRequestMetadata() {
        var guard = mock(McpWriteGuard.class);
        var items = mock(AiItemService.class);
        var docs = mock(DocumentService.class);
        var permissions = mock(PermissionChecker.class);
        var em = mock(EntityManager.class);
        when(guard.execute(eq(2L), eq(3L), eq("item"), eq("req"), anyString(), any()))
                .thenAnswer(call -> ((LongSupplier) call.getArgument(5)).getAsLong());
        when(docs.getTree(3L)).thenReturn(List.of());
        when(items.createItem(eq(2L), eq(3L), eq("事项"), eq("说明"), eq(List.of()), anyMap()))
                .thenReturn(new AiItemService.Item(10L, "事项", "说明", List.of()));
        long id = new McpWriteService(guard, items, docs, permissions, em)
                .createItem(2, 3, "事项", "说明", List.of(), "req");
        assertEquals(10L, id);
        verify(permissions).checkKbRole(2L, 3L, KbRole.EDITOR);
        verify(items).createItem(eq(2L), eq(3L), eq("事项"), eq("说明"), eq(List.of()), argThat(meta -> {
            Map<?, ?> request = (Map<?, ?>) meta.get("hellodoc_mcp_request");
            return request != null && "req".equals(request.get("id")) && "item".equals(request.get("type"));
        }));
        verify(em).flush();
    }
}
