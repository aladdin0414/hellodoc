package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import java.util.List;

public interface KbKnowledgeBaseRepository extends JpaRepository<KbKnowledgeBase, Long> {
    List<KbKnowledgeBase> findByOwnerIdAndDeletedAtIsNull(Long ownerId);

    List<KbKnowledgeBase> findByVisibilityAndDeletedAtIsNull(Visibility visibility);

    Optional<KbKnowledgeBase> findByIdAndDeletedAtIsNull(Long id);

    List<KbKnowledgeBase> findByIdInAndDeletedAtIsNull(List<Long> ids);
}
