package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.KbDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KbDocumentRepository extends JpaRepository<KbDocument, Long> {
    List<KbDocument> findByKbIdAndDeletedAtIsNull(Long kbId);

    List<KbDocument> findByKbIdAndParentIdAndDeletedAtIsNull(Long kbId, Long parentId);

    Optional<KbDocument> findByIdAndDeletedAtIsNull(Long id);

    Optional<KbDocument> findByKbIdAndSlugAndDeletedAtIsNull(Long kbId, String slug);

    Optional<KbDocument> findFirstByKbIdAndNameAndDeletedAtIsNull(Long kbId, String name);

    Optional<KbDocument> findFirstByKbIdAndIsCoverTrueAndDeletedAtIsNull(Long kbId);

    @Query(nativeQuery = true, value = "SELECT * FROM kb_document WHERE path <@ CAST(:parentPath AS ltree) AND deleted_at IS NULL")
    List<KbDocument> findDescendants(@Param("parentPath") String parentPath);

    List<KbDocument> findByKbIdAndIsCoverTrue(Long kbId);

    List<KbDocument> findByKbIdAndStatusAndDeletedAtIsNull(Long kbId, com.nopkg.hellodoc.enums.DocStatus status);

    @Query(nativeQuery = true, value = """
            WITH RECURSIVE doc_tree AS (
                SELECT id, parent_id FROM kb_document
                WHERE kb_id = :kbId AND status = 'published' AND deleted_at IS NULL
                UNION ALL
                SELECT p.id, p.parent_id FROM kb_document p
                INNER JOIN doc_tree t ON p.id = t.parent_id
                WHERE p.deleted_at IS NULL
            )
            SELECT * FROM kb_document WHERE id IN (SELECT id FROM doc_tree)
            """)
    List<KbDocument> findAllPublishedAndAncestors(@Param("kbId") Long kbId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE KbDocument d SET d.viewCount = COALESCE(d.viewCount, 0) + 1 WHERE d.id = :docId")
    void incrementViewCount(@Param("docId") Long docId);

    @Query("SELECT MAX(d.orderNum) FROM KbDocument d WHERE d.kb.id = :kbId AND d.parent.id = :parentId AND d.deletedAt IS NULL")
    Integer findMaxOrderNumByKbIdAndParentId(@Param("kbId") Long kbId, @Param("parentId") Long parentId);

    @Query("SELECT MAX(d.orderNum) FROM KbDocument d WHERE d.kb.id = :kbId AND d.parent IS NULL AND d.deletedAt IS NULL")
    Integer findMaxOrderNumByKbIdAndParentIsNull(@Param("kbId") Long kbId);

    @Query("SELECT MIN(d.orderNum) FROM KbDocument d WHERE d.kb.id = :kbId AND d.parent.id = :parentId AND d.deletedAt IS NULL")
    Integer findMinOrderNumByKbIdAndParentId(@Param("kbId") Long kbId, @Param("parentId") Long parentId);

    @Query("SELECT MIN(d.orderNum) FROM KbDocument d WHERE d.kb.id = :kbId AND d.parent IS NULL AND d.deletedAt IS NULL")
    Integer findMinOrderNumByKbIdAndParentIsNull(@Param("kbId") Long kbId);

    @Query(nativeQuery = true, value = """
            SELECT * FROM kb_document
            WHERE kb_id = :kbId
            AND (id = :rootId OR path <@ (SELECT path FROM kb_document WHERE id = :rootId))
            AND deleted_at IS NULL
            """)
    List<KbDocument> findSubtree(@Param("kbId") Long kbId, @Param("rootId") Long rootId);

    List<KbDocument> findByKbIdAndParentId(Long kbId, Long parentId);

    List<KbDocument> findByKbIdAndParentIsNull(Long kbId);

    @Query(nativeQuery = true, value = """
            SELECT * FROM kb_document
            WHERE kb_id = :kbId
              AND deleted_at IS NOT NULL
              AND (parent_id IS NULL OR parent_id NOT IN (
                  SELECT id FROM kb_document WHERE kb_id = :kbId AND deleted_at IS NOT NULL
              ))
            ORDER BY deleted_at DESC
            """)
    List<KbDocument> findTrashTopItems(@Param("kbId") Long kbId);

    @Query(nativeQuery = true, value = """
            SELECT * FROM kb_document
            WHERE kb_id = :kbId
              AND deleted_at IS NOT NULL
            """)
    List<KbDocument> findAllTrashItems(@Param("kbId") Long kbId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE KbDocument d SET d.parent = null WHERE d.id IN :ids")
    void clearParentByIds(@Param("ids") List<Long> ids);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE KbDocument d SET d.parent = null WHERE d.kb.id = :kbId AND d.deletedAt IS NOT NULL")
    void clearTrashParent(@Param("kbId") Long kbId);
}
