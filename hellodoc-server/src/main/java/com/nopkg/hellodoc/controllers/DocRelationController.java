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
@Tag(name = "文档关系", description = "文档链接、反向链接与关系图谱接口")
public class DocRelationController {

    private final DocRelationService relationService;
    private final KbService kbService;

    record CreateLinkRequest(Long targetDocId, RelationType relationType) {
    }

    @GetMapping("/links")
    @Operation(summary = "引用的文档", description = "获取我引用的文档列表")
    @RequireDocRole(DocRole.VIEWER)
    public ApiResponse<List<DocRelationService.DocLinkVO>> links(@PathVariable Long docId) {
        Long userId = currentUserId();
        return ApiResponse.success(relationService.getOutgoingLinks(userId, docId));
    }

    @GetMapping("/backlinks")
    @Operation(summary = "反向链接", description = "获取引用我的文档列表")
    @RequireDocRole(DocRole.VIEWER)
    public ApiResponse<List<DocRelationService.DocLinkVO>> backlinks(@PathVariable Long docId) {
        Long userId = currentUserId();
        return ApiResponse.success(relationService.getBacklinks(userId, docId));
    }

    @GetMapping("/graph")
    @Operation(summary = "关系图谱", description = "获取文档关系图谱")
    @RequireDocRole(DocRole.VIEWER)
    public ApiResponse<DocRelationService.DocGraphVO> graph(@PathVariable Long docId,
            @RequestParam(defaultValue = "2") int depth) {
        Long userId = currentUserId();
        return ApiResponse.success(relationService.getRelationGraph(userId, docId, depth));
    }

    @PostMapping("/links")
    @Operation(summary = "手动添加链接", description = "手动创建文档引用关系")
    @RequireDocRole(DocRole.EDITOR)
    public ApiResponse<Void> createLink(@PathVariable Long docId, @RequestBody CreateLinkRequest request) {
        relationService.createRelation(docId, request.targetDocId(),
                request.relationType() != null ? request.relationType() : RelationType.LINK);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/links/{targetDocId}")
    @Operation(summary = "删除链接", description = "删除文档引用关系")
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
