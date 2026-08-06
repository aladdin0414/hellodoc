package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.KbStorageConfig;
import com.nopkg.hellodoc.services.StorageConfigService;
import com.nopkg.hellodoc.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/storage/configs")
@RequiredArgsConstructor
@Tag(name = "存储配置", description = "存储配置管理接口")
public class StorageConfigController {

    private final StorageConfigService storageConfigService;

    public record StorageConfigRequest(
            String name,
            String provider,
            String bucket,
            String region,
            String endpoint,
            String accessKeyId,
            String secretKey,
            String cdnDomain,
            Boolean isActive
    ) {
    }

    public record StorageConfigVO(
            Long id,
            String name,
            String provider,
            String bucket,
            String region,
            String endpoint,
            String accessKeyId,
            String cdnDomain,
            Boolean isDefault,
            Boolean isActive,
            Boolean hasSecret,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "创建存储配置")
    public ApiResponse<StorageConfigVO> create(@RequestBody StorageConfigRequest request) {
        KbStorageConfig created = storageConfigService.create(toDto(request));
        return ApiResponse.success(toVo(created));
    }

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "存储配置列表")
    public ApiResponse<List<StorageConfigVO>> list() {
        return ApiResponse.success(storageConfigService.list().stream().map(this::toVo).toList());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "更新存储配置")
    public ApiResponse<StorageConfigVO> update(@PathVariable Long id, @RequestBody StorageConfigRequest request) {
        KbStorageConfig updated = storageConfigService.update(id, toDto(request));
        return ApiResponse.success(toVo(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "删除存储配置")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        storageConfigService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "测试存储配置连接")
    public ApiResponse<Map<String, Object>> test(@PathVariable Long id) {
        boolean ok = storageConfigService.testConnection(id);
        return ApiResponse.success(Map.of("ok", ok));
    }

    @PostMapping("/{id}/default")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "设为默认存储配置")
    public ApiResponse<Void> setDefault(@PathVariable Long id) {
        storageConfigService.setDefault(id);
        return ApiResponse.success();
    }

    private StorageConfigService.StorageConfigUpdate toDto(StorageConfigRequest r) {
        return new StorageConfigService.StorageConfigUpdate(
                r.name(),
                r.provider(),
                r.bucket(),
                r.region(),
                r.endpoint(),
                r.accessKeyId(),
                r.secretKey(),
                r.cdnDomain(),
                r.isActive()
        );
    }

    private StorageConfigVO toVo(KbStorageConfig c) {
        boolean hasSecret = c.getSecretKeyEncrypted() != null && !c.getSecretKeyEncrypted().isBlank();
        return new StorageConfigVO(
                c.getId(),
                c.getName(),
                c.getProvider(),
                c.getBucket(),
                c.getRegion(),
                c.getEndpoint(),
                c.getAccessKeyId(),
                c.getCdnDomain(),
                c.getIsDefault(),
                c.getIsActive(),
                hasSecret,
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
