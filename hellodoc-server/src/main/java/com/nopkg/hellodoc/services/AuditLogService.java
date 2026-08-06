package com.nopkg.hellodoc.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.audit.AuditAction;
import com.nopkg.hellodoc.audit.AuditTargetType;
import com.nopkg.hellodoc.audit.RequestInfoProvider;
import com.nopkg.hellodoc.entities.KbAuditLog;
import com.nopkg.hellodoc.repositories.KbAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final KbAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final RequestInfoProvider requestInfoProvider;

    @Transactional
    public void log(Long userId, AuditTargetType targetType, Long targetId, AuditAction action, Object oldValue,
            Object newValue) {
        if (targetType == null || targetId == null || action == null) {
            return;
        }

        KbAuditLog log = new KbAuditLog();
        log.setUserId(userId);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setAction(action);
        log.setOldValue(toMap(oldValue));
        log.setNewValue(toMap(newValue));
        log.setIpAddress(requestInfoProvider.getClientIpAddress());
        log.setUserAgent(requestInfoProvider.getUserAgent());
        log.setCreatedAt(OffsetDateTime.now());
        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<KbAuditLog> getTargetLogs(AuditTargetType targetType, Long targetId, Pageable pageable) {
        return auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<KbAuditLog> getUserLogs(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<KbAuditLog> getRecentLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return objectMapper.convertValue(value, Map.class);
    }
}
