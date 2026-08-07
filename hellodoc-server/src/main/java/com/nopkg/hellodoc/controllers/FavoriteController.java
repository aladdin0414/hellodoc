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
@Tag(name = "Favorite Management", description = "Document favorite and unfavorite APIs")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final KbService kbService;

    @GetMapping("/favorites")
    @Operation(summary = "List favorites", description = "Get favorite documents for current user")
    public ApiResponse<List<FavoriteVO>> getFavorites() {
        return ApiResponse.success(favoriteService.getFavorites(currentUserId()));
    }

    @PostMapping("/{docId}/favorite")
    @Operation(summary = "Toggle favorite status", description = "Add or remove document favorite")
    public ApiResponse<Void> toggleFavorite(@PathVariable Long docId) {
        favoriteService.toggleFavorite(currentUserId(), docId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{docId}/favorite")
    @Operation(summary = "Remove favorite", description = "Remove document from favorite list")
    public ApiResponse<Void> removeFavorite(@PathVariable Long docId) {
        favoriteService.removeFavorite(currentUserId(), docId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{docId}/favorite")
    @Operation(summary = "Check favorite status", description = "Check if specific document is favorited by current user")
    public ApiResponse<Boolean> isFavorite(@PathVariable Long docId) {
        return ApiResponse.success(favoriteService.isFavorite(currentUserId(), docId));
    }

    private Long currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return kbService.requireUserId(username);
    }
}
