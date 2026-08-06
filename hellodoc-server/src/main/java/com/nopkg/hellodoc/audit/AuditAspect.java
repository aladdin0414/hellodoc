package com.nopkg.hellodoc.audit;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.repositories.KbKnowledgeBaseRepository;
import com.nopkg.hellodoc.services.AuditLogService;
import com.nopkg.hellodoc.services.KbService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;
    private final KbService kbService;
    private final KbDocumentRepository documentRepository;
    private final KbKnowledgeBaseRepository kbRepository;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        Long userId = currentUserIdOrNull();
        Long targetId = resolveTargetId(pjp, auditable, null);
        Object oldValue = resolveOldValue(auditable, targetId);

        Object result = pjp.proceed();

        if (targetId == null) {
            targetId = resolveTargetId(pjp, auditable, result);
        }
        Object newValue = resolveNewValue(auditable, targetId, result);
        auditLogService.log(userId, auditable.targetType(), targetId, auditable.action(), oldValue, newValue);
        return result;
    }

    private Long currentUserIdOrNull() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username == null || "anonymousUser".equals(username)) {
            return null;
        }
        return kbService.requireUserId(username);
    }

    private Long resolveTargetId(ProceedingJoinPoint pjp, Auditable auditable, Object result) {
        String expr = auditable.targetIdExpression();
        if (!StringUtils.hasText(expr)) {
            return null;
        }
        try {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            String[] paramNames = nameDiscoverer.getParameterNames(signature.getMethod());
            Object[] args = pjp.getArgs();
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length && i < args.length; i++) {
                    ctx.setVariable(paramNames[i], args[i]);
                }
            }
            ctx.setVariable("result", result);
            Object v = parser.parseExpression(expr).getValue(ctx);
            if (v == null) {
                return null;
            }
            if (v instanceof Number n) {
                return n.longValue();
            }
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private Object resolveOldValue(Auditable auditable, Long targetId) {
        if (targetId == null) {
            return null;
        }
        if (auditable.action() == AuditAction.CREATE) {
            return null;
        }
        return loadSnapshot(auditable.targetType(), targetId).orElse(null);
    }

    private Object resolveNewValue(Auditable auditable, Long targetId, Object result) {
        if (result instanceof KbDocument doc) {
            return snapshotDoc(doc);
        }
        if (result instanceof KbKnowledgeBase kb) {
            return snapshotKb(kb);
        }
        if (targetId == null) {
            return null;
        }
        if (auditable.action() == AuditAction.DELETE) {
            return loadSnapshot(auditable.targetType(), targetId).orElse(null);
        }
        if (auditable.action() == AuditAction.CREATE || auditable.action() == AuditAction.UPDATE
                || auditable.action() == AuditAction.RESTORE) {
            return loadSnapshot(auditable.targetType(), targetId).orElse(null);
        }
        return null;
    }

    private Optional<Map<String, Object>> loadSnapshot(AuditTargetType type, Long targetId) {
        try {
            if (type == AuditTargetType.DOCUMENT) {
                return documentRepository.findById(targetId).map(this::snapshotDoc);
            }
            if (type == AuditTargetType.KB) {
                return kbRepository.findById(targetId).map(this::snapshotKb);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Map<String, Object> snapshotDoc(KbDocument doc) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", doc.getId());
        m.put("kbId", doc.getKb() != null ? doc.getKb().getId() : null);
        m.put("name", doc.getName());
        m.put("type", doc.getType() != null ? doc.getType().getValue() : null);
        m.put("parentId", doc.getParent() != null ? doc.getParent().getId() : null);
        m.put("slug", doc.getSlug());
        m.put("status", doc.getStatus() != null ? doc.getStatus().getValue() : null);
        m.put("isOpen", doc.getIsOpen());
        m.put("isCover", doc.getIsCover());
        m.put("currentVersion", doc.getCurrentVersion());
        m.put("deletedAt", doc.getDeletedAt());
        m.put("updatedAt", doc.getUpdatedAt());
        return m;
    }

    private Map<String, Object> snapshotKb(KbKnowledgeBase kb) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", kb.getId());
        m.put("title", kb.getTitle());
        m.put("description", kb.getDescription());
        m.put("icon", kb.getIcon());
        m.put("color", kb.getColor());
        m.put("ownerId", kb.getOwnerId());
        m.put("allowAnonymous", kb.getAllowAnonymous());
        m.put("visibility", kb.getVisibility() != null ? kb.getVisibility().getValue() : null);
        m.put("publicRole", kb.getPublicRole() != null ? kb.getPublicRole().getValue() : null);
        m.put("deletedAt", kb.getDeletedAt());
        m.put("updatedAt", kb.getUpdatedAt());
        return m;
    }
}
