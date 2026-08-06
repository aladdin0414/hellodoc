package com.nopkg.hellodoc.services.impl;

import com.nopkg.hellodoc.entities.KbDocRelation;
import com.nopkg.hellodoc.entities.KbDocRelationId;
import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.RelationType;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.exceptions.ResourceNotFoundException;
import com.nopkg.hellodoc.repositories.KbDocRelationRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.services.DocRelationService;
import com.nopkg.hellodoc.services.PermissionChecker;
import com.nopkg.hellodoc.services.relation.DocLinkParser;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DocRelationServiceImpl implements DocRelationService {

    private final KbDocRelationRepository relationRepository;
    private final KbDocumentRepository documentRepository;
    private final PermissionChecker permissionChecker;
    private final DocLinkParser linkParser;

    @Override
    @Transactional
    public void createRelation(Long sourceDocId, Long targetDocId, RelationType type) {
        if (sourceDocId == null || targetDocId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "sourceDocId and targetDocId are required");
        }
        if (Objects.equals(sourceDocId, targetDocId)) {
            return;
        }

        KbDocument source = documentRepository.findByIdAndDeletedAtIsNull(sourceDocId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", sourceDocId));
        KbDocument target = documentRepository.findByIdAndDeletedAtIsNull(targetDocId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", targetDocId));
        if (source.getKb() == null || target.getKb() == null || !Objects.equals(source.getKb().getId(), target.getKb().getId())) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "cross-kb relation is not supported");
        }

        KbDocRelationId id = new KbDocRelationId();
        id.setSourceDocId(sourceDocId);
        id.setTargetDocId(targetDocId);

        KbDocRelation relation = relationRepository.findById(id).orElseGet(() -> {
            KbDocRelation r = new KbDocRelation();
            r.setId(id);
            r.setSourceDoc(source);
            r.setTargetDoc(target);
            r.setCreatedAt(OffsetDateTime.now());
            return r;
        });

        RelationType safeType = type != null ? type : RelationType.LINK;
        relation.setRelationType(safeType.getValue());
        if (relation.getCreatedAt() == null) {
            relation.setCreatedAt(OffsetDateTime.now());
        }
        relationRepository.save(relation);
    }

    @Override
    @Transactional
    public void removeRelation(Long sourceDocId, Long targetDocId) {
        if (sourceDocId == null || targetDocId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "sourceDocId and targetDocId are required");
        }
        relationRepository.deleteByIdSourceDocIdAndIdTargetDocId(sourceDocId, targetDocId);
    }

    @Override
    @Transactional
    public void updateRelationType(Long sourceDocId, Long targetDocId, RelationType type) {
        if (sourceDocId == null || targetDocId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "sourceDocId and targetDocId are required");
        }
        KbDocRelationId id = new KbDocRelationId();
        id.setSourceDocId(sourceDocId);
        id.setTargetDocId(targetDocId);

        KbDocRelation relation = relationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, "relation not found"));

        RelationType safeType = type != null ? type : RelationType.LINK;
        relation.setRelationType(safeType.getValue());
        relationRepository.save(relation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocLinkVO> getOutgoingLinks(Long userId, Long docId) {
        if (docId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "docId is required");
        }
        permissionChecker.checkDocRole(userId, docId, DocRole.VIEWER);

        List<KbDocRelation> relations = relationRepository.findByIdSourceDocId(docId);
        List<DocLinkVO> result = new ArrayList<>();
        for (KbDocRelation rel : relations) {
            KbDocument target = rel.getTargetDoc();
            if (target == null || target.getDeletedAt() != null) {
                continue;
            }
            Long targetId = target.getId();
            if (targetId == null) {
                continue;
            }
            if (!permissionChecker.hasDocRole(userId, targetId, DocRole.VIEWER)) {
                continue;
            }
            result.add(toLinkVO(target, rel.getRelationType()));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocLinkVO> getBacklinks(Long userId, Long docId) {
        if (docId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "docId is required");
        }
        permissionChecker.checkDocRole(userId, docId, DocRole.VIEWER);

        List<KbDocRelation> relations = relationRepository.findByIdTargetDocId(docId);
        List<DocLinkVO> result = new ArrayList<>();
        for (KbDocRelation rel : relations) {
            KbDocument source = rel.getSourceDoc();
            if (source == null || source.getDeletedAt() != null) {
                continue;
            }
            Long sourceId = source.getId();
            if (sourceId == null) {
                continue;
            }
            if (!permissionChecker.hasDocRole(userId, sourceId, DocRole.VIEWER)) {
                continue;
            }
            result.add(toLinkVO(source, rel.getRelationType()));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public DocGraphVO getRelationGraph(Long userId, Long docId, int depth) {
        if (docId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "docId is required");
        }
        int maxDepth = Math.max(0, Math.min(10, depth));
        permissionChecker.checkDocRole(userId, docId, DocRole.VIEWER);

        KbDocument centerDoc = documentRepository.findByIdAndDeletedAtIsNull(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId));

        Map<Long, Integer> distances = new LinkedHashMap<>();
        List<DocEdgeVO> edges = new ArrayList<>();
        Set<String> edgeKeys = new HashSet<>();

        ArrayDeque<Long> queue = new ArrayDeque<>();
        distances.put(docId, 0);
        queue.offer(docId);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            Integer currentDepth = distances.get(current);
            if (currentDepth == null || currentDepth >= maxDepth) {
                continue;
            }

            for (KbDocRelation rel : relationRepository.findByIdSourceDocId(current)) {
                Long to = rel.getId() != null ? rel.getId().getTargetDocId() : null;
                if (to == null || Objects.equals(to, current)) {
                    continue;
                }
                if (!permissionChecker.hasDocRole(userId, to, DocRole.VIEWER)) {
                    continue;
                }
                addEdge(edges, edgeKeys, current, to, rel.getRelationType());
                if (!distances.containsKey(to)) {
                    distances.put(to, currentDepth + 1);
                    queue.offer(to);
                }
            }

            for (KbDocRelation rel : relationRepository.findByIdTargetDocId(current)) {
                Long from = rel.getId() != null ? rel.getId().getSourceDocId() : null;
                if (from == null || Objects.equals(from, current)) {
                    continue;
                }
                if (!permissionChecker.hasDocRole(userId, from, DocRole.VIEWER)) {
                    continue;
                }
                addEdge(edges, edgeKeys, from, current, rel.getRelationType());
                if (!distances.containsKey(from)) {
                    distances.put(from, currentDepth + 1);
                    queue.offer(from);
                }
            }
        }

        Map<Long, KbDocument> docMap = new HashMap<>();
        for (Long id : distances.keySet()) {
            documentRepository.findByIdAndDeletedAtIsNull(id).ifPresent(d -> docMap.put(id, d));
        }

        List<DocNodeVO> nodes = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : distances.entrySet()) {
            KbDocument d = docMap.get(e.getKey());
            if (d == null) {
                continue;
            }
            nodes.add(new DocNodeVO(d.getId(),
                    d.getKb() != null ? d.getKb().getId() : null,
                    d.getKb() != null ? d.getKb().getTitle() : "",
                    d.getName(),
                    d.getSlug(),
                    e.getValue()));
        }

        DocNodeVO center = new DocNodeVO(centerDoc.getId(),
                centerDoc.getKb() != null ? centerDoc.getKb().getId() : null,
                centerDoc.getKb() != null ? centerDoc.getKb().getTitle() : "",
                centerDoc.getName(),
                centerDoc.getSlug(),
                0);

        return new DocGraphVO(center, nodes, edges);
    }

    @Override
    @Transactional
    public void syncRelationsFromContent(Long docId, String content) {
        if (docId == null) {
            return;
        }
        KbDocument source = documentRepository.findByIdAndDeletedAtIsNull(docId).orElse(null);
        if (source == null || source.getKb() == null) {
            return;
        }

        List<DocLinkParser.ParsedLink> parsedLinks = linkParser.parseLinks(content);

        Set<Long> newTargets = new LinkedHashSet<>();
        Map<Long, RelationType> targetTypes = new LinkedHashMap<>();
        for (DocLinkParser.ParsedLink link : parsedLinks) {
            if (link == null || link.ref() == null) {
                continue;
            }
            KbDocument target = resolveTargetInSameKb(source.getKb().getId(), link.ref());
            if (target == null || target.getDeletedAt() != null) {
                continue;
            }
            if (Objects.equals(target.getId(), docId)) {
                continue;
            }
            newTargets.add(target.getId());
            targetTypes.put(target.getId(), link.type());
        }

        List<KbDocRelation> existing = relationRepository.findByIdSourceDocId(docId);
        Set<Long> existingTargets = new LinkedHashSet<>();
        for (KbDocRelation rel : existing) {
            if (rel.getId() != null && rel.getId().getTargetDocId() != null) {
                existingTargets.add(rel.getId().getTargetDocId());
            }
        }

        for (Long targetId : newTargets) {
            if (!existingTargets.contains(targetId)) {
                createRelation(docId, targetId, targetTypes.getOrDefault(targetId, RelationType.LINK));
            } else {
                RelationType newType = targetTypes.getOrDefault(targetId, RelationType.LINK);
                RelationType currentType = RelationType.fromValue(existingType(existing, targetId));
                if (newType != currentType) {
                    updateRelationType(docId, targetId, newType);
                }
            }
        }

        for (Long targetId : existingTargets) {
            if (!newTargets.contains(targetId)) {
                removeRelation(docId, targetId);
            }
        }
    }

    private void addEdge(List<DocEdgeVO> edges, Set<String> edgeKeys, Long sourceId, Long targetId, String type) {
        String key = sourceId + "->" + targetId + ":" + (type == null ? "" : type);
        if (edgeKeys.add(key)) {
            edges.add(new DocEdgeVO(sourceId, targetId, RelationType.fromValue(type)));
        }
    }

    private String existingType(List<KbDocRelation> existing, Long targetId) {
        for (KbDocRelation rel : existing) {
            if (rel.getId() != null && Objects.equals(rel.getId().getTargetDocId(), targetId)) {
                return rel.getRelationType();
            }
        }
        return RelationType.LINK.getValue();
    }

    private DocLinkVO toLinkVO(KbDocument doc, String relationType) {
        RelationType t = RelationType.fromValue(relationType);
        return new DocLinkVO(doc.getId(),
                doc.getKb() != null ? doc.getKb().getId() : null,
                doc.getKb() != null ? doc.getKb().getTitle() : "",
                doc.getName(),
                doc.getSlug(),
                doc.getType() != null ? doc.getType().getValue() : null,
                t);
    }

    private KbDocument resolveTargetInSameKb(Long kbId, String ref) {
        if (kbId == null || ref == null || ref.isBlank()) {
            return null;
        }
        String q = ref.trim();
        return documentRepository.findFirstByKbIdAndNameAndDeletedAtIsNull(kbId, q)
                .or(() -> documentRepository.findByKbIdAndSlugAndDeletedAtIsNull(kbId, q))
                .orElse(null);
    }
}
