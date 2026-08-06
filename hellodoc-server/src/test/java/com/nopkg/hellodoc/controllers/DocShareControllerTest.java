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
public class DocShareControllerTest {

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
    private SysUser otherUser;
    private long kbId;
    private long docId;

    @BeforeEach
    void setUp() throws Exception {
        SysUser owner = createUser("doc_owner", "owner_pass");
        ownerAuthHeader = "Bearer " + loginAndGetAccessToken("doc_owner", "owner_pass");

        otherUser = createUser("doc_other", "other_pass");

        // Create KB
        ObjectNode createKbRequest = objectMapper.createObjectNode();
        createKbRequest.put("title", "Share test KB");
        String kbResponse = mockMvc.perform(post("/api/kb/createKnowledgeBase")
                .header("Authorization", ownerAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createKbRequest)))
                .andReturn().getResponse().getContentAsString();
        kbId = objectMapper.readTree(kbResponse).path("data").path("id").asLong();

        // Create Doc
        ObjectNode createDocRequest = objectMapper.createObjectNode();
        createDocRequest.put("name", "Share Test Doc");
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
    void docSharing_workflow() throws Exception {
        // 1. Initial Permissions List (Empty)
        mockMvc.perform(get("/api/docs/{docId}/share", docId)
                .header("Authorization", ownerAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // 2. Share to User as Editor
        ObjectNode shareRequest = objectMapper.createObjectNode();
        shareRequest.put("targetType", "USER");
        shareRequest.put("targetId", otherUser.getId());
        shareRequest.put("role", "editor");

        mockMvc.perform(post("/api/docs/{docId}/share", docId)
                .header("Authorization", ownerAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shareRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("editor"))
                .andExpect(jsonPath("$.data.targetName").value("doc_other"));

        // 3. Create Share Link
        ObjectNode linkRequest = objectMapper.createObjectNode();
        linkRequest.put("role", "viewer");

        mockMvc.perform(post("/api/docs/{docId}/share/link", docId)
                .header("Authorization", ownerAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(linkRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetType").value("link"))
                .andExpect(jsonPath("$.data.linkToken").isNotEmpty());

        // 4. List All Permissions (Should have 2)
        mockMvc.perform(get("/api/docs/{docId}/share", docId)
                .header("Authorization", ownerAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 5. Remove one permission
        String permissionsResponse = mockMvc.perform(get("/api/docs/{docId}/share", docId)
                .header("Authorization", ownerAuthHeader))
                .andReturn().getResponse().getContentAsString();
        long permId = objectMapper.readTree(permissionsResponse).path("data").get(0).path("id").asLong();

        mockMvc.perform(delete("/api/docs/{docId}/share/{permId}", docId, permId)
                .header("Authorization", ownerAuthHeader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/docs/{docId}/share", docId)
                .header("Authorization", ownerAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
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
