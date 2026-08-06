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
@Tag(name = "评论管理", description = "文档评论相关接口")
public class CommentController {

    private final CommentService commentService;
    private final UserAuthRepository userAuthRepository;
    private final ConfigService configService;

    @PostMapping("/docs/{docId}/comments")
    @Operation(summary = "发表评论", description = "在指定文档中发表新评论或针对锚点发表评论")
    public ApiResponse<DocCommentVO> addComment(
            @PathVariable Long docId,
            @RequestBody CommentCreateDTO dto) {
        checkGuestbookEnabled();
        Long userId = currentUserId();
        dto.setDocId(docId);
        return ApiResponse.success(DocCommentVO.from(commentService.addComment(dto, userId)));
    }

    @GetMapping("/docs/{docId}/comments")
    @Operation(summary = "获取文档评论列表", description = "获取指定文档的所有评论，包括回复")
    public ApiResponse<List<DocCommentVO>> getDocumentComments(@PathVariable Long docId) {
        List<DocComment> comments = commentService.getDocumentComments(docId);
        return ApiResponse.success(comments.stream()
                .map(DocCommentVO::from)
                .collect(Collectors.toList()));
    }

    @PutMapping("/comments/{id}")
    @Operation(summary = "更新评论", description = "修改已发表的评论内容")
    public ApiResponse<DocCommentVO> updateComment(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        Long userId = currentUserId();
        return ApiResponse.success(DocCommentVO.from(commentService.updateComment(id, payload.get("content"), userId)));
    }

    @DeleteMapping("/comments/{id}")
    @Operation(summary = "删除评论", description = "删除指定的评论")
    public ApiResponse<Void> deleteComment(@PathVariable Long id) {
        Long userId = currentUserId();
        commentService.deleteComment(id, userId);
        return ApiResponse.success();
    }

    @PostMapping("/comments/{id}/reply")
    @Operation(summary = "回复评论", description = "针对指定的评论进行回复")
    public ApiResponse<DocCommentVO> replyComment(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        checkGuestbookEnabled();
        Long userId = currentUserId();
        return ApiResponse.success(DocCommentVO.from(commentService.replyComment(id, payload.get("content"), userId)));
    }

    @PostMapping("/comments/{id}/resolve")
    @Operation(summary = "解决评论", description = "将评论标记为已解决状态")
    public ApiResponse<DocCommentVO> resolveComment(@PathVariable Long id) {
        Long userId = currentUserId();
        return ApiResponse.success(DocCommentVO.from(commentService.resolveComment(id, userId)));
    }

    @PostMapping("/comments/{id}/unresolve")
    @Operation(summary = "取消解决评论", description = "将评论恢复为未解决状态")
    public ApiResponse<DocCommentVO> unresolveComment(@PathVariable Long id) {
        Long userId = currentUserId();
        return ApiResponse.success(DocCommentVO.from(commentService.unresolveComment(id, userId)));
    }

    @GetMapping("/docs/{docId}/comments/unresolved-count")
    @Operation(summary = "获取未解决评论数", description = "统计指定文档中未解决的评论数量")
    public ApiResponse<Integer> getUnresolvedCount(@PathVariable Long docId) {
        return ApiResponse.success(commentService.getUnresolvedCount(docId));
    }

    private void checkGuestbookEnabled() {
        Boolean enabled = configService.getConfigValue("app.enable_guestbook", Boolean.class);
        if (Boolean.FALSE.equals(enabled)) {
            throw new com.nopkg.hellodoc.exceptions.BusinessException(ApiResponse.Code.SYSTEM_ERROR, "留言功能已关闭");
        }
    }

    private Long currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUserAuth auth = userAuthRepository.findByIdentifierAndIdentityType(username, "PASSWORD")
                .orElseThrow(() -> new RuntimeException("User not found"));
        return auth.getUser().getId();
    }
}
