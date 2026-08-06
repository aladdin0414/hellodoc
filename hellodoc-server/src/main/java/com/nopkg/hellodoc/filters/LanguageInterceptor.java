package com.nopkg.hellodoc.filters;

import com.nopkg.hellodoc.i18n.LanguageContext;
import com.nopkg.hellodoc.repositories.UserAuthRepository;
import com.nopkg.hellodoc.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class LanguageInterceptor extends OncePerRequestFilter {
    @Autowired(required = false)
    private JwtTokenProvider jwtTokenProvider;

    @Autowired(required = false)
    private UserAuthRepository userAuthRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String headerLocale = resolveHeaderLocale(request);
        String userLocale = resolveUserLocale(request);
        String resolvedLocale = StringUtils.hasText(userLocale) ? userLocale : headerLocale;
        LanguageContext.setLocale(resolvedLocale);
        response.setHeader("Content-Language", LanguageContext.getLocale());
        try {
            filterChain.doFilter(request, response);
        } finally {
            LanguageContext.clear();
        }
    }

    private String resolveHeaderLocale(HttpServletRequest request) {
        String xLanguage = request.getHeader("X-Language");
        if (StringUtils.hasText(xLanguage)) {
            return LanguageContext.normalize(xLanguage);
        }
        String acceptLanguage = request.getHeader("Accept-Language");
        if (!StringUtils.hasText(acceptLanguage)) {
            return LanguageContext.DEFAULT_LOCALE;
        }
        String first = acceptLanguage.split(",")[0].trim();
        int index = first.indexOf(';');
        if (index >= 0) {
            first = first.substring(0, index);
        }
        return LanguageContext.normalize(first);
    }

    private String resolveUserLocale(HttpServletRequest request) {
        if (jwtTokenProvider == null || userAuthRepository == null) {
            return null;
        }
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7);
        if (!jwtTokenProvider.validateTokenStrict(token)) {
            return null;
        }
        String username = jwtTokenProvider.getUsernameFromToken(token);
        if (!StringUtils.hasText(username)) {
            return null;
        }
        Optional<String> mode = userAuthRepository.findLanguageModeByIdentifierAndIdentityType(username, "PASSWORD");
        if (mode.isEmpty() || !StringUtils.hasText(mode.get()) || "AUTO".equalsIgnoreCase(mode.get())) {
            return null;
        }
        return LanguageContext.normalize(mode.get());
    }
}
