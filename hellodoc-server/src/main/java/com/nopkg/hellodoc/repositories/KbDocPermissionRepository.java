package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.KbDocPermission;
import com.nopkg.hellodoc.enums.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KbDocPermissionRepository extends JpaRepository<KbDocPermission, Long> {
    List<KbDocPermission> findByDocId(Long docId);

    Optional<KbDocPermission> findByDocIdAndTargetTypeAndTargetId(Long docId, TargetType targetType, Long targetId);

    Optional<KbDocPermission> findByDocIdAndTargetTypeAndLinkToken(Long docId, TargetType targetType, String linkToken);
}
