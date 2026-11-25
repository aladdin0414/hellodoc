package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuickNotePreferenceServiceTest {
    @Test
    void savesOnlyAnEditableKnowledgeBaseAndAllowsClearing() {
        UserService users = mock(UserService.class);
        UserRepository repository = mock(UserRepository.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        SysUser user = new SysUser(); user.setId(5L);
        when(users.getUserByUsername("alice")).thenReturn(Optional.of(user));
        QuickNotePreferenceService service = new QuickNotePreferenceService(users, repository, permissions);
        service.update("alice", 12L);
        verify(permissions).checkKbRole(5L, 12L, KbRole.EDITOR);
        assertEquals(12L, user.getQuickNoteKbId());
        service.update("alice", null);
        assertNull(user.getQuickNoteKbId());
        verify(repository, times(2)).save(user);
    }
    @Test
    void doesNotSaveWhenPermissionIsDenied() {
        UserService users = mock(UserService.class);
        UserRepository repository = mock(UserRepository.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        SysUser user = new SysUser(); user.setId(5L);
        when(users.getUserByUsername("alice")).thenReturn(Optional.of(user));
        doThrow(new IllegalStateException()).when(permissions).checkKbRole(5L, 12L, KbRole.EDITOR);
        QuickNotePreferenceService service = new QuickNotePreferenceService(users, repository, permissions);
        assertThrows(IllegalStateException.class, () -> service.update("alice", 12L));
        verify(repository, never()).save(any());
    }
}
