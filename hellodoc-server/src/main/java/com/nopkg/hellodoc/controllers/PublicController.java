package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.services.ConfigService;
import com.nopkg.hellodoc.services.DictDataService;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.dict.DictDataVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Public APIs", description = "Public access to dictionaries and configurations without authentication")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final DictDataService dictDataService;
    private final ConfigService configService;

    @Operation(summary = "Get dictionary items")
    @GetMapping("/dict/{dictCode}")
    public ApiResponse<List<DictDataVO>> getDict(@PathVariable String dictCode) {
        return ApiResponse.success(dictDataService.getLocalizedDictDataByCode(dictCode));
    }

    @Operation(summary = "Get frontend configurations")
    @GetMapping("/configs/frontend")
    public ApiResponse<Map<String, String>> getFrontendConfigs() {
        Map<String, String> map = new HashMap<>(configService.getFrontendConfigs());
        // 从动态配置中获取协作功能状态，默认为 false
        Boolean collabEnabled = configService.getConfigValue("app.collab.enabled", Boolean.class);
        map.put("collab.enabled", String.valueOf(collabEnabled != null && collabEnabled));
        return ApiResponse.success(map);
    }
}
