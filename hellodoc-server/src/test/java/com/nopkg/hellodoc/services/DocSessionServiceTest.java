package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.DocSession;
import com.nopkg.hellodoc.repositories.DocSessionRepository;
import com.nopkg.hellodoc.services.impl.DocSessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DocSessionServiceTest {

    @Mock
    private DocSessionRepository sessionRepository;

    @InjectMocks
    private DocSessionServiceImpl sessionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testJoinSession() {
        Long docId = 1L;
        Long userId = 100L;
        String sessionId = UUID.randomUUID().toString();

        when(sessionRepository.save(any(DocSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocSession session = sessionService.joinSession(docId, userId, sessionId);

        assertNotNull(session);
        assertEquals(docId, session.getDocId());
        assertEquals(userId, session.getUserId());
        assertEquals(sessionId, session.getSessionId());
        assertTrue(session.getIsActive());
        assertNotNull(session.getUserColor());
    }

    @Test
    void testLeaveSession() {
        String sessionId = "test-session";
        DocSession session = new DocSession();
        session.setSessionId(sessionId);
        session.setIsActive(true);

        when(sessionRepository.findBySessionId(sessionId)).thenReturn(session);

        sessionService.leaveSession(sessionId);

        // verify(sessionRepository).save(session); // If using soft delete
        // assertFalse(session.getIsActive());
        verify(sessionRepository).delete(session); // If using hard delete
    }

    @Test
    void testUpdateCursor() {
        String sessionId = "test-session";
        String cursor = "{\"line\":1,\"column\":1}";
        DocSession session = new DocSession();
        session.setSessionId(sessionId);

        when(sessionRepository.findBySessionId(sessionId)).thenReturn(session);

        sessionService.updateCursor(sessionId, cursor);

        verify(sessionRepository).save(session);
        assertEquals(cursor, session.getCursorPosition());
    }

    @Test
    void testHeartbeat() {
        String sessionId = "test-session";
        DocSession session = new DocSession();
        session.setSessionId(sessionId);
        session.setLastHeartbeat(java.time.LocalDateTime.now().minusSeconds(20));

        when(sessionRepository.findBySessionId(sessionId)).thenReturn(session);

        sessionService.heartbeat(sessionId);

        verify(sessionRepository).save(session);
        assertNotNull(session.getLastHeartbeat());
        // Heartbeat should update to recent time
        assertTrue(session.getLastHeartbeat().isAfter(java.time.LocalDateTime.now().minusSeconds(5)));
    }

    @Test
    void testGetActiveSessions() {
        Long docId = 1L;
        DocSession session1 = new DocSession();
        session1.setDocId(docId);
        session1.setIsActive(true);

        DocSession session2 = new DocSession();
        session2.setDocId(docId);
        session2.setIsActive(true);

        when(sessionRepository.findByDocIdAndIsActiveTrue(docId))
                .thenReturn(java.util.Arrays.asList(session1, session2));

        var result = sessionService.getActiveSessions(docId);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(DocSession::getIsActive));
    }

    @Test
    void testCleanupStaleSessions() {
        // This method is scheduled, so we test it can be called
        sessionService.cleanupStaleSessions();

        // Verify it calls repository to delete old sessions (30 seconds ago)
        verify(sessionRepository)
                .deleteByLastHeartbeatBefore(argThat(cutoff -> cutoff.isBefore(java.time.LocalDateTime.now()) &&
                        cutoff.isAfter(java.time.LocalDateTime.now().minusSeconds(35))));
    }

    @Test
    void testHeartbeat_SessionNotFound() {
        String sessionId = "non-existent";
        when(sessionRepository.findBySessionId(sessionId)).thenReturn(null);

        sessionService.heartbeat(sessionId);

        verify(sessionRepository, never()).save(any(DocSession.class));
    }
}
