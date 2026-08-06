package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.SysDictType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysDictTypeRepository extends JpaRepository<SysDictType, Long> {
    Optional<SysDictType> findByDictCode(String dictCode);

    List<SysDictType> findByStatus(Short status);

    boolean existsByDictCode(String dictCode);
}
