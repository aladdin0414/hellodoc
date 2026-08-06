package com.nopkg.hellodoc.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.entities.DocOperation;
import com.nopkg.hellodoc.enums.OpType;
import com.nopkg.hellodoc.repositories.DocOperationRepository;
import com.nopkg.hellodoc.services.DocCollabService;
import com.nopkg.hellodoc.services.DocSessionService;
import com.nopkg.hellodoc.websocket.WsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocCollabServiceImpl implements DocCollabService {

    private final DocOperationRepository operationRepository;
    private final DocSessionService sessionService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public WsMessage processOperation(Long docId, String sessionId, WsMessage message) {
        // 假设 message.data 是我们可以序列化的 Map 或对象
        // 基础实现：分配服务器版本并保存。

        DocOperation op = new DocOperation();
        op.setDocId(docId);
        op.setSessionId(sessionId);
        op.setOpType(OpType.INSERT); // 应该从消息中提取
        // 目前假定为通用操作

        try {
            op.setOpData(objectMapper.writeValueAsString(message.getData()));
        } catch (JsonProcessingException e) {
            log.error("Error serializing op data", e);
            throw new RuntimeException("Invalid op data");
        }

        // 获取当前最大版本
        DocOperation lastOp = operationRepository.findTopByDocIdOrderByServerVersionDesc(docId);
        int nextVersion = (lastOp != null && lastOp.getServerVersion() != null) ? lastOp.getServerVersion() + 1 : 1;

        op.setServerVersion(nextVersion);
        // op.setBaseVersion(...) // 应该从客户端消息中获取

        operationRepository.save(op);

        // 返回带有分配版本的消息（如果客户端需要确认）
        // 或者创建一个新的 "ack" 消息
        // 为了简单起见，只返回已处理的消息（可能是增强后的）
        return message;
    }

    @Override
    public void handleCursor(Long docId, String sessionId, WsMessage message) {
        try {
            String cursorJson = objectMapper.writeValueAsString(message.getData());
            sessionService.updateCursor(sessionId, cursorJson);
        } catch (Exception e) {
            log.error("Error handling cursor", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public WsMessage handleJoin(Long docId, Long userId) {
        // 在实际应用中，获取实际内容和版本
        // 目前，返回一个虚拟的同步消息，或者如果有的话从数据库获取
        // 我们需要 DocumentService 或 Repository 来获取内容。
        // 让我们假设内容为空，或者将来从仓库中获取
        DocOperation lastOp = operationRepository.findTopByDocIdOrderByServerVersionDesc(docId);
        int currentVersion = (lastOp != null && lastOp.getServerVersion() != null) ? lastOp.getServerVersion() : 0;

        return new WsMessage("sync", Map.of("version", currentVersion, "content", ""));
    }

    @Override
    @Transactional(readOnly = true)
    public WsMessage handleReconnect(Long docId, String sessionId, Integer lastVersion) {
        // 获取错过的操作（ops）
        DocOperation lastOp = operationRepository.findTopByDocIdOrderByServerVersionDesc(docId);
        int currentVersion = (lastOp != null && lastOp.getServerVersion() != null) ? lastOp.getServerVersion() : 0;

        if (lastVersion == null)
            lastVersion = 0;

        // 简单策略：如果差距较小，则返回操作；否则进行全量同步（此处尚未实现）
        java.util.List<DocOperation> missedOps = operationRepository
                .findByDocIdAndServerVersionGreaterThanOrderByServerVersionAsc(docId, lastVersion);

        return new WsMessage("reconnect_ack", Map.of(
                "success", true,
                "currentVersion", currentVersion,
                "missedOps", missedOps));
    }
}
