package com.nopkg.hellodoc.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nopkg.hellodoc.repositories.KbStorageConfigRepository;
import org.junit.jupiter.api.AfterEach;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.cache.type=none",
        "spring.sql.init.mode=never",
        "file.upload-dir=build/test-uploads-storage-config"
})
@AutoConfigureMockMvc
@Transactional
public class StorageConfigControllerTest {

    private static final Path UPLOAD_DIR = Paths.get("build", "test-uploads-storage-config");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KbStorageConfigRepository storageConfigRepository;

    @BeforeEach
    void setUp() {
        storageConfigRepository.deleteAll();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (Files.exists(UPLOAD_DIR)) {
            try (var s = Files.walk(UPLOAD_DIR)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    @Test
    @DisplayName("存储配置：权限校验")
    @WithMockUser(roles = "user")
    void storageConfig_permission_shouldEnforceSuperAdmin() throws Exception {
        ObjectNode create = objectMapper.createObjectNode();
        create.put("name", "local-1");
        create.put("provider", "local");

        mockMvc.perform(post("/api/system/storage/configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("存储配置：本地存储 CRUD + 默认切换 + 测试连接")
    @WithMockUser(roles = "admin")
    void storageConfig_local_workflow() throws Exception {
        long id1 = createConfig("local-1", "local", null);
        long id2 = createConfig("local-2", "local", null);

        mockMvc.perform(get("/api/system/storage/configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(post("/api/system/storage/configs/{id}/test", id1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ok").value(true));

        mockMvc.perform(post("/api/system/storage/configs/{id}/default", id2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ObjectNode disableDefault = objectMapper.createObjectNode();
        disableDefault.put("isActive", false);
        mockMvc.perform(put("/api/system/storage/configs/{id}", id2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disableDefault)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1007));

        mockMvc.perform(delete("/api/system/storage/configs/{id}", id2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1007));

        mockMvc.perform(delete("/api/system/storage/configs/{id}", id1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("存储配置：名称冲突")
    @WithMockUser(roles = "admin")
    void storageConfig_nameConflict_shouldReturnCode1003() throws Exception {
        createConfig("dup", "local", null);

        ObjectNode create = objectMapper.createObjectNode();
        create.put("name", "dup");
        create.put("provider", "local");

        mockMvc.perform(post("/api/system/storage/configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("存储配置：S3 测试连接失败返回 ok=false")
    @WithMockUser(roles = "admin")
    void storageConfig_s3_testConnection_shouldReturnFalse() throws Exception {
        ObjectNode create = objectMapper.createObjectNode();
        create.put("name", "s3-1");
        create.put("provider", "s3");
        create.put("bucket", "test-bucket");
        create.put("region", "us-east-1");
        create.put("endpoint", "http://localhost:9");
        create.put("accessKeyId", "ak");
        create.put("secretKey", "sk");

        String resp = mockMvc.perform(post("/api/system/storage/configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(resp).path("data").path("id").asLong();

        mockMvc.perform(post("/api/system/storage/configs/{id}/test", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ok").value(false));
    }

    private long createConfig(String name, String provider, String secretKey) throws Exception {
        ObjectNode create = objectMapper.createObjectNode();
        create.put("name", name);
        create.put("provider", provider);
        if (secretKey != null) {
            create.put("secretKey", secretKey);
        }

        String resp = mockMvc.perform(post("/api/system/storage/configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(resp);
        return root.path("data").path("id").asLong();
    }
}
