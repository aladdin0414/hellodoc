package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.DocSession;
import java.util.List;

public interface DocSessionService {
    DocSession joinSession(Long docId, Long userId, String sessionId);

    void leaveSession(String sessionId);

    void updateCursor(String sessionId, String cursorPosition);

    void heartbeat(String sessionId);

    List<DocSession> getActiveSessions(Long docId);

    void cleanupStaleSessions();
}
