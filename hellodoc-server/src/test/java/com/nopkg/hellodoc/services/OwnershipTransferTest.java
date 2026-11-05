package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.*;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.mcp.DirectoryRevision;
import com.nopkg.hellodoc.repositories.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnershipTransferTest {
    @Test void transfersOwnerAndDemotesFormerOwner() {
        var kbs=mock(KbKnowledgeBaseRepository.class); var members=mock(KbKbMemberRepository.class);
        var users=mock(UserRepository.class); var revision=mock(DirectoryRevision.class);
        var kb=new KbKnowledgeBase(); kb.setId(1L); kb.setOwnerId(2L);
        var oldMember=new KbKbMember(); oldMember.setKb(kb); oldMember.setUserId(2L); oldMember.setRole(KbRole.OWNER);
        var newMember=new KbKbMember(); newMember.setKb(kb); newMember.setUserId(3L); newMember.setRole(KbRole.VIEWER);
        var target=new SysUser(); target.setId(3L); target.setStatus((short)0);
        when(revision.lock(1L)).thenReturn(kb); when(users.findById(3L)).thenReturn(Optional.of(target));
        when(members.findByKbIdAndUserId(1L,2L)).thenReturn(Optional.of(oldMember));
        when(members.findByKbIdAndUserId(1L,3L)).thenReturn(Optional.of(newMember));
        var service=new KbService(kbs,members,mock(KbKbUserPrefRepository.class),mock(UserService.class),users,mock(KbDocPermissionRepository.class),revision);
        service.transferOwnership(1L,2L,2L,3L,true,false);
        assertEquals(3L,kb.getOwnerId()); assertEquals(KbRole.ADMIN,oldMember.getRole()); assertEquals(KbRole.OWNER,newMember.getRole());
        verify(revision).bump(kb);
    }
    @Test void rejectsUnauthorizedStaleAndDisabledTransfers() {
        var kbs=mock(KbKnowledgeBaseRepository.class); var members=mock(KbKbMemberRepository.class);
        var users=mock(UserRepository.class); var revision=mock(DirectoryRevision.class);
        var kb=new KbKnowledgeBase(); kb.setId(1L); kb.setOwnerId(2L);
        when(revision.lock(1L)).thenReturn(kb);
        var service=new KbService(kbs,members,mock(KbKbUserPrefRepository.class),mock(UserService.class),users,mock(KbDocPermissionRepository.class),revision);
        assertThrows(RuntimeException.class, () -> service.transferOwnership(1L,4L,2L,3L,true,false));
        assertThrows(RuntimeException.class, () -> service.transferOwnership(1L,4L,7L,3L,true,true));
        var disabled=new SysUser(); disabled.setId(3L); disabled.setStatus((short)1);
        when(users.findById(3L)).thenReturn(Optional.of(disabled));
        assertThrows(RuntimeException.class, () -> service.transferOwnership(1L,2L,2L,3L,true,false));
        assertEquals(2L,kb.getOwnerId());
        verify(members, never()).save(any());
    }
}
