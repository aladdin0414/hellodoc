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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class SearchControllerTest {

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

    private String ownerAuthHeader;
    private String otherAuthHeader;

    @BeforeEach
    void setUp() throws Exception {
        SysUser owner = new SysUser();
        owner.setNickname("liyc");
        owner.setStatus((short) 0);
        owner.setCreateTime(Instant.now());
        owner.setUpdateTime(Instant.now());
        owner = userRepository.save(owner);

        SysUserAuth ownerAuth = new SysUserAuth();
        ownerAuth.setUser(owner);
        ownerAuth.setIdentityType("PASSWORD");
        ownerAuth.setIdentifier("liyc_search_owner");
        ownerAuth.setCredential(passwordEncoder.encode("11111"));
        ownerAuth.setStatus((short) 0);
        ownerAuth.setVerified(true);
        ownerAuth.setCreateTime(Instant.now());
        userAuthRepository.save(ownerAuth);

        SysUser other = new SysUser();
        other.setNickname("bob");
        other.setStatus((short) 0);
        other.setCreateTime(Instant.now());
        other.setUpdateTime(Instant.now());
        other = userRepository.save(other);

        SysUserAuth otherAuth = new SysUserAuth();
        otherAuth.setUser(other);
        otherAuth.setIdentityType("PASSWORD");
        otherAuth.setIdentifier("liyc_search_other");
        otherAuth.setCredential(passwordEncoder.encode("11111"));
        otherAuth.setStatus((short) 0);
        otherAuth.setVerified(true);
        otherAuth.setCreateTime(Instant.now());
        userAuthRepository.save(otherAuth);

        ownerAuthHeader = "Bearer " + loginAndGetAccessToken("liyc_search_owner", "11111");
        otherAuthHeader = "Bearer " + loginAndGetAccessToken("liyc_search_other", "11111");
    }

    @Test
    void search_shouldReturnResultsAndRespectPermissions() throws Exception {
        long kbId = createKb(ownerAuthHeader, "Search KB");
        long docId = createDoc(ownerAuthHeader, kbId, "Hello 搜索文档", "这里包含关键字：HelloDocSearch");
        publishDoc(ownerAuthHeader, kbId, docId);

        mockMvc.perform(get("/api/kb/{kbId}/search", kbId)
                        .header("Authorization", ownerAuthHeader)
                        .param("q", "HelloDocSearch")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].docId").value(docId))
                .andExpect(jsonPath("$.data[0].highlightedSnippet").value(org.hamcrest.Matchers.containsString("<mark>HelloDocSearch</mark>")));

        mockMvc.perform(get("/api/search")
                        .header("Authorization", otherAuthHeader)
                        .param("q", "HelloDocSearch")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private long createKb(String authHeader, String title) throws Exception {
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

    private long createDoc(String authHeader, long kbId, String name, String content) throws Exception {
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

    private void publishDoc(String authHeader, long kbId, long docId) throws Exception {
        ObjectNode updateRequest = objectMapper.createObjectNode();
        updateRequest.put("status", "PUBLISHED");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/kb/{kbId}/documents/{docId}", kbId, docId)
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
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
