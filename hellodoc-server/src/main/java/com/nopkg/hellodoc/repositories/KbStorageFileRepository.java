package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.KbStorageFile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface KbStorageFileRepository extends JpaRepository<KbStorageFile, Long> {

    Optional<KbStorageFile> findByContentHashAndDeletedAtIsNull(String contentHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<KbStorageFile> findFirstByContentHashOrderByIdAsc(String contentHash);

    Optional<KbStorageFile> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from KbStorageFile f where f.id = :id")
    Optional<KbStorageFile> findByIdForUpdate(@Param("id") Long id);

    List<KbStorageFile> findByRefCountAndDeletedAtBefore(Integer refCount, OffsetDateTime deletedAt);
}
