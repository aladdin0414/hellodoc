package com.nopkg.hellodoc.web.dto.audit;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class AuditLogVO {
    private Long id;
    private Long userId;
    private String targetType;
    private Long targetId;
    private String action;
    private Map<String, Object> oldValue;
    private Map<String, Object> newValue;
    private String ipAddress;
    private String userAgent;
    private OffsetDateTime createdAt;
}
