package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.KbKbMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KbKbMemberRepository extends JpaRepository<KbKbMember, Long> {
    List<KbKbMember> findByUserId(Long userId);

    List<KbKbMember> findByKbId(Long kbId);

    List<KbKbMember> findByKbIdIn(List<Long> kbIds);

    Optional<KbKbMember> findByKbIdAndUserId(Long kbId, Long userId);

    boolean existsByKbIdAndUserId(Long kbId, Long userId);
}
