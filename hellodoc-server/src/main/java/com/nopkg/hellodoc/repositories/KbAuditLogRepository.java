package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.audit.AuditTargetType;
import com.nopkg.hellodoc.entities.KbAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KbAuditLogRepository extends JpaRepository<KbAuditLog, Long> {
    Page<KbAuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(AuditTargetType targetType, Long targetId,
            Pageable pageable);

    Page<KbAuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<KbAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
