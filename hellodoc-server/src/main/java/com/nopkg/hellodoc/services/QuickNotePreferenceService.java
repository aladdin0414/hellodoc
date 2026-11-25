package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.exceptions.ResourceNotFoundException;
import com.nopkg.hellodoc.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuickNotePreferenceService {
    private final UserService users;
    private final UserRepository repository;
    private final PermissionChecker permissions;

    @Transactional
    public void update(String username, Long kbId) {
        SysUser user = users.getUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
        if (kbId != null) permissions.checkKbRole(user.getId(), kbId, KbRole.EDITOR);
        user.setQuickNoteKbId(kbId);
        repository.save(user);
    }
}
