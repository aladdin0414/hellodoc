package com.nopkg.hellodoc.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.security.RequireDocRole;
import com.nopkg.hellodoc.services.AiConversationStore;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/docs/{docId}/ai-conversation")
@RequiredArgsConstructor
public class AiConversationController {
    private final AiConversationStore store;
    private final KbService kbService;
    private final ObjectMapper mapper;

    private Long userId() {
        return kbService.requireUserId(SecurityContextHolder.getContext().getAuthentication().getName());
    }
    @GetMapping
    @RequireDocRole
    public ApiResponse<JsonNode> load(@PathVariable Long docId) throws Exception {
        return ApiResponse.success(mapper.readTree(store.load(userId(), docId)));
    }
    @PutMapping
    @RequireDocRole
    public ApiResponse<Void> save(@PathVariable Long docId, @RequestBody JsonNode messages) throws Exception {
        if (!messages.isArray() || messages.size() > 1000 || messages.toString().length() > 2000000) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "Invalid conversation");
        }
        store.save(userId(), docId, mapper.writeValueAsString(messages));
        return ApiResponse.success(null);
    }
}
