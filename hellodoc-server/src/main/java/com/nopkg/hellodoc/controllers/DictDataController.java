package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.SysDictDatum;
import com.nopkg.hellodoc.entities.SysDictType;
import com.nopkg.hellodoc.services.DictDataService;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.dict.DictDataVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统字典数据", description = "字典数据管理")
@RestController
@RequestMapping("/api/system/dict/data")
@RequiredArgsConstructor
public class DictDataController {

    private final DictDataService dictDataService;

    public record CreateDictDataRequest(
            Long dictTypeId,
            String label,
            String value,
            String valueType,
            String cssClass,
            String styleAttr,
            Integer sortOrder,
            Boolean isDefault,
            String labelI18n,
            String remark) {
    }

    @Operation(summary = "根据类型获取字典数据")
    @GetMapping("/type/{typeId}")
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<List<DictDataVO>> listByTypeId(@PathVariable Long typeId) {
        return ApiResponse.success(dictDataService.listLocalizedDictDataByType(typeId));
    }

    @Operation(summary = "创建字典数据")
    @PostMapping
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<SysDictDatum> create(@RequestBody CreateDictDataRequest request) {
        SysDictDatum dictDatum = new SysDictDatum();
        SysDictType dictType = new SysDictType();
        dictType.setId(request.dictTypeId());
        dictDatum.setDictType(dictType);

        dictDatum.setLabel(request.label());
        dictDatum.setValue(request.value());
        dictDatum.setValueType(request.valueType());
        dictDatum.setCssClass(request.cssClass());
        dictDatum.setStyleAttr(request.styleAttr());
        dictDatum.setSortOrder(request.sortOrder());
        dictDatum.setIsDefault(request.isDefault());
        dictDatum.setLabelI18n(request.labelI18n());
        dictDatum.setRemark(request.remark());
        dictDatum.setStatus((short) 0);

        return ApiResponse.success(dictDataService.createDictData(dictDatum));
    }

    @Operation(summary = "更新字典数据")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<SysDictDatum> update(@PathVariable Long id, @RequestBody SysDictDatum dictDatum) {
        return ApiResponse.success(dictDataService.updateDictData(id, dictDatum));
    }

    @Operation(summary = "删除字典数据")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dictDataService.deleteDictData(id);
        return ApiResponse.success(null);
    }
}
