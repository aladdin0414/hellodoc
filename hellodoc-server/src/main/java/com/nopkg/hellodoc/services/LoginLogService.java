package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.SysLoginLog;
import com.nopkg.hellodoc.repositories.LoginLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 登录日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final LoginLogRepository loginLogRepository;

    /**
     * 记录登录日志
     */
    @Transactional
    public void recordLogin(Long userId, Long authId, String ip, String device, String userAgent, boolean success) {
        SysLoginLog loginLog = new SysLoginLog();
        loginLog.setUserId(userId);
        loginLog.setAuthId(authId);
        loginLog.setIp(ip);
        loginLog.setDevice(device);
        loginLog.setUserAgent(userAgent);
        loginLog.setSuccess(success);
        loginLog.setLoginTime(Instant.now());

        loginLogRepository.save(loginLog);

        if (success) {
            log.info("User {} logged in successfully from IP: {}", userId, ip);
        } else {
            log.warn("Failed login attempt for user {} from IP: {}", userId, ip);
        }
    }

    /**
     * 获取用户登录历史
     */
    public List<SysLoginLog> getLoginHistory(Long userId) {
        return loginLogRepository.findTop10ByUserIdOrderByLoginTimeDesc(userId);
    }

    /**
     * 获取指定认证方式的最近登录记录
     */
    public List<SysLoginLog> getRecentLogins(Long authId) {
        return loginLogRepository.findByAuthIdOrderByLoginTimeDesc(authId);
    }
}
