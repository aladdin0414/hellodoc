package com.nopkg.hellodoc.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文件接口测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Test
    @DisplayName("获取头像应成功")
    void getAvatar_shouldSucceed() throws Exception {
        // 准备测试文件
        Path avatarDir = Paths.get(uploadDir, "avatars");
        if (!Files.exists(avatarDir)) {
            Files.createDirectories(avatarDir);
        }
        String filename = "test-avatar.png";
        Path testFile = avatarDir.resolve(filename);
        byte[] content = "test image content".getBytes();
        Files.write(testFile, content);

        try {
            mockMvc.perform(get("/api/files/avatars/" + filename))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.IMAGE_PNG))
                    .andExpect(content().bytes(content));
        } finally {
            // 清理测试文件
            Files.deleteIfExists(testFile);
        }
    }

    @Test
    @DisplayName("获取不存在的头像应返回 404")
    void getAvatar_whenNotExists_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/files/avatars/non-existent.png"))
                .andExpect(status().isNotFound());
    }
}
