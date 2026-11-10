package com.nopkg.hellodoc.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;

/** Commits the SDK's validated SSE response before it waits for the first event. */
@Component
class McpSseHeadersFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod()) || !"/api/mcp".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            @Override
            public PrintWriter getWriter() throws IOException {
                PrintWriter writer = super.getWriter();
                if (getStatus() == HttpServletResponse.SC_OK && getContentType() != null
                        && getContentType().startsWith("text/event-stream")) {
                    flushBuffer();
                }
                return writer;
            }
        });
    }
}
