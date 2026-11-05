package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.repositories.KbKbMemberRepository;
import com.nopkg.hellodoc.repositories.KbKnowledgeBaseRepository;
import com.nopkg.hellodoc.services.KbMemberService;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;

class DirectoryPermissionMutationTest {
    @Test void changingKnowledgeBaseVisibilityInvalidatesTheSnapshot() {
        var kbs = mock(KbKnowledgeBaseRepository.class);
        var revisions = mock(DirectoryRevision.class);
        var kb = new KbKnowledgeBase(); kb.setId(9L); kb.setOwnerId(7L);
        when(revisions.lock(9L)).thenReturn(kb);
        when(kbs.findById(9L)).thenReturn(Optional.of(kb));
        var service = new com.nopkg.hellodoc.services.KbService(kbs, mock(KbKbMemberRepository.class),
                mock(com.nopkg.hellodoc.repositories.KbKbUserPrefRepository.class),
                mock(com.nopkg.hellodoc.services.UserService.class), mock(com.nopkg.hellodoc.repositories.UserRepository.class), mock(com.nopkg.hellodoc.repositories.KbDocPermissionRepository.class), revisions);
        var dto = new com.nopkg.hellodoc.web.dto.kb.KbUpdateDTO();
        dto.setVisibility(com.nopkg.hellodoc.enums.Visibility.PUBLIC);
        service.updateKnowledgeBase(7L, 9L, dto);
        verify(revisions).lock(9L); verify(revisions).bump(kb);
    }
    @Test void documentPermissionChangesInvalidateTheSnapshot() {
        var repo = mock(com.nopkg.hellodoc.repositories.KbDocPermissionRepository.class);
        var docs = mock(com.nopkg.hellodoc.repositories.KbDocumentRepository.class);
        var revisions = mock(DirectoryRevision.class);
        var kb = new KbKnowledgeBase(); kb.setId(9L);
        var doc = new com.nopkg.hellodoc.entities.KbDocument(); doc.setId(1L); doc.setKb(kb);
        when(docs.findById(1L)).thenReturn(Optional.of(doc));
        when(revisions.lock(9L)).thenReturn(kb);
        var service = new com.nopkg.hellodoc.services.KbDocPermissionService(repo, docs, revisions);
        service.addPermission(1L, com.nopkg.hellodoc.enums.TargetType.USER, 8L,
                com.nopkg.hellodoc.enums.DocRole.EDITOR, null);
        verify(revisions).lock(9L);
        verify(revisions).bump(kb);
    }
    @Test void membershipChangesInvalidateTheSnapshot() {
        var repo = mock(KbKbMemberRepository.class);
        var kbs = mock(KbKnowledgeBaseRepository.class);
        var revisions = mock(DirectoryRevision.class);
        var kb = new KbKnowledgeBase(); kb.setId(9L); kb.setOwnerId(7L);
        when(kbs.findById(9L)).thenReturn(Optional.of(kb));
        when(revisions.lock(9L)).thenReturn(kb);
        var service = new KbMemberService(repo, kbs, mock(com.nopkg.hellodoc.repositories.KbDocPermissionRepository.class), revisions);
        service.addMember(7L, 9L, 8L, KbRole.EDITOR);
        verify(revisions).lock(9L);
        verify(revisions).bump(kb);
    }
}
