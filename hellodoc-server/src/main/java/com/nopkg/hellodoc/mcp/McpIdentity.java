package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.services.KbService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/** Runs a tool using the authentication extracted from its HTTP request. */
@Component
public class McpIdentity {
    private final KbService kbService;

    public McpIdentity(KbService kbService) {
        this.kbService = kbService;
    }

    public <T> T run(Authentication authentication, Function<Long, T> action) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new SecurityException("An authenticated HelloDoc user is required");
        }
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext current = SecurityContextHolder.createEmptyContext();
        current.setAuthentication(authentication);
        SecurityContextHolder.setContext(current);
        try {
            return action.apply(kbService.requireUserId(authentication.getName()));
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
