package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.services.AiService;
import com.nopkg.hellodoc.web.dto.ai.AiCompletionReq;
import com.nopkg.hellodoc.web.dto.ai.AiCompletionResp;
import com.nopkg.hellodoc.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI Integration", description = "AI assisted editing features")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/completion")
    @Operation(summary = "Generate AI completion for selected text")
    public ApiResponse<AiCompletionResp> completion(@RequestBody AiCompletionReq req) {
        if (req.getContext() == null || req.getPrompt() == null) {
            throw new com.nopkg.hellodoc.exceptions.BusinessException(ApiResponse.Code.PARAM_ERROR, "参数不能为空");
        }
        String result = aiService.getCompletion(req.getContext(), req.getPrompt());
        String model = aiService.getResolvedModel();
        return ApiResponse.success(new AiCompletionResp(result, model));
    }

    @PostMapping(value = "/completion/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Generate AI completion stream for selected text")
    public SseEmitter completionStream(@RequestBody AiCompletionReq req) {
        if (req.getContext() == null || req.getPrompt() == null) {
            throw new com.nopkg.hellodoc.exceptions.BusinessException(ApiResponse.Code.PARAM_ERROR, "参数不能为空");
        }
        SseEmitter emitter = new SseEmitter(0L);
        String model = aiService.getResolvedModel();
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().name("model").data(model));
                aiService.streamCompletion(req.getContext(), req.getPrompt(), chunk -> {
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
                    // ignore
                }
                emitter.complete();
            }
        });
        return emitter;
    }
}
