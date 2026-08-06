package com.nopkg.hellodoc.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.entities.SysConfig;
import com.nopkg.hellodoc.entities.SysDictDatum;
import com.nopkg.hellodoc.entities.SysDictType;
import com.nopkg.hellodoc.repositories.SysConfigRepository;
import com.nopkg.hellodoc.repositories.SysDictDataRepository;
import com.nopkg.hellodoc.repositories.SysDictTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.cache.type=none")
@AutoConfigureMockMvc
@Transactional
public class DictConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysDictTypeRepository dictTypeRepository;

    @Autowired
    private SysDictDataRepository dictDataRepository;

    @Autowired
    private SysConfigRepository configRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long testTypeId;
    private Long testDataId;
    private Long testConfigId;

    @BeforeEach
    void setUp() {
        // 准备字典数据
        SysDictType type = new SysDictType();
        type.setDictCode("test_status");
        type.setDictName("测试状态");
        type.setStatus((short) 0);
        type.setIsSystem(true);
        type.setCreateTime(Instant.now());
        type.setUpdateTime(Instant.now());
        type = dictTypeRepository.save(type);
        testTypeId = type.getId();

        SysDictDatum data = new SysDictDatum();
        data.setDictType(type);
        data.setDictCode("test_status");
        data.setLabel("正常");
        data.setValue("0");
        data.setValueType("string");
        data.setSortOrder(1);
        data.setIsDefault(true);
        data.setStatus((short) 0);
        data.setCreateTime(Instant.now());
        data.setUpdateTime(Instant.now());
        data = dictDataRepository.save(data);
        testDataId = data.getId();

        // 准备配置数据
        SysConfig config1 = new SysConfig();
        config1.setConfigKey("site.name");
        config1.setConfigName("站点名");
        config1.setConfigValue("HelloDoc");
        config1.setValueType("string");
        config1.setConfigGroup("site");
        config1.setIsFrontend(true);
        config1.setIsSystem(true);
        config1.setStatus((short) 0);
        config1.setCreateTime(Instant.now());
        config1.setUpdateTime(Instant.now());
        config1 = configRepository.save(config1);
        testConfigId = config1.getId();

        SysConfig config2 = new SysConfig();
        config2.setConfigKey("secret.key");
        config2.setConfigName("密钥");
        config2.setConfigValue("abcd");
        config2.setValueType("string");
        config2.setConfigGroup("security");
        config2.setIsFrontend(false);
        config2.setIsSystem(true);
        config2.setStatus((short) 0);
        config2.setCreateTime(Instant.now());
        config2.setUpdateTime(Instant.now());
        configRepository.save(config2);
    }

    // --- 公开接口测试 ---

    @Test
    @DisplayName("公开接口：获取字典项列表")
    void getPublicDict_shouldReturnData() throws Exception {
        mockMvc.perform(get("/api/public/dict/test_status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].label").value("正常"));
    }

    @Test
    @DisplayName("公开接口：获取前端配置（过滤非前端可见项）")
    void getFrontendConfigs_shouldFilterData() throws Exception {
        mockMvc.perform(get("/api/public/configs/frontend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data['site.name']").value("HelloDoc"))
                .andExpect(jsonPath("$.data['secret.key']").doesNotExist());
    }

    // --- 字典类型管理测试 ---

    @Test
    @DisplayName("管理端：字典类型列表查询")
    @WithMockUser(roles = "admin")
    void listDictTypes_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/system/dict/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("管理端：创建字典类型")
    @WithMockUser(roles = "admin")
    void createDictType_shouldSucceed() throws Exception {
        SysDictType newType = new SysDictType();
        newType.setDictCode("new_type");
        newType.setDictName("新类型");
        newType.setStatus((short) 0);

        mockMvc.perform(post("/api/system/dict/types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newType)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dictCode").value("new_type"));
    }

    @Test
    @DisplayName("管理端：更新字典类型")
    @WithMockUser(roles = "admin")
    void updateDictType_shouldSucceed() throws Exception {
        SysDictType update = new SysDictType();
        update.setDictCode("test_status"); // 必需用于 CacheEvict
        update.setDictName("修改后的名称");
        update.setStatus((short) 1);

        mockMvc.perform(put("/api/system/dict/types/" + testTypeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dictName").value("修改后的名称"));
    }

    @Test
    @DisplayName("管理端：删除字典类型（非系统内置）")
    @WithMockUser(roles = "admin")
    void deleteDictType_shouldSucceed() throws Exception {
        // 先创建一个非系统内置的
        SysDictType nonSysType = new SysDictType();
        nonSysType.setDictCode("non_sys");
        nonSysType.setDictName("非系统");
        nonSysType.setIsSystem(false);
        nonSysType = dictTypeRepository.save(nonSysType);

        mockMvc.perform(delete("/api/system/dict/types/" + nonSysType.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // --- 字典数据管理测试 ---

    @Test
    @DisplayName("管理端：创建字典数据")
    @WithMockUser(roles = "admin")
    void createDictData_shouldSucceed() throws Exception {
        // 构建匹配 DictDataController.CreateDictDataRequest 的请求体
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("dictTypeId", testTypeId);
        requestMap.put("label", "停用");
        requestMap.put("value", "1");
        requestMap.put("valueType", "string");
        requestMap.put("sortOrder", 2);
        requestMap.put("isDefault", false);
        requestMap.put("status", 0);

        mockMvc.perform(post("/api/system/dict/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestMap)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.value").value("1"));
    }

    @Test
    @DisplayName("管理端：更新字典数据")
    @WithMockUser(roles = "admin")
    void updateDictData_shouldSucceed() throws Exception {
        SysDictDatum update = new SysDictDatum();
        update.setDictCode("test_status"); // 必需用于 CacheEvict
        update.setLabel("更新后的标签");
        update.setValue("updated_val");

        mockMvc.perform(put("/api/system/dict/data/" + testDataId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.label").value("更新后的标签"));
    }

    @Test
    @DisplayName("管理端：删除字典数据")
    @WithMockUser(roles = "admin")
    void deleteDictData_shouldSucceed() throws Exception {
        mockMvc.perform(delete("/api/system/dict/data/" + testDataId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // --- 系统配置管理测试 ---

    @Test
    @DisplayName("管理端：配置列表查询")
    @WithMockUser(roles = "admin")
    void listConfigs_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/system/configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("管理端：更新配置")
    @WithMockUser(roles = "admin")
    void updateConfig_shouldSucceed() throws Exception {
        SysConfig update = new SysConfig();
        update.setConfigKey("site.name");
        update.setConfigValue("New HelloDoc");

        mockMvc.perform(put("/api/system/configs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.configValue").value("New HelloDoc"));
    }

    @Test
    @DisplayName("管理端：创建配置")
    @WithMockUser(roles = "admin")
    void createConfig_shouldSucceed() throws Exception {
        SysConfig newConfig = new SysConfig();
        newConfig.setConfigKey("brand.logo");
        newConfig.setConfigName("品牌Logo");
        newConfig.setConfigValue("/brand.png");

        mockMvc.perform(post("/api/system/configs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newConfig)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.configKey").value("brand.logo"));
    }
}
