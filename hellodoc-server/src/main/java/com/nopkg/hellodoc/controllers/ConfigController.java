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

@Tag(name = "System Configuration", description = "System Parameter Configuration APIs")
@RestController
@RequestMapping("/api/system/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @Operation(summary = "List configurations")
    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<List<SysConfigVO>> list() {
        return ApiResponse.success(configService.listConfigs().stream()
                .map(SysConfigVO::from)
                .collect(Collectors.toList()));
    }

    @Operation(summary = "Update configuration")
    @PutMapping
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<SysConfigVO> update(@RequestBody SysConfig config) {
        return ApiResponse.success(SysConfigVO.from(configService.updateConfig(config)));
    }

    @Operation(summary = "Create configuration")
    @PostMapping
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<SysConfigVO> create(@RequestBody SysConfig config) {
        return ApiResponse.success(SysConfigVO.from(configService.createConfig(config)));
    }

    @Operation(summary = "Refresh configuration cache")
    @PostMapping("/refresh")
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<Void> refresh() {
        configService.refreshCache();
        return ApiResponse.success(null);
    }
}
