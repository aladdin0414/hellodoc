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

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DocRelationControllerTest {

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

    @BeforeEach
    void setUp() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        SysUser owner = new SysUser();
        owner.setNickname("liyc");
        owner.setStatus((short) 0);
        owner.setCreateTime(Instant.now());
        owner.setUpdateTime(Instant.now());
        owner = userRepository.save(owner);

        SysUserAuth ownerAuth = new SysUserAuth();
        ownerAuth.setUser(owner);
        ownerAuth.setIdentityType("PASSWORD");
        ownerAuth.setIdentifier("liyc_relation_owner_" + suffix);
        ownerAuth.setCredential(passwordEncoder.encode("11111"));
        ownerAuth.setStatus((short) 0);
        ownerAuth.setVerified(true);
        ownerAuth.setCreateTime(Instant.now());
        userAuthRepository.save(ownerAuth);

        ownerAuthHeader = "Bearer " + loginAndGetAccessToken("liyc_relation_owner_" + suffix, "11111");
    }

    @Test
    void docSave_shouldSyncLinksAndBacklinks() throws Exception {
        long kbId = createKb(ownerAuthHeader, "Relation KB");
        long docA = createDoc(ownerAuthHeader, kbId, "文档A", "");
        long docB = createDoc(ownerAuthHeader, kbId, "文档B", "");

        updateDocContent(ownerAuthHeader, kbId, docA, "参考[[文档B]]以及![[文档B]]");

        boolean ok = false;
        for (int i = 0; i < 40; i++) {
            String linksJson = mockMvc.perform(get("/api/docs/{docId}/links", docA)
                            .header("Authorization", ownerAuthHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn().getResponse().getContentAsString();
            JsonNode root = objectMapper.readTree(linksJson);
            JsonNode data = root.path("data");
            if (data.isArray() && data.size() >= 1) {
                ok = true;
                break;
            }
            Thread.sleep(50);
        }
        if (!ok) {
            throw new AssertionError("relation sync timeout");
        }

        mockMvc.perform(get("/api/docs/{docId}/links", docA)
                        .header("Authorization", ownerAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(docB));

        mockMvc.perform(get("/api/docs/{docId}/backlinks", docB)
                        .header("Authorization", ownerAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(docA));

        mockMvc.perform(get("/api/docs/{docId}/graph", docA)
                        .header("Authorization", ownerAuthHeader)
                        .param("depth", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.center.id").value(docA))
                .andExpect(jsonPath("$.data.nodes.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.edges.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
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

    private void updateDocContent(String authHeader, long kbId, long docId, String content) throws Exception {
        ObjectNode update = objectMapper.createObjectNode();
        update.put("content", content);
        mockMvc.perform(put("/api/kb/{kbId}/documents/{docId}", kbId, docId)
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
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
