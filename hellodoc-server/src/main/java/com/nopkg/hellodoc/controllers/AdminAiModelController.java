package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.services.AiModelService;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.ai.AiModelCreateReq;
import com.nopkg.hellodoc.web.dto.ai.AiModelResp;
import com.nopkg.hellodoc.web.dto.ai.AiModelTestReq;
import com.nopkg.hellodoc.web.dto.ai.AiModelUpdateReq;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理员 AI 大模型管理控制器
 */
@Tag(name = "Admin AI Models", description = "AI 大模型后台管理相关接口")
@RestController
@RequestMapping("/api/admin/ai/models")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminAiModelController {

    private final AiModelService aiModelService;

    @Operation(summary = "获取所有 AI 大模型列表")
    @GetMapping
    public ApiResponse<List<AiModelResp>> listAllModels() {
        return ApiResponse.success(aiModelService.getAllModels());
    }

    @Operation(summary = "新增 AI 大模型配置")
    @PostMapping
    public ApiResponse<AiModelResp> createModel(@Valid @RequestBody AiModelCreateReq req) {
        return ApiResponse.success(aiModelService.createModel(req));
    }

    @Operation(summary = "更新 AI 大模型配置")
    @PutMapping("/{id}")
    public ApiResponse<AiModelResp> updateModel(@PathVariable String id,
                                               @Valid @RequestBody AiModelUpdateReq req) {
        return ApiResponse.success(aiModelService.updateModel(id, req));
    }

    @Operation(summary = "删除指定的 AI 大模型配置")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteModel(@PathVariable String id) {
        aiModelService.deleteModel(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "一键将指定大模型设为系统默认")
    @PutMapping("/{id}/default")
    public ApiResponse<Void> setDefaultModel(@PathVariable String id) {
        aiModelService.setDefault(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "一键切换模型的激活/停用状态")
    @PutMapping("/{id}/toggle-active")
    public ApiResponse<Void> toggleActiveModel(@PathVariable String id) {
        aiModelService.toggleActive(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "测试大模型连通性")
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testConnection(@RequestBody AiModelTestReq req) {
        return ApiResponse.success(aiModelService.testConnection(req));
    }
}
