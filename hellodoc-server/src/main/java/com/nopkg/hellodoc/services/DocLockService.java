package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.DocLock;
import com.nopkg.hellodoc.enums.LockType;
import java.util.List;
import java.util.Optional;

public interface DocLockService {
    boolean acquireLock(Long docId, Long userId, String sessionId, LockType lockType, String blockId);

    void releaseLock(Long docId, String sessionId, LockType lockType, String blockId);

    Optional<DocLock> checkLock(Long docId, LockType lockType, String blockId);

    List<DocLock> getDocLocks(Long docId);

    void releaseAllLocksForSession(String sessionId);

    void refreshLocksForSession(String sessionId);

    void cleanupExpiredLocks();
}
