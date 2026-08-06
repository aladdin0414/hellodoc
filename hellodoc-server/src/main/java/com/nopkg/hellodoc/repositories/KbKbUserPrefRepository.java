package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.KbKbUserPref;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KbKbUserPrefRepository extends JpaRepository<KbKbUserPref, Long> {
    Optional<KbKbUserPref> findByUserIdAndKbId(Long userId, Long kbId);

    List<KbKbUserPref> findByUserId(Long userId);

    List<KbKbUserPref> findByUserIdAndIsPinnedTrueOrderByPinnedAtDesc(Long userId);
}
