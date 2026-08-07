package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.SysDictType;
import com.nopkg.hellodoc.services.DictTypeService;
import com.nopkg.hellodoc.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "System Dictionary Types", description = "Dictionary Type Management APIs")
@RestController
@RequestMapping("/api/system/dict/types")
@RequiredArgsConstructor
public class DictTypeController {

    private final DictTypeService dictTypeService;

    @Operation(summary = "List dictionary types")
    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<List<SysDictType>> list() {
        return ApiResponse.success(dictTypeService.listDictTypes());
    }

    @Operation(summary = "Get dictionary type details")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<SysDictType> get(@PathVariable Long id) {
        return ApiResponse.success(dictTypeService.getDictType(id));
    }

    public record CreateDictTypeRequest(
            String dictCode,
            String dictName,
            String description,
            Boolean isSystem) {
    }

    @Operation(summary = "Create dictionary type")
    @PostMapping
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<SysDictType> create(@RequestBody CreateDictTypeRequest request) {
        SysDictType dictType = new SysDictType();
        dictType.setDictCode(request.dictCode());
        dictType.setDictName(request.dictName());
        dictType.setDescription(request.description());
        dictType.setIsSystem(request.isSystem());
        return ApiResponse.success(dictTypeService.createDictType(dictType));
    }

    @Operation(summary = "Update dictionary type")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<SysDictType> update(@PathVariable Long id, @RequestBody SysDictType dictType) {
        return ApiResponse.success(dictTypeService.updateDictType(id, dictType));
    }

    @Operation(summary = "Delete dictionary type")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dictTypeService.deleteDictType(id);
        return ApiResponse.success(null);
    }
}
