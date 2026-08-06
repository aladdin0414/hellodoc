package com.nopkg.hellodoc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.entities.DocOperation;
import com.nopkg.hellodoc.repositories.DocOperationRepository;
import com.nopkg.hellodoc.services.impl.DocCollabServiceImpl;
import com.nopkg.hellodoc.websocket.WsMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DocCollabServiceTest {

    @Mock
    private DocOperationRepository operationRepository;

    @Mock
    private DocSessionService sessionService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DocCollabServiceImpl collabService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testProcessOperation_FirstOp() throws JsonProcessingException {
        Long docId = 1L;
        String sessionId = "s1";
        WsMessage msg = new WsMessage("operation", Map.of("insert", "a"));

        when(operationRepository.findTopByDocIdOrderByServerVersionDesc(docId)).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"insert\":\"a\"}");
        when(operationRepository.save(any(DocOperation.class))).thenAnswer(i -> {
            DocOperation op = i.getArgument(0);
            assertEquals(1, op.getServerVersion());
            return op;
        });

        WsMessage result = collabService.processOperation(docId, sessionId, msg);

        verify(operationRepository).save(any(DocOperation.class));
        assertEquals(msg, result);
    }

    @Test
    void testProcessOperation_NextOp() throws JsonProcessingException {
        Long docId = 1L;
        String sessionId = "s1";
        WsMessage msg = new WsMessage("operation", "data");

        DocOperation lastOp = new DocOperation();
        lastOp.setServerVersion(5);

        when(operationRepository.findTopByDocIdOrderByServerVersionDesc(docId)).thenReturn(lastOp);
        when(objectMapper.writeValueAsString(any())).thenReturn("\"data\"");
        when(operationRepository.save(any(DocOperation.class))).thenAnswer(i -> {
            DocOperation op = i.getArgument(0);
            assertEquals(6, op.getServerVersion());
            return op;
        });

        collabService.processOperation(docId, sessionId, msg);

        verify(operationRepository).save(any(DocOperation.class));
    }

    @Test
    void testHandleCursor() throws JsonProcessingException {
        Long docId = 1L;
        String sessionId = "s1";
        Object cursorData = Map.of("line", 1);
        WsMessage msg = new WsMessage("cursor", cursorData);

        when(objectMapper.writeValueAsString(cursorData)).thenReturn("{\"line\":1}");

        collabService.handleCursor(docId, sessionId, msg);

        verify(sessionService).updateCursor(sessionId, "{\"line\":1}");
    }

    @Test
    void testHandleJoin() {
        Long docId = 1L;
        Long userId = 100L;

        DocOperation lastOp = new DocOperation();
        lastOp.setServerVersion(10);
        when(operationRepository.findTopByDocIdOrderByServerVersionDesc(docId)).thenReturn(lastOp);

        WsMessage result = collabService.handleJoin(docId, userId);

        assertEquals("sync", result.getType());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(10, data.get("version"));
    }

    @Test
    void testHandleReconnect() {
        Long docId = 1L;
        String sessionId = "s1";
        Integer clientVersion = 5;

        DocOperation lastOp = new DocOperation();
        lastOp.setServerVersion(10);

        when(operationRepository.findTopByDocIdOrderByServerVersionDesc(docId)).thenReturn(lastOp);

        when(operationRepository.findByDocIdAndServerVersionGreaterThanOrderByServerVersionAsc(docId, clientVersion))
                .thenReturn(java.util.Collections.emptyList()); // Mock returning list

        WsMessage result = collabService.handleReconnect(docId, sessionId, clientVersion);

        assertEquals("reconnect_ack", result.getType());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(true, data.get("success"));
        assertEquals(10, data.get("currentVersion"));
    }

    @Test
    void testHandleReconnect_NullClientVersion() {
        Long docId = 1L;
        String sessionId = "s1";
        Integer clientVersion = null;

        DocOperation lastOp = new DocOperation();
        lastOp.setServerVersion(5);

        when(operationRepository.findTopByDocIdOrderByServerVersionDesc(docId)).thenReturn(lastOp);
        when(operationRepository.findByDocIdAndServerVersionGreaterThanOrderByServerVersionAsc(docId, 0))
                .thenReturn(java.util.Collections.emptyList());

        WsMessage result = collabService.handleReconnect(docId, sessionId, clientVersion);

        assertEquals("reconnect_ack", result.getType());
        assertNotNull(result.getData());
    }

    @Test
    void testProcessOperation_Error() throws JsonProcessingException {
        Long docId = 1L;
        String sessionId = "s1";
        WsMessage msg = new WsMessage("operation", new Object());

        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Error") {
        });

        assertThrows(RuntimeException.class, () -> {
            collabService.processOperation(docId, sessionId, msg);
        });
    }
}
