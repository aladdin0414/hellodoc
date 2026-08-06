package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.DocComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocCommentRepository extends JpaRepository<DocComment, Long> {
    List<DocComment> findByDocIdAndDeletedAtIsNull(Long docId);

    List<DocComment> findByDocIdAndParentIdIsNullAndDeletedAtIsNull(Long docId);

    List<DocComment> findByParentIdAndDeletedAtIsNull(Long parentId);

    Integer countByDocIdAndIsResolvedFalseAndDeletedAtIsNull(Long docId);

    Integer countByDocIdAndParentIdIsNullAndIsResolvedFalseAndDeletedAtIsNull(Long docId);
}
