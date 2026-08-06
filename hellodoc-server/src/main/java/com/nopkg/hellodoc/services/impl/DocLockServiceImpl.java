package com.nopkg.hellodoc.services.impl;

import com.nopkg.hellodoc.entities.DocLock;
import com.nopkg.hellodoc.enums.LockType;
import com.nopkg.hellodoc.repositories.DocLockRepository;
import com.nopkg.hellodoc.services.DocLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocLockServiceImpl implements DocLockService {

    private final DocLockRepository lockRepository;
    private static final int LOCK_TTL_SECONDS = 30;

    @Override
    @Transactional
    public boolean acquireLock(Long docId, Long userId, String sessionId, LockType lockType, String blockId) {
        Optional<DocLock> existing = findExistingLock(docId, lockType, blockId);

        if (existing.isPresent()) {
            DocLock lock = existing.get();
            // If already owned by same session, refresh it
            if (lock.getSessionId().equals(sessionId)) {
                lock.setUserId(userId);
                lock.setExpiresAt(LocalDateTime.now().plusSeconds(LOCK_TTL_SECONDS));
                lockRepository.save(lock);
                return true;
            }
            // Check if expired
            if (lock.getExpiresAt().isBefore(LocalDateTime.now())) {
                // Expired, take over
                lock.setUserId(userId);
                lock.setSessionId(sessionId);
                lock.setAcquiredAt(LocalDateTime.now());
                lock.setExpiresAt(LocalDateTime.now().plusSeconds(LOCK_TTL_SECONDS));
                lockRepository.save(lock);
                return true;
            }
            return false;
        }

        // Create new lock
        if (lockType != LockType.DOCUMENT && blockId == null) {
            return false;
        }
        DocLock lock = new DocLock();
        lock.setDocId(docId);
        lock.setUserId(userId);
        lock.setSessionId(sessionId);
        lock.setBlockId(blockId);
        lock.setLockType(lockType);
        lock.setAcquiredAt(LocalDateTime.now());
        lock.setExpiresAt(LocalDateTime.now().plusSeconds(LOCK_TTL_SECONDS));
        lockRepository.save(lock);
        return true;
    }

    @Override
    @Transactional
    public void releaseLock(Long docId, String sessionId, LockType lockType, String blockId) {
        Optional<DocLock> existing = findExistingLock(docId, lockType, blockId);
        if (existing.isPresent()) {
            DocLock lock = existing.get();
            if (lock.getSessionId().equals(sessionId)) {
                lockRepository.delete(lock);
            }
        }
    }

    @Override
    public Optional<DocLock> checkLock(Long docId, LockType lockType, String blockId) {
        return findExistingLock(docId, lockType, blockId);
    }

    @Override
    public List<DocLock> getDocLocks(Long docId) {
        return lockRepository.findByDocId(docId);
    }

    @Override
    @Transactional
    public void releaseAllLocksForSession(String sessionId) {
        lockRepository.deleteBySessionId(sessionId);
    }

    @Override
    @Transactional
    public void refreshLocksForSession(String sessionId) {
        List<DocLock> locks = lockRepository.findBySessionId(sessionId);
        if (locks.isEmpty()) {
            return;
        }
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(LOCK_TTL_SECONDS);
        for (DocLock lock : locks) {
            lock.setExpiresAt(expiresAt);
        }
        lockRepository.saveAll(locks);
    }

    @Override
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupExpiredLocks() {
        lockRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    private Optional<DocLock> findExistingLock(Long docId, LockType lockType, String blockId) {
        if (lockType == null) {
            return Optional.empty();
        }
        if (lockType == LockType.DOCUMENT) {
            return lockRepository.findByDocIdAndLockType(docId, lockType);
        }
        if (blockId == null) {
            return Optional.empty();
        }
        return lockRepository.findByDocIdAndBlockIdAndLockType(docId, blockId, lockType);
    }
}
