package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.security.RequireKbRole;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.services.SearchService;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.search.SearchResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "搜索", description = "全文搜索与索引维护接口")
public class SearchController {

    private final SearchService searchService;
    private final KbService kbService;

    @GetMapping("/api/kb/{kbId}/search")
    @Operation(summary = "知识库内搜索", description = "在指定知识库中进行全文搜索")
    @RequireKbRole(KbRole.VIEWER)
    public ApiResponse<List<SearchResultVO>> searchInKb(@PathVariable Long kbId,
            @RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(searchService.search(kbId, query, limit, true));
    }

    @GetMapping("/api/kb/{kbId}/search-editor")
    @Operation(summary = "编辑模式知识库搜索", description = "在指定知识库中进行全文搜索，包含草稿（需要编辑器权限或以上）")
    @RequireKbRole(KbRole.EDITOR)
    public ApiResponse<List<SearchResultVO>> searchEditorInKb(@PathVariable Long kbId,
            @RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(searchService.search(kbId, query, limit, false));
    }

    @GetMapping("/api/search")
    @Operation(summary = "全局搜索", description = "在所有可访问文档中进行全文搜索")
    public ApiResponse<List<SearchResultVO>> searchAll(@RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = currentUserId();
        return ApiResponse.success(searchService.searchAll(userId, query, limit));
    }

    @PostMapping("/api/search/rebuild")
    @Operation(summary = "重建索引", description = "重建全文搜索索引（管理员）")
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<Void> rebuildAll() {
        searchService.rebuildAllIndex();
        return ApiResponse.success();
    }

    private Long currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return kbService.requireUserId(username);
    }
}
