package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.RelationType;
import com.nopkg.hellodoc.security.RequireDocRole;
import com.nopkg.hellodoc.services.DocRelationService;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/docs/{docId}")
@RequiredArgsConstructor
@Tag(name = "Document Relations", description = "Document links, backlinks and relationship graph APIs")
public class DocRelationController {

    private final DocRelationService relationService;
    private final KbService kbService;

    record CreateLinkRequest(Long targetDocId, RelationType relationType) {
    }

    @GetMapping("/links")
    @Operation(summary = "Referenced documents", description = "List documents referenced by this document")
    @RequireDocRole(DocRole.VIEWER)
    public ApiResponse<List<DocRelationService.DocLinkVO>> links(@PathVariable Long docId) {
        Long userId = currentUserId();
        return ApiResponse.success(relationService.getOutgoingLinks(userId, docId));
    }

    @GetMapping("/backlinks")
    @Operation(summary = "Backlinks", description = "List documents referencing this document")
    @RequireDocRole(DocRole.VIEWER)
    public ApiResponse<List<DocRelationService.DocLinkVO>> backlinks(@PathVariable Long docId) {
        Long userId = currentUserId();
        return ApiResponse.success(relationService.getBacklinks(userId, docId));
    }

    @GetMapping("/graph")
    @Operation(summary = "Relationship graph", description = "Get document relationship graph")
    @RequireDocRole(DocRole.VIEWER)
    public ApiResponse<DocRelationService.DocGraphVO> graph(@PathVariable Long docId,
            @RequestParam(defaultValue = "2") int depth) {
        Long userId = currentUserId();
        return ApiResponse.success(relationService.getRelationGraph(userId, docId, depth));
    }

    @PostMapping("/links")
    @Operation(summary = "Add link manually", description = "Manually create document reference relation")
    @RequireDocRole(DocRole.EDITOR)
    public ApiResponse<Void> createLink(@PathVariable Long docId, @RequestBody CreateLinkRequest request) {
        relationService.createRelation(docId, request.targetDocId(),
                request.relationType() != null ? request.relationType() : RelationType.LINK);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/links/{targetDocId}")
    @Operation(summary = "Delete link", description = "Delete document reference relation")
    @RequireDocRole(DocRole.EDITOR)
    public ApiResponse<Void> deleteLink(@PathVariable Long docId, @PathVariable Long targetDocId) {
        relationService.removeRelation(docId, targetDocId);
        return ApiResponse.success(null);
    }

    private Long currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return kbService.requireUserId(username);
    }
}
