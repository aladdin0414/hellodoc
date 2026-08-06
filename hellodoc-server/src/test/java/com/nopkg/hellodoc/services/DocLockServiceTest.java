package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.DocLock;
import com.nopkg.hellodoc.enums.LockType;
import com.nopkg.hellodoc.repositories.DocLockRepository;
import com.nopkg.hellodoc.services.impl.DocLockServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DocLockServiceTest {

    @Mock
    private DocLockRepository lockRepository;

    @InjectMocks
    private DocLockServiceImpl lockService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testAcquireLock_NewLock() {
        when(lockRepository.findByDocIdAndBlockIdAndLockType(1L, "block1", LockType.BLOCK))
                .thenReturn(Optional.empty());
        when(lockRepository.save(any(DocLock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = lockService.acquireLock(1L, 100L, "session1", LockType.BLOCK, "block1");

        assertTrue(result);
        verify(lockRepository).save(any(DocLock.class));
    }

    @Test
    void testAcquireLock_AlreadyHeldBySameSession() {
        DocLock existing = new DocLock();
        existing.setSessionId("session1");
        existing.setExpiresAt(LocalDateTime.now().plusSeconds(10));

        when(lockRepository.findByDocIdAndBlockIdAndLockType(1L, "block1", LockType.BLOCK))
                .thenReturn(Optional.of(existing));

        boolean result = lockService.acquireLock(1L, 100L, "session1", LockType.BLOCK, "block1");

        assertTrue(result);
        verify(lockRepository).save(existing);
        assertTrue(existing.getExpiresAt().isAfter(LocalDateTime.now().plusSeconds(25))); // Renewed
    }

    @Test
    void testAcquireLock_HeldByOther_NotExpired() {
        DocLock existing = new DocLock();
        existing.setSessionId("session2");
        existing.setExpiresAt(LocalDateTime.now().plusSeconds(10));

        when(lockRepository.findByDocIdAndBlockIdAndLockType(1L, "block1", LockType.BLOCK))
                .thenReturn(Optional.of(existing));

        boolean result = lockService.acquireLock(1L, 100L, "session1", LockType.BLOCK, "block1");

        assertFalse(result);
        verify(lockRepository, never()).save(existing);
    }

    @Test
    void testAcquireLock_HeldByOther_Expired() {
        DocLock existing = new DocLock();
        existing.setSessionId("session2");
        existing.setExpiresAt(LocalDateTime.now().minusSeconds(1)); // Expired

        when(lockRepository.findByDocIdAndBlockIdAndLockType(1L, "block1", LockType.BLOCK))
                .thenReturn(Optional.of(existing));

        boolean result = lockService.acquireLock(1L, 100L, "session1", LockType.BLOCK, "block1");

        assertTrue(result);
        assertEquals("session1", existing.getSessionId());
        verify(lockRepository).save(existing);
    }

    @Test
    void testReleaseLock_Success() {
        DocLock existing = new DocLock();
        existing.setSessionId("session1");
        existing.setDocId(1L);
        existing.setBlockId("block1");

        when(lockRepository.findByDocIdAndBlockIdAndLockType(1L, "block1", LockType.BLOCK))
                .thenReturn(Optional.of(existing));

        lockService.releaseLock(1L, "session1", LockType.BLOCK, "block1");

        verify(lockRepository).delete(existing);
    }

    @Test
    void testReleaseLock_WrongSession() {
        DocLock existing = new DocLock();
        existing.setSessionId("session1");

        when(lockRepository.findByDocIdAndBlockIdAndLockType(1L, "block1", LockType.BLOCK))
                .thenReturn(Optional.of(existing));

        lockService.releaseLock(1L, "session2", LockType.BLOCK, "block1");

        verify(lockRepository, never()).delete(existing);
    }

    @Test
    void testReleaseLock_NotFound() {
        when(lockRepository.findByDocIdAndBlockIdAndLockType(1L, "block1", LockType.BLOCK))
                .thenReturn(Optional.empty());

        lockService.releaseLock(1L, "session1", LockType.BLOCK, "block1");

        verify(lockRepository, never()).delete(any(DocLock.class));
    }

    @Test
    void testCheckLock() {
        DocLock lock = new DocLock();
        lock.setDocId(1L);
        lock.setBlockId("block1");
        lock.setSessionId("session1");

        when(lockRepository.findByDocIdAndBlockIdAndLockType(1L, "block1", LockType.BLOCK))
                .thenReturn(Optional.of(lock));

        Optional<DocLock> result = lockService.checkLock(1L, LockType.BLOCK, "block1");

        assertTrue(result.isPresent());
        assertEquals("session1", result.get().getSessionId());
    }

    @Test
    void testGetDocLocks() {
        DocLock lock1 = new DocLock();
        lock1.setBlockId("block1");
        DocLock lock2 = new DocLock();
        lock2.setBlockId("block2");

        when(lockRepository.findByDocId(1L))
                .thenReturn(java.util.Arrays.asList(lock1, lock2));

        var result = lockService.getDocLocks(1L);

        assertEquals(2, result.size());
    }

    @Test
    void testCleanupExpiredLocks() {
        lockService.cleanupExpiredLocks();

        verify(lockRepository)
                .deleteByExpiresAtBefore(argThat(cutoff -> cutoff.isBefore(LocalDateTime.now().plusSeconds(1)) &&
                        cutoff.isAfter(LocalDateTime.now().minusSeconds(1))));
    }
}
