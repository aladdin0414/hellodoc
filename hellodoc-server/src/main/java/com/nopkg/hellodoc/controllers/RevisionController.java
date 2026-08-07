package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.KbDocumentRevision;
import com.nopkg.hellodoc.security.RequireDocRole;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.services.RevisionService;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.revision.CreateRevisionDTO;
import com.nopkg.hellodoc.web.dto.revision.RevisionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/docs/{docId}/revisions")
@RequiredArgsConstructor
@Tag(name = "Document Revisions", description = "Document version control and revision APIs")
public class RevisionController {

    private final RevisionService revisionService;
    private final KbService kbService;

    @GetMapping
    @Operation(summary = "Get version history")
    @RequireDocRole
    public ApiResponse<Page<RevisionVO>> getHistory(@PathVariable Long docId,
            @PageableDefault(sort = "version", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        Page<KbDocumentRevision> page = revisionService.getRevisionHistory(docId, pageable);
        java.util.Set<Long> authorIds = page.getContent().stream()
                .map(KbDocumentRevision::getAuthorUserId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, com.nopkg.hellodoc.entities.SysUser> authors = kbService.loadUsersByIds(authorIds);

        Page<RevisionVO> voPage = page.map(rev -> toVO(rev, authors.get(rev.getAuthorUserId())));
        return ApiResponse.success(voPage);
    }

    @PostMapping
    @Operation(summary = "Create manual revision")
    @RequireDocRole(com.nopkg.hellodoc.enums.DocRole.EDITOR)
    public ApiResponse<RevisionVO> createRevision(@PathVariable Long docId, @RequestBody CreateRevisionDTO dto) {
        Long userId = currentUserId();
        KbDocumentRevision revision = revisionService.createRevision(docId, dto.getContent(),
                com.nopkg.hellodoc.enums.RevisionType.MANUAL,
                dto.getMessage(), userId);
        com.nopkg.hellodoc.entities.SysUser author = kbService.loadUsersByIds(java.util.Set.of(userId)).get(userId);
        return ApiResponse.success(toVO(revision, author));
    }

    @PostMapping("/milestone")
    @Operation(summary = "Create milestone revision")
    @RequireDocRole(com.nopkg.hellodoc.enums.DocRole.EDITOR)
    public ApiResponse<Void> createMilestone(@PathVariable Long docId, @RequestBody CreateRevisionDTO dto) {
        Long userId = currentUserId();
        revisionService.createMilestone(docId, dto.getMessage(), userId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{version}")
    @Operation(summary = "Get revision details")
    @RequireDocRole
    public ApiResponse<RevisionVO> getRevision(@PathVariable Long docId, @PathVariable Integer version) {
        KbDocumentRevision revision = revisionService.getRevision(docId, version);
        com.nopkg.hellodoc.entities.SysUser author = null;
        if (revision.getAuthorUserId() != null) {
            author = kbService.loadUsersByIds(java.util.Set.of(revision.getAuthorUserId()))
                    .get(revision.getAuthorUserId());
        }
        return ApiResponse.success(toVO(revision, author));
    }

    @GetMapping("/{version}/content")
    @Operation(summary = "Get revision content")
    @RequireDocRole
    public ApiResponse<String> getRevisionContent(@PathVariable Long docId, @PathVariable Integer version) {
        String content = revisionService.getRevisionContent(docId, version);
        return ApiResponse.success(content);
    }

    @PostMapping("/{version}/restore")
    @Operation(summary = "Restore revision")
    @RequireDocRole(com.nopkg.hellodoc.enums.DocRole.EDITOR)
    public ApiResponse<Void> restoreRevision(@PathVariable Long docId, @PathVariable Integer version) {
        Long userId = currentUserId();
        revisionService.restoreRevision(docId, version, userId);
        return ApiResponse.success(null);
    }

    @GetMapping("/compare")
    @Operation(summary = "Compare revision diff")
    @RequireDocRole
    public ApiResponse<String> compareRevisions(@PathVariable Long docId,
            @RequestParam Integer v1,
            @RequestParam Integer v2) {
        String delta = revisionService.compareRevisions(docId, v1, v2);
        return ApiResponse.success(delta);
    }

    private RevisionVO toVO(KbDocumentRevision revision, com.nopkg.hellodoc.entities.SysUser author) {
        RevisionVO vo = new RevisionVO();
        vo.setId(revision.getId());
        vo.setVersion(revision.getVersion());
        vo.setAuthorId(revision.getAuthorUserId());
        vo.setAuthorName(author != null ? author.getNickname() : null);
        vo.setCreatedAt(revision.getCreatedAt());
        vo.setMessage(revision.getMessage());
        vo.setType(revision.getRevisionType());
        vo.setWordCount(revision.getWordCount());
        return vo;
    }

    private Long currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return kbService.requireUserId(username);
    }
}
