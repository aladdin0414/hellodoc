package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.DocSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DocSessionRepository extends JpaRepository<DocSession, Long> {
    List<DocSession> findByDocIdAndIsActiveTrue(Long docId);

    DocSession findBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);

    void deleteByLastHeartbeatBefore(LocalDateTime cutoff);
}
