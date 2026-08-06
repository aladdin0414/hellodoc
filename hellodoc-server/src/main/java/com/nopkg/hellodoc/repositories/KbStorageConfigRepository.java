package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.KbStorageConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KbStorageConfigRepository extends JpaRepository<KbStorageConfig, Long> {

    Optional<KbStorageConfig> findByName(String name);

    boolean existsByName(String name);

    Optional<KbStorageConfig> findFirstByIsDefaultTrue();

    List<KbStorageConfig> findByIsActiveTrueOrderByIdAsc();
}
