package com.nopkg.hellodoc.services.impl;

import com.nopkg.hellodoc.entities.DocSession;
import com.nopkg.hellodoc.repositories.DocSessionRepository;
import com.nopkg.hellodoc.services.DocSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class DocSessionServiceImpl implements DocSessionService {

    private final DocSessionRepository sessionRepository;

    @Override
    @Transactional
    public DocSession joinSession(Long docId, Long userId, String sessionId) {
        DocSession session = new DocSession();
        session.setDocId(docId);
        session.setUserId(userId);
        session.setSessionId(sessionId);
        session.setIsActive(true);
        session.setJoinedAt(LocalDateTime.now());
        session.setLastHeartbeat(LocalDateTime.now());
        session.setUserColor(generateRandomColor());
        return sessionRepository.save(session);
    }

    @Override
    @Transactional
    public void leaveSession(String sessionId) {
        DocSession session = sessionRepository.findBySessionId(sessionId);
        if (session != null) {
            session.setIsActive(false);
            sessionRepository.save(session);
            // 或者删除：sessionRepository.delete(session);
            // 但对于历史记录/日志，软删除可能更好？
            // 需求说 "离开会话"，实现细节较灵活。
            // 让我们坚持更新活跃状态，或者如果不需要就硬删除。
            // 为了简单起见以及减小表大小，根据 "清理" 逻辑暗示会话是瞬态的，目前采用硬删除。
            sessionRepository.delete(session);
        }
    }

    @Override
    @Transactional
    public void updateCursor(String sessionId, String cursorPosition) {
        DocSession session = sessionRepository.findBySessionId(sessionId);
        if (session != null) {
            session.setCursorPosition(cursorPosition);
            session.setLastHeartbeat(LocalDateTime.now());
            sessionRepository.save(session);
        }
    }

    @Override
    @Transactional
    public void heartbeat(String sessionId) {
        DocSession session = sessionRepository.findBySessionId(sessionId);
        if (session != null) {
            session.setLastHeartbeat(LocalDateTime.now());
            sessionRepository.save(session);
        }
    }

    @Override
    public List<DocSession> getActiveSessions(Long docId) {
        return sessionRepository.findByDocIdAndIsActiveTrue(docId);
    }

    @Override
    @Scheduled(fixedRate = 60000) // 每分钟检查一次
    @Transactional
    public void cleanupStaleSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(30);
        sessionRepository.deleteByLastHeartbeatBefore(cutoff);
    }

    private String generateRandomColor() {
        Random rand = new Random();
        return String.format("#%06x", rand.nextInt(0xffffff + 1));
    }
}
