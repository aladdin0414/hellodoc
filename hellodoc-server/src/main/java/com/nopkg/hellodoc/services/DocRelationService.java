package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.enums.RelationType;

import java.util.List;

public interface DocRelationService {

    record DocLinkVO(
            Long id,
            Long kbId,
            String kbTitle,
            String name,
            String slug,
            String type,
            RelationType relationType
    ) {
    }

    record DocNodeVO(
            Long id,
            Long kbId,
            String kbTitle,
            String name,
            String slug,
            Integer depth
    ) {
    }

    record DocEdgeVO(
            Long sourceId,
            Long targetId,
            RelationType type
    ) {
    }

    record DocGraphVO(
            DocNodeVO center,
            List<DocNodeVO> nodes,
            List<DocEdgeVO> edges
    ) {
    }

    void createRelation(Long sourceDocId, Long targetDocId, RelationType type);

    void removeRelation(Long sourceDocId, Long targetDocId);

    void updateRelationType(Long sourceDocId, Long targetDocId, RelationType type);

    List<DocLinkVO> getOutgoingLinks(Long userId, Long docId);

    List<DocLinkVO> getBacklinks(Long userId, Long docId);

    DocGraphVO getRelationGraph(Long userId, Long docId, int depth);

    void syncRelationsFromContent(Long docId, String content);
}
