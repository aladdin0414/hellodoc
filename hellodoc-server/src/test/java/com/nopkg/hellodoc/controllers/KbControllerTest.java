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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class KbControllerTest {

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

        private Long liycUserId;
        private Long bobUserId;

        @BeforeEach
        void setUpUsers() {
                SysUser liyc = new SysUser();
                liyc.setNickname("liyc");
                liyc.setStatus((short) 0);
                liyc.setCreateTime(Instant.now());
                liyc.setUpdateTime(Instant.now());
                liyc = userRepository.save(liyc);

                SysUserAuth liycAuth = new SysUserAuth();
                liycAuth.setUser(liyc);
                liycAuth.setIdentityType("PASSWORD");
                liycAuth.setIdentifier("liyc");
                liycAuth.setCredential(passwordEncoder.encode("11111"));
                liycAuth.setStatus((short) 0);
                liycAuth.setVerified(true);
                liycAuth.setCreateTime(Instant.now());
                userAuthRepository.save(liycAuth);

                SysUser bob = new SysUser();
                bob.setNickname("bob");
                bob.setStatus((short) 0);
                bob.setCreateTime(Instant.now());
                bob.setUpdateTime(Instant.now());
                bob = userRepository.save(bob);

                SysUserAuth bobAuth = new SysUserAuth();
                bobAuth.setUser(bob);
                bobAuth.setIdentityType("PASSWORD");
                bobAuth.setIdentifier("bob");
                bobAuth.setCredential(passwordEncoder.encode("11111"));
                bobAuth.setStatus((short) 0);
                bobAuth.setVerified(true);
                bobAuth.setCreateTime(Instant.now());
                userAuthRepository.save(bobAuth);

                this.liycUserId = liyc.getId();
                this.bobUserId = bob.getId();
        }

        @Test
        void kbController_allEndpoints_flow() throws Exception {
                String accessToken = loginAndGetAccessToken("liyc", "11111");
                String authHeader = "Bearer " + accessToken;

                mockMvc.perform(get("/api/kb/listKnowledgeBases")
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0))
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.data.length()").value(0));

                ObjectNode createKbRequest = objectMapper.createObjectNode();
                createKbRequest.put("title", "测试知识库");
                createKbRequest.put("description", "用于接口测试");
                createKbRequest.put("color", "blue");
                createKbRequest.put("icon", "book");
                createKbRequest.put("allowAnonymous", false);
                createKbRequest.put("visibility", "PRIVATE");

                String createKbResponse = mockMvc.perform(post("/api/kb/createKnowledgeBase")
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createKbRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0))
                                .andExpect(jsonPath("$.data.id").isNumber())
                                .andExpect(jsonPath("$.data.title").value("测试知识库"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                long kbId = objectMapper.readTree(createKbResponse).path("data").path("id").asLong();

                mockMvc.perform(get("/api/kb/listKnowledgeBases")
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0))
                                .andExpect(jsonPath("$.data.length()").value(1))
                                .andExpect(jsonPath("$.data[0].id").value(kbId));

                ObjectNode updateKbRequest = objectMapper.createObjectNode();
                updateKbRequest.put("title", "测试知识库-已更新");
                updateKbRequest.put("description", "更新描述");
                updateKbRequest.put("color", "red");
                updateKbRequest.put("icon", "updated");
                updateKbRequest.put("allowAnonymous", true);
                updateKbRequest.put("visibility", "PUBLIC");

                mockMvc.perform(put("/api/kb/{kbId}", kbId)
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateKbRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0))
                                .andExpect(jsonPath("$.data.id").value(kbId))
                                .andExpect(jsonPath("$.data.title").value("测试知识库-已更新"))
                                .andExpect(jsonPath("$.data.allowAnonymous").value(true))
                                .andExpect(jsonPath("$.data.visibility").value("public"));

                ObjectNode pinRequest = objectMapper.createObjectNode();
                pinRequest.put("pinned", true);

                mockMvc.perform(post("/api/kb/{kbId}/pin", kbId)
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(pinRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0))
                                .andExpect(jsonPath("$.data").value(true));

                mockMvc.perform(get("/api/kb/listKnowledgeBases")
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0))
                                .andExpect(jsonPath("$.data.length()").value(1))
                                .andExpect(jsonPath("$.data[0].id").value(kbId))
                                .andExpect(jsonPath("$.data[0].isPinned").value(true));

                mockMvc.perform(get("/api/kb/{kbId}/members", kbId)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0))
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.data.length()").value(1))
                                .andExpect(jsonPath("$.data[0].userId").value(liycUserId))
                                .andExpect(jsonPath("$.data[0].role").value("owner"));

                ObjectNode addMemberRequest = objectMapper.createObjectNode();
                addMemberRequest.put("username", "bob");
                addMemberRequest.put("role", "editor");

                String addMemberResponse = mockMvc.perform(post("/api/kb/{kbId}/members", kbId)
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(addMemberRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0))
                                .andExpect(jsonPath("$.data.id").isNumber())
                                .andExpect(jsonPath("$.data.userId").value(bobUserId))
                                .andExpect(jsonPath("$.data.role").value("editor"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                long memberId = objectMapper.readTree(addMemberResponse).path("data").path("id").asLong();

                mockMvc.perform(get("/api/kb/{kbId}/members", kbId)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0))
                                .andExpect(jsonPath("$.data.length()").value(2));

                ObjectNode updateMemberRequest = objectMapper.createObjectNode();
                updateMemberRequest.put("role", "admin");

                mockMvc.perform(put("/api/kb/{kbId}/members/{userId}", kbId, bobUserId)
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateMemberRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0))
                                .andExpect(jsonPath("$.data.id").value(memberId))
                                .andExpect(jsonPath("$.data.userId").value(bobUserId))
                                .andExpect(jsonPath("$.data.role").value("admin"));

                mockMvc.perform(delete("/api/kb/{kbId}/members/{userId}", kbId, bobUserId)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0));

                mockMvc.perform(get("/api/kb/{kbId}/members", kbId)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0))
                                .andExpect(jsonPath("$.data.length()").value(1));

                mockMvc.perform(get("/api/kb/{kbId}/members", kbId)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0))
                                .andExpect(jsonPath("$.data.length()").value(1));

                // Test Reorder
                ObjectNode reorderRequest = objectMapper.createObjectNode();
                reorderRequest.putArray("kbIds").add(kbId);
                mockMvc.perform(post("/api/kb/reorder")
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reorderRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0));

                // Test Soft Delete
                mockMvc.perform(delete("/api/kb/{kbId}", kbId)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0));

                mockMvc.perform(get("/api/kb/trash")
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0))
                                .andExpect(jsonPath("$.data.length()").value(1))
                                .andExpect(jsonPath("$.data[0].id").value(kbId));

                mockMvc.perform(get("/api/kb/listKnowledgeBases")
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(0))
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
                                .andExpect(jsonPath("$.code").value(0))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                JsonNode root = objectMapper.readTree(response);
                return root.path("data").path("accessToken").asText();
        }
}
