package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbDocumentRevision;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.security.RequireDocRole;
import com.nopkg.hellodoc.security.RequireKbRole;
import com.nopkg.hellodoc.services.DocumentService;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.services.RecentService;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.kb.DocCreateDTO;
import com.nopkg.hellodoc.web.dto.kb.DocUpdateDTO;
import com.nopkg.hellodoc.web.dto.ux.RecentDocVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "文档管理", description = "文档树与内容管理接口")
public class DocumentController {

        private final DocumentService documentService;
        private final KbService kbService;
        private final RecentService recentService;

        record KbDocItem(Long id, String name, String type, Long parentId, Integer orderNum, String status,
                        String content, String paperBgColor, String paperBgImage, Map<String, Object> extraMeta, Boolean isOpen, Boolean isCover, Boolean isEncrypted, Integer version,
                        Long viewCount,
                        OffsetDateTime createdAt, OffsetDateTime updatedAt, Long authorId) {
        }

        record CreateDocRequest(String name, String type, Long parentId, Integer orderNum, String content,
                        String paperBgColor, String paperBgImage, Map<String, Object> extraMeta) {
        }

        record UpdateDocRequest(String name, String type, Long parentId, Integer orderNum, String status,
                        String content, Boolean isOpen, Boolean isCover, String password, String paperBgColor,
                        String paperBgImage, Map<String, Object> extraMeta) {
        }

        record UnlockRequest(String password) {
        }

        record KbRevision(Long id, Integer version, String content, String authorName, String createdAt,
                        String message) {
        }

        record CreateRevisionRequest(String content, String message) {
        }

