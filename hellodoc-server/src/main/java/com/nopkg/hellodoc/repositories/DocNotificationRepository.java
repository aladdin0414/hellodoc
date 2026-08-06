package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.DocNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocNotificationRepository extends JpaRepository<DocNotification, Long> {
    Page<DocNotification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<DocNotification> findByUserIdAndIsReadOrderByCreatedAtDesc(Long userId, Boolean isRead, Pageable pageable);

    Integer countByUserIdAndIsReadFalse(Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE DocNotification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.isRead = false")
    void markAllAsReadByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}
