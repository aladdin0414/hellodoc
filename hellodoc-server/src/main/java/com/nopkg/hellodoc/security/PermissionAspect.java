package com.nopkg.hellodoc.security;

import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.services.PermissionChecker;
import com.nopkg.hellodoc.utils.MessageUtils;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionAspect {

    private final PermissionChecker permissionChecker;
    private final KbService kbService;

    @Before("@annotation(requireKbRole)")
    public void checkKbPermission(JoinPoint joinPoint, RequireKbRole requireKbRole) {
        Long userId = getCurrentUserId();
        Long kbId = getResourceId(joinPoint, requireKbRole.idParam());

        if (kbId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, MessageUtils.get("legacy.permission.parameter_id_missing", "KB"));
        }

        permissionChecker.checkKbRole(userId, kbId, requireKbRole.value());
    }

    @Before("@annotation(requireDocRole)")
    public void checkDocPermission(JoinPoint joinPoint, RequireDocRole requireDocRole) {
        Long userId = getCurrentUserId();
        Long docId = getResourceId(joinPoint, requireDocRole.idParam());

        if (docId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, MessageUtils.get("legacy.permission.parameter_id_missing", "DOC"));
        }

        permissionChecker.checkDocRole(userId, docId, requireDocRole.value());
    }

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username == null || "anonymousUser".equals(username)) {
            return null; // 用于公开文档或匿名访问
        }
        return kbService.requireUserId(username);
    }

    private Long getResourceId(JoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (parameterNames == null) {
            log.warn("Parameter names are null for method: {}", signature.getMethod().getName());
            return null;
        }

        log.debug("Found parameter names: {}", Arrays.toString(parameterNames));
        log.debug("Searching for parameter: {}", paramName);

        for (int i = 0; i < parameterNames.length; i++) {
            if (parameterNames[i].equals(paramName)) {
                Object arg = args[i];
                log.debug("Found matching parameter '{}' at index {}: {}", paramName, i, arg);
                if (arg instanceof Long) {
                    return (Long) arg;
                } else if (arg instanceof String) {
                    try {
                        return Long.parseLong((String) arg);
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse resource ID from string: {}", arg);
                        return null;
                    }
                }
            }
        }
        log.warn("Parameter '{}' not found in: {}", paramName, Arrays.toString(parameterNames));
        return null;
    }
}
