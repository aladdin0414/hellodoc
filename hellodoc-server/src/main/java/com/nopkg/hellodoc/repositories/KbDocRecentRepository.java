package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.KbDocRecent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KbDocRecentRepository extends JpaRepository<KbDocRecent, Long> {
    Optional<KbDocRecent> findByUserIdAndDocId(Long userId, Long docId);

    List<KbDocRecent> findByUserIdOrderByVisitedAtDesc(Long userId, Pageable pageable);

    void deleteByUserId(Long userId);
}
