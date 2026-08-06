package com.nopkg.hellodoc.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统信息控制器
 * 用于提供后端应用版本号与运行状态接口
 */
@RestController
@RequestMapping("/api/system")
public class SystemInfoController {

    /**
     * 系统版本号（在 build.gradle / application.properties 中配置）
     */
    @Value("${app.version:2.0.0}")
    private String version;

    /**
     * 获取后端系统信息接口
     *
     * @return 包含版本号、系统名称与当前服务器时间的 Map
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "HelloDoc Server");
        info.put("version", version);
        info.put("status", "UP");
        info.put("serverTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return ResponseEntity.ok(info);
    }
}
