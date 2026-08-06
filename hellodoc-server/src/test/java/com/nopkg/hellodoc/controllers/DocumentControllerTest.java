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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class DocumentControllerTest {

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

                SysUserAuth liycAuth = new SysUserAuth();
                liycAuth.setUser(liyc);
                liycAuth.setIdentityType("PASSWORD");
                liycAuth.setIdentifier("liyc_doc_test");
                liycAuth.setCredential(passwordEncoder.encode("11111"));
                liycAuth.setStatus((short) 0);
                liycAuth.setVerified(true);
                liycAuth.setCreateTime(Instant.now());
                userAuthRepository.save(liycAuth);

                authHeader = "Bearer " + loginAndGetAccessToken("liyc_doc_test", "11111");
        }

        @Test
        void documentController_workflow() throws Exception {
                // 1. Create KB
                ObjectNode createKbRequest = objectMapper.createObjectNode();
                createKbRequest.put("title", "Doc Test KB");
                createKbRequest.put("visibility", "PRIVATE");

                String kbResponse = mockMvc.perform(post("/api/kb/createKnowledgeBase")
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createKbRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
                long kbId = objectMapper.readTree(kbResponse).path("data").path("id").asLong();

                // 2. Create Document
                ObjectNode createDocRequest = objectMapper.createObjectNode();
                createDocRequest.put("name", "Test Document");
                createDocRequest.put("type", "FILE");
                createDocRequest.put("content", "Initial content");

                String docResponse = mockMvc.perform(post("/api/kb/{kbId}/documents", kbId)
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createDocRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.name").value("Test Document"))
                                .andReturn().getResponse().getContentAsString();
                long docId = objectMapper.readTree(docResponse).path("data").path("id").asLong();

                // 3. Update Document
                ObjectNode updateDocRequest = objectMapper.createObjectNode();
                updateDocRequest.put("name", "Updated Document");
                updateDocRequest.put("content", "Updated content");

                mockMvc.perform(put("/api/kb/{kbId}/documents/{docId}", kbId, docId)
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateDocRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.name").value("Updated Document"));

                // 4. Create Revision
                ObjectNode createRevRequest = objectMapper.createObjectNode();
                createRevRequest.put("content", "Revision content");
                createRevRequest.put("message", "First revision");

                mockMvc.perform(post("/api/kb/{kbId}/documents/{docId}/revisions", kbId, docId)
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRevRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.version").value(3));

                // 5. List Revisions
                mockMvc.perform(get("/api/kb/{kbId}/documents/{docId}/revisions", kbId, docId)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.length()").value(3));

                // 6. List Documents Tree
                mockMvc.perform(get("/api/kb/{kbId}/documents", kbId)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.length()").value(1));

                // 7. Delete Document
                mockMvc.perform(delete("/api/kb/{kbId}/documents/{docId}", kbId, docId)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/kb/{kbId}/documents", kbId)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.length()").value(0));
        }

        private String loginAndGetAccessToken(String username, String password) throws Exception {
                ObjectNode loginRequest = objectMapper.createObjectNode();
                loginRequest.put("username", username);
                loginRequest.put("password", password);

                String response = mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                JsonNode root = objectMapper.readTree(response);
                return root.path("data").path("accessToken").asText();
        }
}
