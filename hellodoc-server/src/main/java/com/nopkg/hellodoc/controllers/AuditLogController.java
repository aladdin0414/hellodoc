package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.audit.AuditTargetType;
import com.nopkg.hellodoc.entities.KbAuditLog;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.security.RequireDocRole;
import com.nopkg.hellodoc.security.RequireKbRole;
import com.nopkg.hellodoc.services.AuditLogService;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.audit.AuditLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Audit log query APIs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/audit-logs")
    @Operation(summary = "All logs", description = "Admin query for all audit logs")
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<Page<AuditLogVO>> getAll(@RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(0, pageNum - 1), Math.min(100, Math.max(1, pageSize)));
        return ApiResponse.success(auditLogService.getRecentLogs(pageable).map(this::toVo));
    }

    @GetMapping("/audit-logs/user/{userId}")
    @Operation(summary = "User logs", description = "Query audit logs by user ID")
    @PreAuthorize("hasRole('admin') or #userId == authentication.principal.id")
    public ApiResponse<Page<AuditLogVO>> getByUser(@PathVariable Long userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(0, pageNum - 1), Math.min(100, Math.max(1, pageSize)));
        return ApiResponse.success(auditLogService.getUserLogs(userId, pageable).map(this::toVo));
    }

    @GetMapping("/kb/{kbId}/audit-logs")
    @Operation(summary = "Knowledge Base logs", description = "Query audit logs by Knowledge Base ID")
    @RequireKbRole(KbRole.VIEWER)
    public ApiResponse<Page<AuditLogVO>> getByKb(@PathVariable Long kbId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(0, pageNum - 1), Math.min(100, Math.max(1, pageSize)));
        return ApiResponse.success(auditLogService
                .getTargetLogs(AuditTargetType.KB, kbId, pageable).map(this::toVo));
    }

    @GetMapping("/docs/{docId}/audit-logs")
    @Operation(summary = "Document logs", description = "Query audit logs by Document ID")
    @RequireDocRole(DocRole.VIEWER)
    public ApiResponse<Page<AuditLogVO>> getByDoc(@PathVariable Long docId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(0, pageNum - 1), Math.min(100, Math.max(1, pageSize)));
        return ApiResponse.success(auditLogService
                .getTargetLogs(AuditTargetType.DOCUMENT, docId, pageable).map(this::toVo));
    }

    private AuditLogVO toVo(KbAuditLog log) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setTargetType(log.getTargetType() != null ? log.getTargetType().getValue() : null);
        vo.setTargetId(log.getTargetId());
        vo.setAction(log.getAction() != null ? log.getAction().getValue() : null);
        vo.setOldValue(log.getOldValue());
        vo.setNewValue(log.getNewValue());
        vo.setIpAddress(log.getIpAddress() != null ? log.getIpAddress().getHostAddress() : null);
        vo.setUserAgent(log.getUserAgent());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }
}
