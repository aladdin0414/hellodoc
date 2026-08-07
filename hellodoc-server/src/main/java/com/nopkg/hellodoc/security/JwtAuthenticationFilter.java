package com.nopkg.hellodoc.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token)) {

            String username;
            try {
                username = jwtTokenProvider.getUsernameFromToken(token);
            } catch (JwtException e) {
                sendOAuthError(response, "invalid_token", com.nopkg.hellodoc.utils.MessageUtils.get("api.code.TOKEN_INVALID"), false);
                return;
            }

            // 1. Token 解析失败
            if (username == null) {
                sendOAuthError(response, "invalid_token", com.nopkg.hellodoc.utils.MessageUtils.get("api.code.TOKEN_INVALID"), false);
                return;
            }

            // 2. Token 过期 → 返回 OAuth2 标准格式，前端会用 refresh_token 自动刷新
            if (jwtTokenProvider.isTokenExpired(token)) {
                sendOAuthError(response, "invalid_token", com.nopkg.hellodoc.utils.MessageUtils.get("legacy.storage.link_expired"), true);
                return;
            }

            // 3. 其他非法 Token 情况
            if (!jwtTokenProvider.validateTokenStrict(token)) {
                sendOAuthError(response, "invalid_token", com.nopkg.hellodoc.utils.MessageUtils.get("legacy.storage.invalid_signature"), false);
                return;
            }

            // 4. 限制 Refresh Token 不能直接用于业务 API 认证
            if (jwtTokenProvider.isRefreshToken(token)) {
                sendOAuthError(response, "invalid_token", com.nopkg.hellodoc.utils.MessageUtils.get("api.code.TOKEN_TYPE_ERROR"), false);
                return;
            }

            // 4. Token 正常 → 设置认证上下文
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (!userDetails.isEnabled()) {
                    sendOAuthError(response, "invalid_token", com.nopkg.hellodoc.utils.MessageUtils.get("api.code.ACCOUNT_DISABLED"), false);
                    return;
                }
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 返回符合 OAuth2 标准格式的错误结构
     */
    private void sendOAuthError(HttpServletResponse response,
            String error,
            String description,
            boolean needRefresh) throws IOException {

        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        // 标准 OAuth2 header
        String headerDescription = buildSafeHeaderDescription(error, needRefresh);
        response.setHeader("WWW-Authenticate",
                String.format("Bearer error=\"%s\", error_description=\"%s\"",
                        escapeHttpQuotedString(error),
                        escapeHttpQuotedString(headerDescription)));

        // JSON 响应格式（前端现有逻辑可识别）
        String json = String.format(
                "{\"error\":\"%s\",\"error_description\":\"%s\",\"needRefresh\":%b}",
                jsonEscape(error), jsonEscape(description), needRefresh);

        response.getWriter().write(json);
    }

    private static String buildSafeHeaderDescription(String error, boolean needRefresh) {
        if (needRefresh) {
            return "token_expired";
        }
        if ("invalid_token".equals(error)) {
            return "invalid_token";
        }
        return "unauthorized";
    }

    private static String escapeHttpQuotedString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
