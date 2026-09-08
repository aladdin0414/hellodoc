package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.services.AiModelService;
import com.nopkg.hellodoc.services.AiService;
import com.nopkg.hellodoc.web.dto.ai.AiCompletionReq;
import com.nopkg.hellodoc.web.dto.ai.AiCompletionResp;
import com.nopkg.hellodoc.web.dto.ai.AiModelResp;
import com.nopkg.hellodoc.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * AI 集成前台交互控制器
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI Integration", description = "AI 辅助编辑与多模型对话接口")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final AiModelService aiModelService;

    @GetMapping("/models/active")
    @Operation(summary = "获取当前所有已激活的 AI 大模型列表")
    public ApiResponse<List<AiModelResp>> getActiveModels() {
        return ApiResponse.success(aiModelService.getActiveModels());
    }

    @PostMapping("/completion")
    @Operation(summary = "同步生成 AI 文本补全")
    public ApiResponse<AiCompletionResp> completion(@RequestBody AiCompletionReq req) {
        if (req.getContext() == null || req.getPrompt() == null) {
            throw new com.nopkg.hellodoc.exceptions.BusinessException(
                    ApiResponse.Code.PARAM_ERROR,
                    com.nopkg.hellodoc.utils.MessageUtils.get("common.param_cannot_be_empty")
            );
        }
        String result = aiService.getCompletion(req.getModelId(), req.getContext(), req.getPrompt(), req.getLang());
        String model = aiService.getResolvedModel(req.getModelId());
        return ApiResponse.success(new AiCompletionResp(result, model));
    }

    @PostMapping(value = "/completion/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE 流式生成 AI 补全或对话")
    public SseEmitter completionStream(@RequestBody AiCompletionReq req) {
        if (req.getContext() == null || req.getPrompt() == null) {
            throw new com.nopkg.hellodoc.exceptions.BusinessException(
                    ApiResponse.Code.PARAM_ERROR,
                    com.nopkg.hellodoc.utils.MessageUtils.get("common.param_cannot_be_empty")
            );
        }
        SseEmitter emitter = new SseEmitter(0L);
        String model = aiService.getResolvedModel(req.getModelId());
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().name("model").data(model));
                aiService.streamCompletion(req.getModelId(), req.getContext(), req.getPrompt(), req.getLang(), chunk -> {
                    try {
                        emitter.send(SseEmitter.event().name("chunk").data(chunk));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                emitter.send(SseEmitter.event().name("done").data("ok"));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {
                }
                emitter.complete();
            }
        });
        return emitter;
    }
}
