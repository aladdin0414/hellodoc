package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.DocLock;
import com.nopkg.hellodoc.enums.LockType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocLockRepository extends JpaRepository<DocLock, Long> {
    Optional<DocLock> findByDocIdAndBlockIdAndLockType(Long docId, String blockId, LockType lockType);

    Optional<DocLock> findByDocIdAndLockType(Long docId, LockType lockType);

    List<DocLock> findByDocId(Long docId);

    List<DocLock> findBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);

    void deleteByExpiresAtBefore(LocalDateTime now);
}
