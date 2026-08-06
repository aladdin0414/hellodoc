package com.nopkg.hellodoc.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.entities.SysUserAuth;
import com.nopkg.hellodoc.repositories.UserAuthRepository;
import com.nopkg.hellodoc.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthRepository userAuthRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        SysUser liyc = new SysUser();
        liyc.setNickname("liyc");
        liyc.setStatus((short) 0);
        liyc.setCreateTime(Instant.now());
        liyc.setUpdateTime(Instant.now());
        liyc = userRepository.save(liyc);

        SysUserAuth auth = new SysUserAuth();
        auth.setUser(liyc);
        auth.setIdentityType("PASSWORD");
        auth.setIdentifier("liyc_audit_test");
        auth.setCredential(passwordEncoder.encode("11111"));
        auth.setStatus((short) 0);
        auth.setVerified(true);
        auth.setCreateTime(Instant.now());
        userAuthRepository.save(auth);

        authHeader = "Bearer " + loginAndGetAccessToken("liyc_audit_test", "11111");
    }

    @Test
    void auditLogs_shouldBeRecordedAndQueryable() throws Exception {
        long kbId = createKb("Audit KB");
        updateKb(kbId, "Audit KB Updated");
        long docId = createDoc(kbId, "Audit Doc", "内容包含审计关键字");
        updateDoc(kbId, docId, "Audit Doc Updated", "更新内容");

        mockMvc.perform(get("/api/kb/{kbId}/audit-logs", kbId)
                .header("Authorization", authHeader)
                .param("pageNum", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.content[0].targetType").value("kb"))
                .andExpect(jsonPath("$.data.content[0].targetId").value((int) kbId));

        mockMvc.perform(get("/api/docs/{docId}/audit-logs", docId)
                .header("Authorization", authHeader)
                .param("pageNum", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.content[0].targetType").value("document"))
                .andExpect(jsonPath("$.data.content[0].targetId").value((int) docId));
    }

    private long createKb(String title) throws Exception {
        ObjectNode createKbRequest = objectMapper.createObjectNode();
        createKbRequest.put("title", title);
        createKbRequest.put("visibility", "PRIVATE");

        String kbResponse = mockMvc.perform(post("/api/kb/createKnowledgeBase")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createKbRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(kbResponse).path("data").path("id").asLong();
    }

    private void updateKb(long kbId, String newTitle) throws Exception {
        ObjectNode updateKbRequest = objectMapper.createObjectNode();
        updateKbRequest.put("title", newTitle);
        updateKbRequest.put("visibility", "PRIVATE");

        mockMvc.perform(put("/api/kb/{kbId}", kbId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateKbRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private long createDoc(long kbId, String name, String content) throws Exception {
        ObjectNode createDocRequest = objectMapper.createObjectNode();
        createDocRequest.put("name", name);
        createDocRequest.put("type", "FILE");
        createDocRequest.put("content", content);

        String docResponse = mockMvc.perform(post("/api/kb/{kbId}/documents", kbId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDocRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(docResponse).path("data").path("id").asLong();
    }

    private void updateDoc(long kbId, long docId, String name, String content) throws Exception {
        ObjectNode updateDocRequest = objectMapper.createObjectNode();
        updateDocRequest.put("name", name);
        updateDocRequest.put("content", content);

        mockMvc.perform(put("/api/kb/{kbId}/documents/{docId}", kbId, docId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDocRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String loginAndGetAccessToken(String username, String password) throws Exception {
        ObjectNode loginRequest = objectMapper.createObjectNode();
        loginRequest.put("username", username);
        loginRequest.put("password", password);

        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("accessToken").asText();
    }
}
