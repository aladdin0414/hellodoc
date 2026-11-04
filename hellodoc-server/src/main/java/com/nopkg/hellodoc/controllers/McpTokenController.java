package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.mcp.McpAccessTokenService;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.web.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/mcp-tokens")
public class McpTokenController {
    private final McpAccessTokenService tokens;
    private final KbService users;
    public McpTokenController(McpAccessTokenService tokens, KbService users) { this.tokens = tokens; this.users = users; }
    public record CreateRequest(String name) {}
    @PostMapping
    public ResponseEntity<ApiResponse<McpAccessTokenService.IssuedToken>> create(Authentication auth, @RequestBody CreateRequest request) {
        var token = tokens.create(users.requireUserId(auth.getName()), auth.getName(), request.name());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(token));
    }
    @GetMapping
    public ApiResponse<List<McpAccessTokenService.TokenInfo>> list(Authentication auth) { return ApiResponse.success(tokens.list(users.requireUserId(auth.getName()))); }
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> revoke(Authentication auth, @PathVariable String id) {
        return tokens.revoke(users.requireUserId(auth.getName()), id) ? ResponseEntity.ok(ApiResponse.success()) : ResponseEntity.notFound().build();
    }
}
