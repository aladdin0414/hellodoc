package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.services.FavoriteService;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.ux.FavoriteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
@Tag(name = "收藏管理", description = "文档收藏与取消收藏接口")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final KbService kbService;

    @GetMapping("/favorites")
    @Operation(summary = "获取收藏列表", description = "获取当前用户的收藏文档列表")
    public ApiResponse<List<FavoriteVO>> getFavorites() {
        return ApiResponse.success(favoriteService.getFavorites(currentUserId()));
    }

    @PostMapping("/{docId}/favorite")
    @Operation(summary = "切换收藏状态", description = "添加或取消文档收藏")
    public ApiResponse<Void> toggleFavorite(@PathVariable Long docId) {
        favoriteService.toggleFavorite(currentUserId(), docId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{docId}/favorite")
    @Operation(summary = "移除收藏", description = "将文档从收藏列表中移除")
    public ApiResponse<Void> removeFavorite(@PathVariable Long docId) {
        favoriteService.removeFavorite(currentUserId(), docId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{docId}/favorite")
    @Operation(summary = "检查是否已收藏", description = "检查指定文档是否已被当前用户收藏")
    public ApiResponse<Boolean> isFavorite(@PathVariable Long docId) {
        return ApiResponse.success(favoriteService.isFavorite(currentUserId(), docId));
    }

    private Long currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return kbService.requireUserId(username);
    }
}
