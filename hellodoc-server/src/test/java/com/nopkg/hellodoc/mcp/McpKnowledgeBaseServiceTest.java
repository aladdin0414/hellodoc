package com.nopkg.hellodoc.mcp;
import com.nopkg.hellodoc.entities.*;
import com.nopkg.hellodoc.enums.*;
import com.nopkg.hellodoc.repositories.*;
import com.nopkg.hellodoc.services.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class McpKnowledgeBaseServiceTest {
    @Test void listsOwnedPublicAndDirectlySharedKnowledgeBasesWithoutDuplicates() {
        var kbService = mock(KbService.class); var kbs = mock(KbKnowledgeBaseRepository.class);
        var grants = mock(KbDocPermissionRepository.class); var permissions = mock(PermissionChecker.class);
        var owned = new KbKnowledgeBase(); owned.setId(1L); owned.setTitle("Owned");
        var shared = new KbKnowledgeBase(); shared.setId(2L); shared.setTitle("Shared");
        var doc = new KbDocument(); doc.setId(8L); doc.setKb(shared);
        var grant = new KbDocPermission(); grant.setDoc(doc); grant.setTargetType(TargetType.USER); grant.setTargetId(7L); grant.setRole(DocRole.VIEWER);
        when(kbService.listAccessibleKnowledgeBases(7L)).thenReturn(List.of(owned));
        when(kbService.listPublicKbs()).thenReturn(List.of(owned));
        when(grants.findByTargetTypeAndTargetId(TargetType.USER, 7L)).thenReturn(List.of(grant));
        when(permissions.hasKbRole(7L, 1L, KbRole.VIEWER)).thenReturn(true);
        when(permissions.hasDocRole(7L, 8L, DocRole.VIEWER)).thenReturn(true);
        var result = new McpKnowledgeBaseService(kbService,kbs,grants,permissions).list(7L, 0, 50);
        var items = (List<?>)result.get("knowledgeBases"); assertEquals(2,items.size());
        assertEquals(false, ((Map<?,?>)items.get(1)).get("canBrowseRoot"));
    }
}
