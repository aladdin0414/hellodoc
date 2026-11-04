package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import java.util.List;

public interface KbKnowledgeBaseRepository extends JpaRepository<KbKnowledgeBase, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select k from KbKnowledgeBase k where k.id = :id and k.deletedAt is null")
    Optional<KbKnowledgeBase> findByIdForTreeUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    List<KbKnowledgeBase> findByAllowAnonymousTrueAndDeletedAtIsNull();
    List<KbKnowledgeBase> findByOwnerIdAndDeletedAtIsNull(Long ownerId);

    List<KbKnowledgeBase> findByVisibilityAndDeletedAtIsNull(Visibility visibility);

    Optional<KbKnowledgeBase> findByIdAndDeletedAtIsNull(Long id);

    List<KbKnowledgeBase> findByIdInAndDeletedAtIsNull(List<Long> ids);
}
