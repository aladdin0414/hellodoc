package com.nopkg.hellodoc.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.entities.DocLock;
import com.nopkg.hellodoc.entities.DocSession;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.LockType;
import com.nopkg.hellodoc.services.DocLockService;
import com.nopkg.hellodoc.services.DocSessionService;
import com.nopkg.hellodoc.services.PermissionChecker;
import com.nopkg.hellodoc.utils.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocCollabHandler extends TextWebSocketHandler {

    private final DocSessionService docSessionService;
    private final DocLockService docLockService;
    private final PermissionChecker permissionChecker;
    private final ObjectMapper objectMapper;

    // 用于广播的内存中会话跟踪
    // docId -> Map<sessionId, WebSocketSession>
    private final Map<Long, Map<String, WebSocketSession>> docSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long docId = getDocId(session);
        if (docId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        Long userId = (Long) session.getAttributes().get("userId");
        String sessionId = (String) session.getAttributes().get("sessionId");
        String username = (String) session.getAttributes().get("username");

        try {
            permissionChecker.checkDocRole(userId, docId, DocRole.VIEWER);
        } catch (Exception e) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        log.info("User {} joined doc {} with session {}", userId, docId, sessionId);

        DocSession savedSession = docSessionService.joinSession(docId, userId, sessionId);
        if (savedSession != null && savedSession.getUserColor() != null) {
            session.getAttributes().put("userColor", savedSession.getUserColor());
        }

        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(session, 10_000, 512 * 1024);

        // 添加到广播映射中
        docSessions.computeIfAbsent(docId, k -> new ConcurrentHashMap<>()).put(sessionId, safeSession);

        // 在全局范围内跟踪用户会话
        trackUserSession(userId, safeSession);

        // 广播加入消息（join message）
        Map<String, Object> user = new HashMap<>();
        user.put("id", userId);
        user.put("name", username);
        user.put("color", session.getAttributes().get("userColor"));

        Map<String, Object> presence = new HashMap<>();
        presence.put("type", "join");
        presence.put("sessionId", sessionId);
        presence.put("user", user);

        broadcast(docId, sessionId, new WsMessage("presence", presence));

        sendMessage(safeSession, buildSyncMessage(docId, sessionId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long docId = getDocId(session);
        String sessionId = (String) session.getAttributes().get("sessionId");

        if (docId == null)
            return;

        try {
            WebSocketSession safe = resolveSafeSession(docId, sessionId, session);
            WsMessage wsMsg = objectMapper.readValue(message.getPayload(), WsMessage.class);

            switch (wsMsg.getType()) {
                case "join":
                    sendMessage(safe, buildSyncMessage(docId, sessionId));
                    break;
                case "reconnect":
                    sendMessage(safe, buildReconnectAckMessage(docId, sessionId));
                    break;
                case "operation":
                    sendMessage(safe, new WsMessage("error", Map.of(
                            "code", "MVP_READONLY",
                            "message", MessageUtils.get("ws.collab.lock_only"))));
                    break;
                case "cursor":
                    handleCursor(sessionId, wsMsg);
                    broadcast(docId, sessionId, new WsMessage("remote_cursor",
                            Map.of("sessionId", sessionId, "position", wsMsg.getData())));
                    break;
                case "heartbeat":
                    docSessionService.heartbeat(sessionId);
                    docLockService.refreshLocksForSession(sessionId);
                    break;
                case "lock":
                    handleLockMessage(safe, docId, sessionId, wsMsg);
                    break;
                case "unlock":
                    handleUnlockMessage(safe, docId, sessionId, wsMsg);
                    break;
                default:
                    log.warn("Unknown message type: {}", wsMsg.getType());
            }
        } catch (Exception e) {
            log.error("Error handling message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long docId = getDocId(session);
        String sessionId = (String) session.getAttributes().get("sessionId");

        if (docId != null && sessionId != null) {
            docSessionService.leaveSession(sessionId);
            docLockService.releaseAllLocksForSession(sessionId);

            // 从映射中移除
            Map<String, WebSocketSession> sessions = docSessions.get(docId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    docSessions.remove(docId);
                }
            }

            // 广播离开消息
            broadcast(docId, sessionId, new WsMessage("presence", Map.of("type", "leave", "sessionId", sessionId)));

            broadcast(docId, sessionId, buildLockStateMessage(docId));
        }

        Long userId = (Long) session.getAttributes().get("userId");
        removeUserSession(userId, session);
    }

    private void broadcast(Long docId, String senderSessionId, WsMessage message) {
        Map<String, WebSocketSession> sessions = docSessions.get(docId);
        if (sessions == null)
            return;

        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.error("Error serializing message", e);
            return;
        }

        sessions.forEach((sid, s) -> {
            if (!sid.equals(senderSessionId) && s.isOpen()) {
                try {
                    s.sendMessage(new TextMessage(payload));
                } catch (Exception e) {
                    log.error("Error sending message to {}", sid, e);
                }
            }
        });
    }

    private final Map<Long, List<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    private void trackUserSession(Long userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(session);
    }

    private void removeUserSession(Long userId, WebSocketSession session) {
        if (userId != null) {
            List<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.removeIf(s -> s != null && s.getId().equals(session.getId()));
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
    }

    public void sendToUser(Long userId, WsMessage message) {
        List<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null)
            return;

        try {
            String payload = objectMapper.writeValueAsString(message);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(payload));
                    } catch (Exception e) {
                        log.error("Error sending notification to user {}", userId, e);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error serializing message", e);
        }
    }

    private void sendMessage(WebSocketSession session, WsMessage message) {
        try {
            if (session.isOpen()) {
                String payload = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(payload));
            }
        } catch (Exception e) {
            log.error("Error sending message to {}", session.getId(), e);
        }
    }

    private WebSocketSession resolveSafeSession(Long docId, String sessionId, WebSocketSession fallback) {
        if (docId == null || sessionId == null) {
            return fallback;
        }
        Map<String, WebSocketSession> sessions = docSessions.get(docId);
        if (sessions == null) {
            return fallback;
        }
        WebSocketSession safe = sessions.get(sessionId);
        return safe != null ? safe : fallback;
    }

    private Long getDocId(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null)
                return null;
            String path = uri.getPath();
            // /ws/doc/{docId}
            String[] parts = path.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            return null;
        }
    }

    private WsMessage buildSyncMessage(Long docId, String selfSessionId) {
        return new WsMessage("sync", Map.of(
                "selfSessionId", selfSessionId,
                "activeEditors", buildActiveEditors(docId),
                "locks", buildLockPayloads(docId)));
    }

    private WsMessage buildReconnectAckMessage(Long docId, String selfSessionId) {
        return new WsMessage("reconnect_ack", Map.of(
                "success", true,
                "selfSessionId", selfSessionId,
                "activeEditors", buildActiveEditors(docId),
                "locks", buildLockPayloads(docId)));
    }

    private WsMessage buildLockStateMessage(Long docId) {
        return new WsMessage("lock_state", Map.of("locks", buildLockPayloads(docId)));
    }

    private List<Map<String, Object>> buildActiveEditors(Long docId) {
        Map<String, WebSocketSession> sessions = docSessions.get(docId);
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> editors = new ArrayList<>();
        sessions.forEach((sid, s) -> {
            Long userId = (Long) s.getAttributes().get("userId");
            String username = (String) s.getAttributes().get("username");
            Object userColor = s.getAttributes().get("userColor");
            Map<String, Object> editor = new HashMap<>();
            editor.put("sessionId", sid);
            editor.put("userId", userId);
            editor.put("username", username);
            editor.put("userColor", userColor);
            editors.add(editor);
        });
        return editors;
    }

    private List<Map<String, Object>> buildLockPayloads(Long docId) {
        List<DocLock> locks = docLockService.getDocLocks(docId);
        if (locks == null || locks.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (DocLock lock : locks) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", lock.getId());
            payload.put("docId", lock.getDocId());
            payload.put("userId", lock.getUserId());
            payload.put("sessionId", lock.getSessionId());
            payload.put("lockType", lock.getLockType() != null ? lock.getLockType().name() : null);
            payload.put("blockId", lock.getBlockId());
            payload.put("rangeStart", lock.getRangeStart());
            payload.put("rangeEnd", lock.getRangeEnd());
            payload.put("acquiredAt", lock.getAcquiredAt() != null ? lock.getAcquiredAt().toString() : null);
            payload.put("expiresAt", lock.getExpiresAt() != null ? lock.getExpiresAt().toString() : null);
            payloads.add(payload);
        }
        return payloads;
    }

    private void handleCursor(String sessionId, WsMessage message) {
        try {
            String cursorJson = objectMapper.writeValueAsString(message.getData());
            docSessionService.updateCursor(sessionId, cursorJson);
        } catch (Exception e) {
            log.error("Error handling cursor", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleLockMessage(WebSocketSession session, Long docId, String sessionId, WsMessage wsMsg) {
        Long userId = (Long) session.getAttributes().get("userId");
        try {
            permissionChecker.checkDocRole(userId, docId, DocRole.EDITOR);
        } catch (Exception e) {
            sendMessage(session, new WsMessage("error", Map.of(
                    "code", "NO_PERMISSION",
                    "message", MessageUtils.get("ws.collab.no_edit_permission"))));
            return;
        }

        Map<String, Object> data = (wsMsg.getData() instanceof Map) ? (Map<String, Object>) wsMsg.getData() : Map.of();
        String lockTypeRaw = data.getOrDefault("lockType", "DOCUMENT").toString();
        LockType lockType;
        try {
            lockType = LockType.valueOf(lockTypeRaw);
        } catch (Exception e) {
            lockType = LockType.DOCUMENT;
        }
        String blockId = data.get("blockId") != null ? data.get("blockId").toString() : null;

        boolean ok = docLockService.acquireLock(docId, userId, sessionId, lockType, blockId);
        if (!ok) {
            Object current = docLockService.checkLock(docId, lockType, blockId)
                    .map(lock -> {
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("userId", lock.getUserId());
                        payload.put("sessionId", lock.getSessionId());
                        payload.put("lockType", lock.getLockType() != null ? lock.getLockType().name() : null);
                        payload.put("blockId", lock.getBlockId());
                        payload.put("expiresAt", lock.getExpiresAt() != null ? lock.getExpiresAt().toString() : null);
                        return payload;
                    })
                    .orElse(null);
            Map<String, Object> err = new HashMap<>();
            err.put("code", "LOCKED");
            err.put("message", MessageUtils.get("ws.collab.editing_by_other"));
            err.put("currentLock", current);
            sendMessage(session, new WsMessage("error", err));
            return;
        }

        WsMessage lockState = buildLockStateMessage(docId);
        sendMessage(session, lockState);
        broadcast(docId, sessionId, lockState);
    }

    @SuppressWarnings("unchecked")
    private void handleUnlockMessage(WebSocketSession session, Long docId, String sessionId, WsMessage wsMsg) {
        Map<String, Object> data = (wsMsg.getData() instanceof Map) ? (Map<String, Object>) wsMsg.getData() : Map.of();
        String lockTypeRaw = data.getOrDefault("lockType", "DOCUMENT").toString();
        LockType lockType;
        try {
            lockType = LockType.valueOf(lockTypeRaw);
        } catch (Exception e) {
            lockType = LockType.DOCUMENT;
        }
        String blockId = data.get("blockId") != null ? data.get("blockId").toString() : null;

        docLockService.releaseLock(docId, sessionId, lockType, blockId);

        WsMessage lockState = buildLockStateMessage(docId);
        sendMessage(session, lockState);
        broadcast(docId, sessionId, lockState);
    }
}
