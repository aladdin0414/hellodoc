package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.DocComment;
import com.nopkg.hellodoc.entities.SysUserAuth;
import com.nopkg.hellodoc.repositories.UserAuthRepository;
import com.nopkg.hellodoc.services.CommentService;
import com.nopkg.hellodoc.services.ConfigService;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.comment.CommentCreateDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.nopkg.hellodoc.web.dto.comment.DocCommentVO;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Comment Management", description = "Document comment APIs")
public class CommentController {

    private final CommentService commentService;
    private final UserAuthRepository userAuthRepository;
    private final ConfigService configService;
    private final com.nopkg.hellodoc.repositories.DocCommentRepository commentRepository;
    private final com.nopkg.hellodoc.repositories.KbDocumentRepository documentRepository;
    private final com.nopkg.hellodoc.services.PermissionChecker permissionChecker;

    @PostMapping("/docs/{docId}/comments")
    @Operation(summary = "Add comment", description = "Add new comment or anchor comment in specific document")
    public ApiResponse<DocCommentVO> addComment(
            @PathVariable Long docId,
            @RequestBody CommentCreateDTO dto) {
        Long userId = currentUserId();
        permissionChecker.checkDocRole(userId, docId, com.nopkg.hellodoc.enums.DocRole.VIEWER);
        if (dto.getParentId() == null) {
            if (dto.getAnchorType() == null) checkGuestbookEnabled(docId);
            else permissionChecker.requirePrivateDocAccess(userId, docId);
        } else {
            DocComment parent = findComment(dto.getParentId());
            if (!parent.getDocId().equals(docId)) throw new com.nopkg.hellodoc.exceptions.BusinessException(ApiResponse.Code.PARAM_ERROR);
            if (isGuestbookThread(parent)) checkGuestbookEnabled(docId);
            else permissionChecker.requirePrivateDocAccess(userId, docId);
        }
        dto.setDocId(docId);
        return ApiResponse.success(DocCommentVO.from(commentService.addComment(dto, userId)));
    }

    @GetMapping("/docs/{docId}/comments")
    @Operation(summary = "List document comments", description = "Get all comments including replies for specific document")
    public ApiResponse<List<DocCommentVO>> getDocumentComments(@PathVariable Long docId) {
        Long userId = currentUserId();
        permissionChecker.checkDocRole(userId, docId, com.nopkg.hellodoc.enums.DocRole.VIEWER);
        List<DocComment> comments = commentService.getDocumentComments(docId);
        boolean enabled = isGuestbookEnabled(docId);
        boolean privateAccess = permissionChecker.hasPrivateDocAccess(userId, docId);
        return ApiResponse.success(comments.stream()
                .filter(comment -> isGuestbookThread(comment) ? enabled : privateAccess)
                .map(DocCommentVO::from)
                .collect(Collectors.toList()));
    }

    @PutMapping("/comments/{id}")
    @Operation(summary = "Update comment", description = "Update published comment content")
    public ApiResponse<DocCommentVO> updateComment(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        Long userId = currentUserId();
        return ApiResponse.success(DocCommentVO.from(commentService.updateComment(id, payload.get("content"), userId)));
    }

    @DeleteMapping("/comments/{id}")
    @Operation(summary = "Delete comment", description = "Delete specific comment")
    public ApiResponse<Void> deleteComment(@PathVariable Long id) {
        Long userId = currentUserId();
        commentService.deleteComment(id, userId);
        return ApiResponse.success();
    }

    @PostMapping("/comments/{id}/reply")
    @Operation(summary = "Reply to comment", description = "Reply to specific comment")
    public ApiResponse<DocCommentVO> replyComment(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        Long userId = currentUserId();
        DocComment parent = findComment(id);
        permissionChecker.checkDocRole(userId, parent.getDocId(), com.nopkg.hellodoc.enums.DocRole.VIEWER);
        if (isGuestbookThread(parent)) checkGuestbookEnabled(parent.getDocId());
        else permissionChecker.requirePrivateDocAccess(userId, parent.getDocId());
        return ApiResponse.success(DocCommentVO.from(commentService.replyComment(id, payload.get("content"), userId)));
    }

    @PostMapping("/comments/{id}/resolve")
    @Operation(summary = "Resolve comment", description = "Mark comment as resolved")
    public ApiResponse<DocCommentVO> resolveComment(@PathVariable Long id) {
        Long userId = currentUserId();
        return ApiResponse.success(DocCommentVO.from(commentService.resolveComment(id, userId)));
    }

    @PostMapping("/comments/{id}/unresolve")
    @Operation(summary = "Unresolve comment", description = "Revert comment to unresolved status")
    public ApiResponse<DocCommentVO> unresolveComment(@PathVariable Long id) {
        Long userId = currentUserId();
        return ApiResponse.success(DocCommentVO.from(commentService.unresolveComment(id, userId)));
    }

    @GetMapping("/docs/{docId}/comments/unresolved-count")
    @Operation(summary = "Get unresolved count", description = "Count unresolved comments in specific document")
    public ApiResponse<Integer> getUnresolvedCount(@PathVariable Long docId) {
        return ApiResponse.success(commentService.getUnresolvedCount(docId));
    }

    private boolean isGuestbookEnabled(Long docId) {
        Boolean systemEnabled = configService.getConfigValue("app.enable_guestbook", Boolean.class);
        var doc = documentRepository.findByIdAndDeletedAtIsNull(docId)
                .orElseThrow(() -> new com.nopkg.hellodoc.exceptions.ResourceNotFoundException("Document", docId));
        return !Boolean.FALSE.equals(systemEnabled) && !Boolean.FALSE.equals(doc.getKb().getGuestbookEnabled());
    }

    private void checkGuestbookEnabled(Long docId) {
        if (!isGuestbookEnabled(docId)) {
            throw new com.nopkg.hellodoc.exceptions.BusinessException(ApiResponse.Code.NO_PERMISSION, com.nopkg.hellodoc.utils.MessageUtils.get("guestbook.disabled"));
        }
    }

    private DocComment findComment(Long id) {
        return commentRepository.findById(id).orElseThrow(() -> new com.nopkg.hellodoc.exceptions.ResourceNotFoundException("Comment", id));
    }

    private boolean isGuestbookThread(DocComment comment) {
        java.util.Set<Long> visited = new java.util.HashSet<>();
        while (comment.getParentId() != null) {
            if (!visited.add(comment.getId())) throw new com.nopkg.hellodoc.exceptions.BusinessException(ApiResponse.Code.PARAM_ERROR);
            DocComment parent = findComment(comment.getParentId());
            if (!parent.getDocId().equals(comment.getDocId())) throw new com.nopkg.hellodoc.exceptions.BusinessException(ApiResponse.Code.PARAM_ERROR);
            comment = parent;
        }
        return comment.getAnchorType() == null;
    }

    private Long currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUserAuth auth = userAuthRepository.findByIdentifierAndIdentityType(username, "PASSWORD")
                .orElseThrow(() -> new RuntimeException("User not found"));
        return auth.getUser().getId();
    }
}
