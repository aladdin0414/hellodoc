package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.DocLock;
import com.nopkg.hellodoc.enums.LockType;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.repositories.DocLockRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentWriteLockGuardTest {
    private final DocLockRepository repository = mock(DocLockRepository.class);
    private final DocumentWriteLockGuard guard = new DocumentWriteLockGuard(repository);

    @Test void onlyCurrentOwnerAndSessionCanWriteWhileLocked() {
        DocLock lock = new DocLock();
        lock.setUserId  (12L);
        lock.setSessionId("holder");
        lock.setExpiresAt(LocalDateTime.now().plusSeconds(30));
        when(repository.findByDocIdAndLockType(5L, LockType.DOCUMENT)).thenReturn(Optional.of(lock));
        assertDoesNotThrow(() -> guard.check(5L, 12L, "holder"));
        assertThrows(BusinessException.class, () -> guard.check(5L, 12L, null));
        assertThrows(BusinessException.class, () -> guard.check(5L, 13L, "holder"));
        assertThrows(BusinessException.class, () -> guard.check(5L, 12L, "other"));
    }
    @Test void unlockedDocumentsAllowOtherClientsButStaleSessionsCannotWrite() {
        when(repository.findByDocIdAndLockType(5L, LockType.DOCUMENT)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> guard.check(5L, 12L, null));
        assertThrows(BusinessException.class, () -> guard.check(5L, 12L, "expired"));
    }
    @Test void expiredLockIsNotAnActiveOwner() {
        DocLock lock = new DocLock(); lock.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(repository.findByDocIdAndLockType(5L, LockType.DOCUMENT)).thenReturn(Optional.of(lock));
        assertDoesNotThrow(() -> guard.check(5L, 12L, null));
        assertThrows(BusinessException.class, () -> guard.check(5L, 12L, "stale"));
    }
}
