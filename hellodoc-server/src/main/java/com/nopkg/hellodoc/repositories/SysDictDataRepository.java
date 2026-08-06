package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.SysDictDatum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysDictDataRepository extends JpaRepository<SysDictDatum, Long> {
    List<SysDictDatum> findByDictCodeAndStatusOrderBySortOrder(String dictCode, Short status);

    List<SysDictDatum> findByDictTypeId(Long typeId);

    Optional<SysDictDatum> findByDictCodeAndValue(String dictCode, String value);

    boolean existsByDictTypeIdAndValue(Long dictTypeId, String value);

    void deleteByDictTypeId(Long typeId);
}
