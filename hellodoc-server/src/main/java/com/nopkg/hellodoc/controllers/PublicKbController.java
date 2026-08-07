package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.enums.DocStatus;
import com.nopkg.hellodoc.enums.Visibility;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.repositories.KbKnowledgeBaseRepository;
import com.nopkg.hellodoc.services.DocumentService;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.services.SearchService;
import com.nopkg.hellodoc.web.dto.search.SearchResultVO;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.utils.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/public/kb")
@RequiredArgsConstructor
@Tag(name = "Public Knowledge Base", description = "Public knowledge base and document browsing APIs")
public class PublicKbController {

        private final KbService kbService;
        private final DocumentService documentService;
        private final SearchService searchService;
        private final KbKnowledgeBaseRepository kbRepository;
        private final KbDocumentRepository documentRepository;

        public record KbSummary(Long id, String title, String description, String icon, String color,
                        Long ownerId, String ownerName, String ownerAvatar, String lastModified,
                        Visibility visibility, OffsetDateTime createdAt) {
        }

        public record KbDocItem(Long id, String name, String type, Long parentId, Integer orderNum, String status,
                        String content, String paperBgColor, String paperBgImage, Map<String, Object> extraMeta, Boolean isOpen, Boolean isCover,
                        Integer currentVersion, Long viewCount,
                        String updatedAt, String createdAt) {
        }

        @GetMapping("/{kbId}")
        @Operation(summary = "Get public KB details")
        public ApiResponse<KbSummary> getPublicKb(@PathVariable Long kbId) {
                KbKnowledgeBase kb = kbRepository.findByIdAndDeletedAtIsNull(kbId)
                                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND,
                                                MessageUtils.get("legacy.kb.not_found", "KB not found")));

                if (kb.getVisibility() != Visibility.PUBLIC) {
                        throw new BusinessException(ApiResponse.Code.NO_PERMISSION, MessageUtils.get("legacy.kb.not_public"));
                }

                SysUser owner = kbService.loadUsersByIds(Set.of(kb.getOwnerId())).get(kb.getOwnerId());
                String ownerName = owner != null ? owner.getNickname() : "";
                String ownerAvatar = owner != null ? owner.getAvatar() : "";
                String lastModified = Optional.ofNullable(kb.getUpdatedAt())
                                .map(OffsetDateTime::toString).orElse("");

                KbSummary summary = new KbSummary(kb.getId(), kb.getTitle(), kb.getDescription(), kb.getIcon(),
                                kb.getColor(), kb.getOwnerId(), ownerName, ownerAvatar, lastModified,
                                kb.getVisibility(), kb.getCreatedAt());
                return ApiResponse.success(summary);
        }

        @GetMapping("/{kbId}/documents")
        @Operation(summary = "List public documents")
        public ApiResponse<List<KbDocItem>> listPublicDocuments(@PathVariable Long kbId) {
                KbKnowledgeBase kb = kbRepository.findByIdAndDeletedAtIsNull(kbId)
                                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND,
                                                MessageUtils.get("legacy.kb.not_found", "KB not found")));

                if (kb.getVisibility() != Visibility.PUBLIC) {
                        throw new BusinessException(ApiResponse.Code.NO_PERMISSION, MessageUtils.get("legacy.kb.not_public"));
                }

                List<KbDocument> docs = documentRepository.findAllPublishedAndAncestors(kbId);
                List<KbDocItem> items = docs.stream()
                                .map(doc -> new KbDocItem(doc.getId(), doc.getName(), doc.getType().getValue(),
                                                doc.getParent() != null ? doc.getParent().getId() : null,
                                                doc.getOrderNum(), doc.getStatus().getValue(),
                                                null,
                                                doc.getPaperBgColor(), doc.getPaperBgImage(),
                                                doc.getExtraMeta() != null ? doc.getExtraMeta() : Map.of(),
                                                doc.getIsOpen(), doc.getIsCover(), doc.getCurrentVersion(),
                                                doc.getViewCount() == null ? 0 : doc.getViewCount(),
                                                doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : null,
                                                doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null))
                                .toList();
                return ApiResponse.success(items);
        }

        @GetMapping("/{kbId}/documents/{docId}")
        @Operation(summary = "Get public document details")
        public ApiResponse<KbDocItem> getPublicDocument(@PathVariable Long kbId, @PathVariable Long docId) {
                KbKnowledgeBase kb = kbRepository.findByIdAndDeletedAtIsNull(kbId)
                                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND,
                                                MessageUtils.get("legacy.kb.not_found", "KB not found")));

                if (kb.getVisibility() != Visibility.PUBLIC) {
                        throw new BusinessException(ApiResponse.Code.NO_PERMISSION, MessageUtils.get("legacy.kb.not_public"));
                }

                KbDocument doc = documentRepository.findByIdAndDeletedAtIsNull(docId)
                                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, MessageUtils.get("legacy.document.not_found")));

                if (!doc.getKb().getId().equals(kbId) || doc.getStatus() != DocStatus.PUBLISHED) {
                        throw new BusinessException(ApiResponse.Code.NO_PERMISSION, MessageUtils.get("legacy.document.no_permission"));
                }

                // Increment view count
                documentService.incrementViewCount(docId);

                KbDocItem item = new KbDocItem(doc.getId(), doc.getName(), doc.getType().getValue(),
                                doc.getParent() != null ? doc.getParent().getId() : null,
                                doc.getOrderNum(), doc.getStatus().getValue(),
                                documentService.getContent(doc.getId()),
                                doc.getPaperBgColor(), doc.getPaperBgImage(),
                                doc.getExtraMeta() != null ? doc.getExtraMeta() : Map.of(),
                                doc.getIsOpen(), doc.getIsCover(), doc.getCurrentVersion(),
                                (doc.getViewCount() == null ? 0 : doc.getViewCount()) + 1,
                                doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : null,
                                doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null);
                return ApiResponse.success(item);
        }

        @GetMapping("/{kbId}/search")
        @Operation(summary = "Search public KB", description = "Full text search in public knowledge base")
        public ApiResponse<List<SearchResultVO>> searchPublicKb(@PathVariable Long kbId,
                        @RequestParam(name = "q") String query,
                        @RequestParam(defaultValue = "20") int limit) {
                KbKnowledgeBase kb = kbRepository.findByIdAndDeletedAtIsNull(kbId)
                                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND,
                                                MessageUtils.get("legacy.kb.not_found", "KB not found")));

                if (kb.getVisibility() != Visibility.PUBLIC) {
                        throw new BusinessException(ApiResponse.Code.NO_PERMISSION, MessageUtils.get("legacy.kb.not_public"));
                }

                return ApiResponse.success(searchService.search(kbId, query, limit, true));
        }
}
