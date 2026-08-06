package com.nopkg.hellodoc.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.entities.SysUserAuth;
import com.nopkg.hellodoc.enums.KbRole;
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
public class KbMemberControllerTest {

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
    private String memberAuthHeader;
    private SysUser memberUser;

    @BeforeEach
    void setUp() throws Exception {
        // Create Owner
        SysUser owner = createUser("kb_owner", "owner_pass");
        ownerAuthHeader = "Bearer " + loginAndGetAccessToken("kb_owner", "owner_pass");

        // Create Potential Member
        memberUser = createUser("kb_member_user", "member_pass");
        memberAuthHeader = "Bearer " + loginAndGetAccessToken("kb_member_user", "member_pass");
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
    void memberManagement_workflow() throws Exception {
        // 1. Create KB as Owner
        ObjectNode createKbRequest = objectMapper.createObjectNode();
        createKbRequest.put("title", "Member Test KB");
        createKbRequest.put("visibility", "PRIVATE");

        String kbResponse = mockMvc.perform(post("/api/kb/createKnowledgeBase")
                .header("Authorization", ownerAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createKbRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long kbId = objectMapper.readTree(kbResponse).path("data").path("id").asLong();

        // 2. List Members (Should have owner only)
        mockMvc.perform(get("/api/kb/{kbId}/members", kbId)
                .header("Authorization", ownerAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].role").value("owner"));

        // 3. Add Member (Owner adds Viewer)
        ObjectNode addMemberRequest = objectMapper.createObjectNode();
        addMemberRequest.put("username", "kb_member_user");
        addMemberRequest.put("role", "viewer");

        mockMvc.perform(post("/api/kb/{kbId}/members", kbId)
                .header("Authorization", ownerAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addMemberRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("viewer"));

        // 4. Member List (Should have 2 members)
        mockMvc.perform(get("/api/kb/{kbId}/members", kbId)
                .header("Authorization", ownerAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 5. Member Tries to add someone else (Should fail)
        mockMvc.perform(post("/api/kb/{kbId}/members", kbId)
                .header("Authorization", memberAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addMemberRequest)))
                .andExpect(status().isForbidden());

        // 6. Update Member Role (Owner promotes to Admin)
        ObjectNode updateMemberRequest = objectMapper.createObjectNode();
        updateMemberRequest.put("role", "admin");

        mockMvc.perform(put("/api/kb/{kbId}/members/{userId}", kbId, memberUser.getId())
                .header("Authorization", ownerAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateMemberRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("admin"));

        // 7. Remove Member (Admin/Owner removes)
        mockMvc.perform(delete("/api/kb/{kbId}/members/{userId}", kbId, memberUser.getId())
                .header("Authorization", ownerAuthHeader))
                .andExpect(status().isOk());

        // 8. Final Check
        mockMvc.perform(get("/api/kb/{kbId}/members", kbId)
                .header("Authorization", ownerAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void memberLeave_workflow() throws Exception {
        // 1. Create KB as Owner
        ObjectNode createKbRequest = objectMapper.createObjectNode();
        createKbRequest.put("title", "Leave Test KB");
        createKbRequest.put("visibility", "PRIVATE");

        String kbResponse = mockMvc.perform(post("/api/kb/createKnowledgeBase")
                .header("Authorization", ownerAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createKbRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long kbId = objectMapper.readTree(kbResponse).path("data").path("id").asLong();

        // 2. Add Member
        ObjectNode addMemberRequest = objectMapper.createObjectNode();
        addMemberRequest.put("username", "kb_member_user");
        addMemberRequest.put("role", "viewer");

        mockMvc.perform(post("/api/kb/{kbId}/members", kbId)
                .header("Authorization", ownerAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addMemberRequest)))
                .andExpect(status().isOk());

        // 3. Member leaves KB
        mockMvc.perform(post("/api/kb/{kbId}/members/leave", kbId)
                .header("Authorization", memberAuthHeader))
                .andExpect(status().isOk());

        // 4. Check members length is back to 1
        mockMvc.perform(get("/api/kb/{kbId}/members", kbId)
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
