package com.nopkg.hellodoc.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;

@Component
public class RequestInfoProvider {

    public InetAddress getClientIpAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String ip = headerFirstIp(request.getHeader("X-Forwarded-For"));
        if (!StringUtils.hasText(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip)) {
            ip = request.getRemoteAddr();
        }
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        try {
            return InetAddress.getByName(ip.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public String getUserAgent() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        return request.getHeader("User-Agent");
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    private String headerFirstIp(String xff) {
        if (!StringUtils.hasText(xff)) {
            return null;
        }
        int comma = xff.indexOf(',');
        if (comma >= 0) {
            return xff.substring(0, comma);
        }
        return xff;
    }
}
