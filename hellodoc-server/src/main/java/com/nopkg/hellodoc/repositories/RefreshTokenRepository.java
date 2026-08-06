package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.SysRefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<SysRefreshToken, Long> {

    Optional<SysRefreshToken> findByJti(String jti);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SysRefreshToken t join fetch t.user where t.jti = :jti")
    Optional<SysRefreshToken> findByJtiForUpdate(@Param("jti") String jti);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SysRefreshToken t set t.revokedAt = :revokedAt, t.revokedReason = :reason " +
            "where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SysRefreshToken t set t.revokedAt = :revokedAt, t.revokedReason = :reason " +
            "where t.user.id = :userId and t.revokedAt is null")
    int revokeByUserId(@Param("userId") Long userId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason);
}
