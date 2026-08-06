package com.nopkg.hellodoc.config;

import com.nopkg.hellodoc.websocket.DocCollabHandler;
import com.nopkg.hellodoc.websocket.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final DocCollabHandler docCollabHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @org.springframework.beans.factory.annotation.Value("${websocket.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(docCollabHandler, "/ws/doc/{docId}")
                .setAllowedOrigins(allowedOrigins)
                .addInterceptors(jwtHandshakeInterceptor);
    }
}
