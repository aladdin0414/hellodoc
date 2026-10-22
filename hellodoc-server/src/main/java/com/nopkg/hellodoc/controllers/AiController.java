package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.services.AiModelService;
import com.nopkg.hellodoc.services.AiService;
import com.nopkg.hellodoc.services.AiStreamControl;
import com.nopkg.hellodoc.services.AiStreamRuntime;
import jakarta.validation.Valid;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

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
    private final AiStreamRuntime streams;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GetMapping("/models/active")
    @Operation(summary = "获取当前所有已激活的 AI 大模型列表")
    public ApiResponse<List<AiModelResp>> getActiveModels() {
        return ApiResponse.success(aiModelService.getActiveModels());
    }

    @PostMapping("/completion")
    @Operation(summary = "同步生成 AI 文本补全")
    public ApiResponse<AiCompletionResp> completion(@Valid @RequestBody AiCompletionReq req) {
        if (req.getContext() == null || req.getPrompt() == null) {
            throw new com.nopkg.hellodoc.exceptions.BusinessException(
                    ApiResponse.Code.PARAM_ERROR,
                    com.nopkg.hellodoc.utils.MessageUtils.get("common.param_cannot_be_empty")
            );
        }
        String result = aiService.getCompletion(req.getModelId(), req.getContext(), req.getPrompt(), req.getLang(), req.getHistory());
        String model = aiService.getResolvedModel(req.getModelId());
        return ApiResponse.success(new AiCompletionResp(result, model));
    }

    @PostMapping(value = "/completion/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE 流式生成 AI 补全或对话")
    public SseEmitter completionStream(@Valid @RequestBody AiCompletionReq req) {
        if (req.getContext() == null || req.getPrompt() == null) {
            throw new com.nopkg.hellodoc.exceptions.BusinessException(
                    ApiResponse.Code.PARAM_ERROR,
                    com.nopkg.hellodoc.utils.MessageUtils.get("common.param_cannot_be_empty")
            );
        }
        int timeout = aiService.getStreamTimeout(req.getModelId());
        SseEmitter emitter = new SseEmitter((long) timeout);
        AiStreamControl control = new AiStreamControl();
        AtomicReference<ScheduledFuture<?>> heartbeat = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> deadline = new AtomicReference<>();
        Runnable cleanup = () -> {
            control.cancel();
            ScheduledFuture<?> tick = heartbeat.getAndSet(null);
            if (tick != null) tick.cancel(false);
            ScheduledFuture<?> limit = deadline.getAndSet(null);
            if (limit != null) limit.cancel(false);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        String model = aiService.getResolvedModel(req.getModelId());
        deadline.set(streams.deadline(() -> {
            cleanup.run();
            emitter.completeWithError(new TimeoutException("AI generation timed out"));
        }, timeout));
        heartbeat.set(streams.heartbeat(() -> {
            try {
                // Also detects a client that disconnects while the model is idle.
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (Exception e) {
                cleanup.run();
                emitter.completeWithError(e);
            }
        }));
        // A very short deadline can fire while the timer handles are being registered.
        if (control.isCancelled()) { cleanup.run(); return emitter; }
        try {
            streams.execute(() -> {
                try {
                    if (control.isCancelled()) return;
                    emitter.send(SseEmitter.event().name("model").data(model));
                    aiService.streamCompletion(req.getModelId(), req.getContext(), req.getPrompt(), req.getLang(), req.getHistory(), control, chunk -> {
                        try {
                            if (!control.isCancelled()) emitter.send(SseEmitter.event().name("chunk").data(objectMapper.writeValueAsString(chunk)));
                        } catch (IOException e) {
                            control.cancel();
                            throw new RuntimeException(e);
                        }
                    });
                    if (!control.isCancelled()) {
                        emitter.send(SseEmitter.event().name("done").data("ok"));
                        emitter.complete();
                    }
                } catch (Exception e) {
                    if (!control.isCancelled()) {
                        try {
                            emitter.send(SseEmitter.event().name("error").data(e.getMessage() == null ? "AI generation failed" : e.getMessage()));
                        } catch (Exception ignored) {
                        }
                        emitter.complete();
                    }
                } finally {
                    cleanup.run();
                }
            });
        } catch (Exception e) {
            cleanup.run();
            emitter.completeWithError(e);
        }
        return emitter;
    }
}
