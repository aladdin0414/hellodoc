package com.nopkg.hellodoc.security;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class PermissionEnforcementTest {

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
        private String userAuthHeader;
        private SysUser testUser;
        private long kbId;
        private long docId;

        @BeforeEach
        void setUp() throws Exception {
                // Create Owner
                createUser("enforce_owner", "pass");
                ownerAuthHeader = "Bearer " + loginAndGetAccessToken("enforce_owner", "pass");

                // Create Test User
                testUser = createUser("test_user", "pass");
                userAuthHeader = "Bearer " + loginAndGetAccessToken("test_user", "pass");

                // Create KB
                ObjectNode createKbRequest = objectMapper.createObjectNode();
                createKbRequest.put("title", "Enforce KB");
                String kbResponse = mockMvc.perform(post("/api/kb/createKnowledgeBase")
                                .header("Authorization", ownerAuthHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createKbRequest)))
                                .andReturn().getResponse().getContentAsString();
                kbId = objectMapper.readTree(kbResponse).path("data").path("id").asLong();

                // Create Doc
                ObjectNode createDocRequest = objectMapper.createObjectNode();
                createDocRequest.put("name", "Enforce Doc");
                createDocRequest.put("type", "FILE");
                String docResponse = mockMvc.perform(post("/api/kb/{kbId}/documents", kbId)
                                .header("Authorization", ownerAuthHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createDocRequest)))
                                .andReturn().getResponse().getContentAsString();
                docId = objectMapper.readTree(docResponse).path("data").path("id").asLong();
        }

        private SysUser createUser(String username, String password) {
                SysUser user = new SysUser();
                user.setNickname(username);
                user.setStatus((short) 0);
                user.setCreateTime(Instant.now());
                user.setUpdateTime(Instant.now());
                user = userRepository.save(user);

                SysUserAuth auth = new SysUserAuth();
                auth.setUser(user);
                auth.setIdentityType("PASSWORD");
                auth.setIdentifier(username);
                auth.setCredential(passwordEncoder.encode(password));
                auth.setStatus((short) 0);
                auth.setVerified(true);
                auth.setCreateTime(Instant.now());
                userAuthRepository.save(auth);
                return user;
        }

        @Test
        void testPermissionPrecedence_EditorOnKB_ViewerOnDoc() throws Exception {
                // 1. Add user as EDITOR to KB
                ObjectNode addMemberRequest = objectMapper.createObjectNode();
                addMemberRequest.put("username", "test_user");
                addMemberRequest.put("role", "editor");
                mockMvc.perform(post("/api/kb/{kbId}/members", kbId)
                                .header("Authorization", ownerAuthHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(addMemberRequest)))
                                .andExpect(status().isOk());

                // 2. Add user as VIEWER to Doc (Override)
                ObjectNode shareRequest = objectMapper.createObjectNode();
                shareRequest.put("targetType", "USER");
                shareRequest.put("targetId", testUser.getId());
                shareRequest.put("role", "viewer");
                mockMvc.perform(post("/api/docs/{docId}/share", docId)
                                .header("Authorization", ownerAuthHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(shareRequest)))
                                .andExpect(status().isOk());

                // 3. User tries to edit doc (Should fail despite KB EDITOR role)
                ObjectNode updateDocRequest = objectMapper.createObjectNode();
                updateDocRequest.put("name", "Illegal Update");
                mockMvc.perform(put("/api/kb/{kbId}/documents/{docId}", kbId, docId)
                                .header("Authorization", userAuthHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateDocRequest)))
                                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                                .andExpect(status().isForbidden());

                // 4. User tries to view doc (Should succeed)
                mockMvc.perform(get("/api/kb/{kbId}/documents", kbId)
                                .header("Authorization", userAuthHeader))
                                .andExpect(status().isOk());
        }

        @Test
        void testPermissionPrecedence_ViewerOnKB_EditorOnDoc() throws Exception {
                // 1. Add user as VIEWER to KB
                ObjectNode addMemberRequest = objectMapper.createObjectNode();
                addMemberRequest.put("username", "test_user");
                addMemberRequest.put("role", "viewer");
                mockMvc.perform(post("/api/kb/{kbId}/members", kbId)
                                .header("Authorization", ownerAuthHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(addMemberRequest)))
                                .andExpect(status().isOk());

                // 2. Add user as EDITOR to Doc (Upgrade)
                ObjectNode shareRequest = objectMapper.createObjectNode();
                shareRequest.put("targetType", "USER");
                shareRequest.put("targetId", testUser.getId());
                shareRequest.put("role", "editor");
                mockMvc.perform(post("/api/docs/{docId}/share", docId)
                                .header("Authorization", ownerAuthHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(shareRequest)))
                                .andExpect(status().isOk());

                // 3. User tries to edit doc (Should succeed due to Doc EDITOR role)
                ObjectNode updateDocRequest = objectMapper.createObjectNode();
                updateDocRequest.put("name", "Legal Update");
                mockMvc.perform(put("/api/kb/{kbId}/documents/{docId}", kbId, docId)
                                .header("Authorization", userAuthHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateDocRequest)))
                                .andExpect(status().isOk());
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
