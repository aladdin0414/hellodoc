package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.SysConfig;
import com.nopkg.hellodoc.services.ConfigService;
import com.nopkg.hellodoc.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.nopkg.hellodoc.web.dto.config.SysConfigVO;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "系统配置管理", description = "系统参数配置")
@RestController
@RequestMapping("/api/system/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @Operation(summary = "配置列表")
    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<List<SysConfigVO>> list() {
        return ApiResponse.success(configService.listConfigs().stream()
                .map(SysConfigVO::from)
                .collect(Collectors.toList()));
    }

    @Operation(summary = "更新配置")
    @PutMapping
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<SysConfigVO> update(@RequestBody SysConfig config) {
        return ApiResponse.success(SysConfigVO.from(configService.updateConfig(config)));
    }

    @Operation(summary = "创建配置项")
    @PostMapping
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<SysConfigVO> create(@RequestBody SysConfig config) {
        return ApiResponse.success(SysConfigVO.from(configService.createConfig(config)));
    }

    @Operation(summary = "刷新配置缓存")
    @PostMapping("/refresh")
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<Void> refresh() {
        configService.refreshCache();
        return ApiResponse.success(null);
    }
}
