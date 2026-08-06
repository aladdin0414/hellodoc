package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.KbAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KbAssetRepository extends JpaRepository<KbAsset, Long> {

    Optional<KbAsset> findByIdAndDeletedAtIsNull(Long id);

    List<KbAsset> findByKb_IdAndDeletedAtIsNullOrderByCreatedAtDesc(Long kbId);

    List<KbAsset> findByDoc_IdAndDeletedAtIsNullOrderByCreatedAtDesc(Long docId);
}
