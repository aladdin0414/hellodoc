package com.nopkg.hellodoc.websocket;

import com.nopkg.hellodoc.security.JwtTokenProvider;
import com.nopkg.hellodoc.services.ConfigService;
import com.nopkg.hellodoc.services.KbService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final KbService kbService;
    private final ConfigService configService;

    @Override
    public boolean beforeHandshake(@org.springframework.lang.NonNull ServerHttpRequest request,
            @org.springframework.lang.NonNull ServerHttpResponse response,
            @org.springframework.lang.NonNull WebSocketHandler wsHandler,
            @org.springframework.lang.NonNull Map<String, Object> attributes) {

        // 检查协作功能是否开启
        Boolean collabEnabled = configService.getConfigValue("app.collab.enabled", Boolean.class);
        if (collabEnabled == null || !collabEnabled) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        // 提取 Token
        String token = extractToken(request);

        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // 验证 Token
        if (!jwtTokenProvider.validateTokenStrict(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            // 获取用户名并验证用户是否存在
            String username = jwtTokenProvider.getUsernameFromToken(token);
            Long userId = kbService.requireUserId(username);

            attributes.put("userId", userId);
            attributes.put("username", username);
            attributes.put("sessionId", UUID.randomUUID().toString());

            return true;
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(@org.springframework.lang.NonNull ServerHttpRequest request,
            @org.springframework.lang.NonNull ServerHttpResponse response,
            @org.springframework.lang.NonNull WebSocketHandler wsHandler,
            @org.springframework.lang.Nullable Exception exception) {
        // 不执行任何操作
    }

    private String extractToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();

            // 尝试查询参数
            String queryToken = servletRequest.getParameter("token");
            if (StringUtils.hasText(queryToken)) {
                return queryToken;
            }

            // 尝试 Header（尽管通常浏览器中的 JS WebSocket 握手不容易允许自定义 Header）
            String authorization = servletRequest.getHeader("Authorization");
            if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
                return authorization.substring(7);
            }
        }
        return null;
    }
}
