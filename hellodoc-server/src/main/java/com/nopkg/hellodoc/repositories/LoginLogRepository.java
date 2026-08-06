package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.SysLoginLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoginLogRepository extends JpaRepository<SysLoginLog, Long> {

    /**
     * 获取用户的登录历史，按时间倒序
     */
    List<SysLoginLog> findByUserIdOrderByLoginTimeDesc(Long userId);

    /**
     * 获取指定数量的登录历史
     */
    List<SysLoginLog> findTop10ByUserIdOrderByLoginTimeDesc(Long userId);

    /**
     * 获取认证方式的登录记录
     */
    List<SysLoginLog> findByAuthIdOrderByLoginTimeDesc(Long authId);
}
