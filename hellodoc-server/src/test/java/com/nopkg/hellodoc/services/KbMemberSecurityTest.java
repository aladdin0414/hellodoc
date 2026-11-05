package com.nopkg.hellodoc.services;
import com.nopkg.hellodoc.entities.KbKbMember;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.mcp.DirectoryRevision;
import com.nopkg.hellodoc.repositories.KbKbMemberRepository;
import com.nopkg.hellodoc.repositories.KbKnowledgeBaseRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
class KbMemberSecurityTest {
    @Test void ownerRoleCannotBeGrantedToAnotherUser() {
        var members = mock(KbKbMemberRepository.class);
        var kbs = mock(KbKnowledgeBaseRepository.class);
        var revisions = mock(DirectoryRevision.class);
        var kb = new KbKnowledgeBase(); kb.setId(9L); kb.setOwnerId(7L);
        when(revisions.lock(9L)).thenReturn(kb);
        when(kbs.findById(9L)).thenReturn(Optional.of(kb));
        var service = new KbMemberService(members, kbs, mock(com.nopkg.hellodoc.repositories.KbDocPermissionRepository.class), revisions);
        assertThrows(RuntimeException.class, () -> service.addMember(7L, 9L, 8L, KbRole.OWNER));
        var member = new KbKbMember(); member.setKb(kb); member.setRole(KbRole.EDITOR);
        when(members.findByKbIdAndUserId(9L, 8L)).thenReturn(Optional.of(member));
        assertThrows(RuntimeException.class, () -> service.updateMemberRole(7L, 9L, 8L, KbRole.OWNER));
    }
    @Test void removingMemberRevokesOnlyTheirDocumentGrantsInThatKnowledgeBase() {
        var members = mock(KbKbMemberRepository.class);
        var kbs = mock(KbKnowledgeBaseRepository.class);
        var permissions = mock(com.nopkg.hellodoc.repositories.KbDocPermissionRepository.class);
        var revisions = mock(DirectoryRevision.class);
        var kb = new KbKnowledgeBase(); kb.setId(9L); kb.setOwnerId(7L);
        var otherKb = new KbKnowledgeBase(); otherKb.setId(10L);
        var member = new KbKbMember(); member.setKb(kb); member.setRole(KbRole.VIEWER);
        var doc = new com.nopkg.hellodoc.entities.KbDocument(); doc.setKb(kb);
        var otherDoc = new com.nopkg.hellodoc.entities.KbDocument(); otherDoc.setKb(otherKb);
        var grant = new com.nopkg.hellodoc.entities.KbDocPermission(); grant.setDoc(doc);
        var otherGrant = new com.nopkg.hellodoc.entities.KbDocPermission(); otherGrant.setDoc(otherDoc);
        when(revisions.lock(9L)).thenReturn(kb);
        when(members.findByKbIdAndUserId(9L, 8L)).thenReturn(Optional.of(member));
        when(permissions.findByTargetTypeAndTargetId(com.nopkg.hellodoc.enums.TargetType.USER, 8L)).thenReturn(java.util.List.of(grant, otherGrant));
        new KbMemberService(members, kbs, permissions, revisions).removeMember(7L, 9L, 8L);
        verify(permissions).deleteAll(java.util.List.of(grant));
        verify(members).delete(member);
    }
    @Test void cannotRevokePermissionFromAnotherDocument() {
        var permissions = mock(com.nopkg.hellodoc.repositories.KbDocPermissionRepository.class);
        var doc = new com.nopkg.hellodoc.entities.KbDocument(); doc.setId(2L);
        var grant = new com.nopkg.hellodoc.entities.KbDocPermission(); grant.setDoc(doc);
        when(permissions.findById(5L)).thenReturn(Optional.of(grant));
        var service = new KbDocPermissionService(permissions, mock(com.nopkg.hellodoc.repositories.KbDocumentRepository.class), mock(DirectoryRevision.class));
        assertThrows(RuntimeException.class, () -> service.removePermission(1L, 5L));
        verify(permissions, never()).deleteById(anyLong());
    }
}
