package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.KbDocSearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KbDocSearchRepository extends JpaRepository<KbDocSearch, Long> {
    Optional<KbDocSearch> findByDoc_Id(Long docId);

    void deleteByDoc_Id(Long docId);
}
