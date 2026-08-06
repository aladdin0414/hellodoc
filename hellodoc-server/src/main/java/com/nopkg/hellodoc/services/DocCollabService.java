package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.websocket.WsMessage;

public interface DocCollabService {
    WsMessage processOperation(Long docId, String sessionId, WsMessage message);

    void handleCursor(Long docId, String sessionId, WsMessage message);

    WsMessage handleJoin(Long docId, Long userId);

    WsMessage handleReconnect(Long docId, String sessionId, Integer lastVersion);
    // Returns broadcast message if any
}