        @GetMapping("/api/kb/{kbId}/documents/{docId}")
        @Operation(summary = "获取文档详情", description = "获取文档详细信息并记录访问历史")
        @RequireDocRole(DocRole.VIEWER)
        public ApiResponse<KbDocItem> getDocument(@PathVariable Long kbId, @PathVariable Long docId) {
                KbDocument doc = documentService.getById(docId);
                if (!Objects.equals(doc.getKb().getId(), kbId)) {
                        throw new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, "Document not found in kb");
                }
                recentService.recordVisit(currentUserId(), docId);
                documentService.incrementViewCount(docId);
                long viewCount = (doc.getViewCount() == null ? 0 : doc.getViewCount()) + 1;
                return ApiResponse.success(toDocItem(doc, documentService.getContent(doc.getId()), viewCount));
        }

        @GetMapping("/api/docs/recent")
        @Operation(summary = "最近访问列表", description = "获取当前用户最近访问的文档列表")
        public ApiResponse<List<RecentDocVO>> getRecentDocuments(@RequestParam(defaultValue = "20") int limit) {
                return ApiResponse.success(recentService.getRecentDocuments(currentUserId(), limit));
        }

        @DeleteMapping("/api/docs/recent")
        @Operation(summary = "清空访问历史", description = "清空当前用户最近访问记录")
        public ApiResponse<Void> clearRecentHistory() {
                recentService.clearRecentHistory(currentUserId());
                return ApiResponse.success(null);
        }

        @GetMapping("/api/kb/{kbId}/documents")
        @Operation(summary = "获取文档列表", description = "获取知识库文档树列表")
        @RequireKbRole(KbRole.VIEWER)
        public ApiResponse<List<KbDocItem>> listDocuments(@PathVariable Long kbId) {
                List<KbDocument> docs = documentService.getTree(kbId);
                List<KbDocItem> items = docs.stream()
                                .map(doc -> toDocItem(doc, null, null))
                                .toList();
                return ApiResponse.success(items);
        }

        @PostMapping("/api/kb/{kbId}/documents")
        @Operation(summary = "创建文档", description = "创建知识库文档或文件夹")
        @RequireKbRole(KbRole.EDITOR)
        public ApiResponse<KbDocItem> createDocument(@PathVariable Long kbId, @RequestBody CreateDocRequest request) {
                DocCreateDTO dto = new DocCreateDTO();
                dto.setName(request.name());
                dto.setType(com.nopkg.hellodoc.enums.DocType.fromValue(request.type()));
                dto.setParentId(request.parentId());
                dto.setOrderNum(request.orderNum());
                dto.setContent(request.content());
                dto.setPaperBgColor(request.paperBgColor());
                dto.setPaperBgImage(request.paperBgImage());
                dto.setExtraMeta(request.extraMeta());

                KbDocument doc = documentService.create(currentUserId(), kbId, dto);
                return ApiResponse.success(toDocItem(doc, request.content(), null));
        }

        @PutMapping("/api/kb/{kbId}/documents/{docId}")
        @Operation(summary = "更新文档", description = "更新文档信息或内容")
        @RequireDocRole(DocRole.EDITOR)
        public ApiResponse<KbDocItem> updateDocument(@PathVariable Long kbId, @PathVariable Long docId,
                        @RequestBody UpdateDocRequest request) {
                DocUpdateDTO dto = new DocUpdateDTO();
                dto.setName(request.name());
                if (request.type() != null)
                        dto.setType(com.nopkg.hellodoc.enums.DocType.fromValue(request.type()));
                dto.setParentId(request.parentId());
                dto.setOrderNum(request.orderNum());
                if (request.status() != null)
                        dto.setStatus(com.nopkg.hellodoc.enums.DocStatus.fromValue(request.status()));
                dto.setContent(request.content());
                dto.setIsOpen(request.isOpen());
                dto.setIsCover(request.isCover());
                dto.setPassword(request.password());
                dto.setPaperBgColor(request.paperBgColor());
                dto.setPaperBgImage(request.paperBgImage());
                dto.setExtraMeta(request.extraMeta());

                KbDocument doc = documentService.update(currentUserId(), kbId, docId, dto);
                return ApiResponse.success(toDocItem(doc, request.content(), null));
        }

        @DeleteMapping("/api/kb/{kbId}/documents/{docId}")
        @Operation(summary = "删除文档", description = "删除文档或文件夹（移入回收站）")
        @RequireDocRole(DocRole.EDITOR)
        public ApiResponse<Void> deleteDocument(@PathVariable Long kbId, @PathVariable Long docId) {
                documentService.delete(currentUserId(), kbId, docId);
                return ApiResponse.success(null);
        }

        @GetMapping("/api/kb/{kbId}/trash")
        @Operation(summary = "获取回收站列表", description = "获取已被移入回收站的项目列表")
        @RequireKbRole(KbRole.VIEWER)
        public ApiResponse<List<KbDocItem>> getTrashList(@PathVariable Long kbId) {
                List<KbDocument> docs = documentService.getTrashList(currentUserId(), kbId);
                List<KbDocItem> items = docs.stream()
                                .map(doc -> toDocItem(doc, null, null))
                                .toList();
                return ApiResponse.success(items);
        }

        @PostMapping("/api/kb/{kbId}/documents/{docId}/restore")
        @Operation(summary = "还原文档/文件夹", description = "从回收站级联还原文档或文件夹")
        @RequireKbRole(KbRole.EDITOR)
        public ApiResponse<Void> restoreDocument(@PathVariable Long kbId, @PathVariable Long docId) {
                documentService.restore(currentUserId(), kbId, docId);
                return ApiResponse.success(null);
        }

        @DeleteMapping("/api/kb/{kbId}/documents/{docId}/permanent")
        @Operation(summary = "彻底删除文档", description = "从数据库物理彻底删除文档或文件夹")
        @RequireKbRole(KbRole.ADMIN)
        public ApiResponse<Void> permanentlyDeleteDocument(@PathVariable Long kbId, @PathVariable Long docId) {
                documentService.permanentlyDelete(currentUserId(), kbId, docId);
                return ApiResponse.success(null);
        }

        @DeleteMapping("/api/kb/{kbId}/trash")
        @Operation(summary = "清空回收站", description = "清空当前知识库回收站中的所有删除项目")
        @RequireKbRole(KbRole.ADMIN)
        public ApiResponse<Void> clearTrash(@PathVariable Long kbId) {
                documentService.clearTrash(currentUserId(), kbId);
                return ApiResponse.success(null);
        }

        @GetMapping("/api/kb/{kbId}/documents/{docId}/revisions")
        @Operation(summary = "获取修订历史", description = "获取文档修订历史")
        @RequireDocRole(DocRole.VIEWER)
        public ApiResponse<List<KbRevision>> listRevisions(@PathVariable Long kbId, @PathVariable Long docId) {
                List<KbDocumentRevision> revisions = documentService.listRevisions(currentUserId(), kbId, docId);
                Set<Long> authorIds = revisions.stream().map(KbDocumentRevision::getAuthorUserId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet());
                Map<Long, SysUser> authors = kbService.loadUsersByIds(authorIds);
                List<KbRevision> result = revisions.stream()
                                .map(rev -> new KbRevision(rev.getId(), rev.getVersion(), rev.getContent(),
                                                Optional.ofNullable(authors.get(rev.getAuthorUserId()))
                                                                .map(SysUser::getNickname).orElse(""),
                                                Optional.ofNullable(rev.getCreatedAt()).map(OffsetDateTime::toString)
                                                                .orElse(""),
                                                rev.getMessage()))
                                .toList();
                return ApiResponse.success(result);
        }

        @PostMapping("/api/kb/{kbId}/documents/{docId}/revisions")
        @Operation(summary = "创建修订", description = "创建文档修订并更新正文")
        @RequireDocRole(DocRole.EDITOR)
        public ApiResponse<KbRevision> createRevision(@PathVariable Long kbId, @PathVariable Long docId,
                        @RequestBody CreateRevisionRequest request) {
                KbDocumentRevision revision = documentService.createRevision(currentUserId(), kbId, docId,
                                request.content(), request.message());
                SysUser author = kbService.loadUsersByIds(Set.of(currentUserId())).get(currentUserId());
                KbRevision response = new KbRevision(revision.getId(), revision.getVersion(), revision.getContent(),
                                author != null ? author.getNickname() : "",
                                Optional.ofNullable(revision.getCreatedAt()).map(OffsetDateTime::toString).orElse(""),
                                revision.getMessage());
                return ApiResponse.success(response);
        }

        @PostMapping("/api/kb/{kbId}/documents/{docId}/duplicate")
        @Operation(summary = "创建副本", description = "创建文档的副本并放置在原文档下方")
        @RequireDocRole(DocRole.EDITOR)
        public ApiResponse<KbDocItem> duplicateDocument(@PathVariable Long kbId, @PathVariable Long docId) {
                KbDocument doc = documentService.duplicate(currentUserId(), kbId, docId);
                return ApiResponse.success(toDocItem(doc, documentService.getContent(doc.getId()), null));
        }

        @PostMapping("/api/kb/{kbId}/documents/{docId}/copy-to/{targetKbId}")
        @Operation(summary = "拷贝到另一知识库", description = "将文档或文件夹及其子项移动到目标知识库的根目录")
        @RequireDocRole(DocRole.VIEWER)
        public ApiResponse<KbDocItem> copyToKb(@PathVariable Long kbId, @PathVariable Long docId,
                        @PathVariable Long targetKbId) {
                KbDocument doc = documentService.copyToKb(currentUserId(), docId, targetKbId);
                return ApiResponse.success(toDocItem(doc, documentService.getContent(doc.getId()), null));
        }

        @PostMapping("/api/kb/{kbId}/documents/{docId}/unlock")
        @Operation(summary = "验证密码", description = "验证加密文档的密码")
        @RequireDocRole(DocRole.VIEWER)
        public ApiResponse<KbDocItem> unlockDocument(@PathVariable Long kbId, @PathVariable Long docId,
                        @RequestBody UnlockRequest request) {
                KbDocument doc = documentService.unlockDocument(currentUserId(), docId, request.password());
                return ApiResponse.success(toDocItem(doc, documentService.getUnprotectedContent(doc.getId()), null));
        }

        @GetMapping("/api/kb/{kbId}/documents/{docId}/export")
        @Operation(summary = "导出文档/文件夹", description = "导出文档或文件夹为ZIP (Markdown)")
        @RequireDocRole(DocRole.EDITOR)
        public void exportDocument(@PathVariable Long kbId, @PathVariable Long docId,
                        jakarta.servlet.http.HttpServletResponse response) {
                KbDocItem docItem = getDocument(kbId, docId).getData();
                String filename = docItem.name() + ".zip";

                response.setContentType("application/zip");
                // Encode filename for browser compatibility
                String encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8)
                                .replaceAll("\\+", "%20");
                response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename
                                + "\"; filename*=UTF-8''" + encodedFilename);

                try {
                        documentService.exportToZip(currentUserId(), kbId, docId, response.getOutputStream());
                } catch (java.io.IOException e) {
                        throw new com.nopkg.hellodoc.exceptions.BusinessException(ApiResponse.Code.SYSTEM_ERROR,
                                        "IO Error");
                }
        }

        private KbDocItem toDocItem(KbDocument doc, String content, Long viewCount) {
                Map<String, Object> meta = doc.getExtraMeta() != null ? doc.getExtraMeta() : Map.of();
                return new KbDocItem(doc.getId(), doc.getName(), doc.getType().getValue(),
                                doc.getParent() != null ? doc.getParent().getId() : null,
                                doc.getOrderNum(), doc.getStatus().getValue(), content,
                                doc.getPaperBgColor() == null ? "" : doc.getPaperBgColor(),
                                doc.getPaperBgImage() == null ? "" : doc.getPaperBgImage(),
                                meta,
                                doc.getIsOpen(), doc.getIsCover(), doc.getIsEncrypted(),
                                doc.getCurrentVersion() == null ? 0 : doc.getCurrentVersion(),
                                viewCount != null ? viewCount : (doc.getViewCount() == null ? 0 : doc.getViewCount()),
                                doc.getCreatedAt(), doc.getUpdatedAt(), doc.getAuthorId());
        }

        private Long currentUserId() {
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                return kbService.requireUserId(username);
        }
}
