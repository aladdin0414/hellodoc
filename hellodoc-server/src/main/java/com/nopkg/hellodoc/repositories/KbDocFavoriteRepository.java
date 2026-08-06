package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.KbDocFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KbDocFavoriteRepository extends JpaRepository<KbDocFavorite, Long> {
    Optional<KbDocFavorite> findByUserIdAndDocId(Long userId, Long docId);

    List<KbDocFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndDocId(Long userId, Long docId);
}
