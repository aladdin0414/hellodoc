package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.DocOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocOperationRepository extends JpaRepository<DocOperation, Long> {
    List<DocOperation> findByDocIdAndServerVersionGreaterThanOrderByServerVersionAsc(Long docId, Integer version);

    DocOperation findTopByDocIdOrderByServerVersionDesc(Long docId);
}
