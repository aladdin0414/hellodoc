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
public class CommentControllerTest {

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
    private long kbId;
    private long docId;

    @BeforeEach
    void setUp() throws Exception {
        SysUser user = new SysUser();
        user.setNickname("comment_tester");
        user.setStatus((short) 0);
        user.setCreateTime(Instant.now());
        user.setUpdateTime(Instant.now());
        user = userRepository.save(user);

        SysUserAuth userAuth = new SysUserAuth();
        userAuth.setUser(user);
        userAuth.setIdentityType("PASSWORD");
        userAuth.setIdentifier("comment_tester");
        userAuth.setCredential(passwordEncoder.encode("123456"));
        userAuth.setStatus((short) 0);
        userAuth.setVerified(true);
        userAuth.setCreateTime(Instant.now());
        userAuthRepository.save(userAuth);

        authHeader = "Bearer " + loginAndGetAccessToken("comment_tester", "123456");

        // Create KB
        ObjectNode createKbRequest = objectMapper.createObjectNode();
        createKbRequest.put("title", "Comment Test KB");
        createKbRequest.put("visibility", "PRIVATE"); // Should default to PRIVATE?

        String kbResponse = mockMvc.perform(post("/api/kb/createKnowledgeBase")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createKbRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        kbId = objectMapper.readTree(kbResponse).path("data").path("id").asLong();

        // Create Document
        ObjectNode createDocRequest = objectMapper.createObjectNode();
        createDocRequest.put("name", "Comment Test Doc");
        createDocRequest.put("type", "FILE");
        createDocRequest.put("content", "Test Content");

        String docResponse = mockMvc.perform(post("/api/kb/{kbId}/documents", kbId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDocRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        docId = objectMapper.readTree(docResponse).path("data").path("id").asLong();
    }

    @Test
    void testCommentController_workflow() throws Exception {
        // 1. Add Comment
        ObjectNode addCommentRequest = objectMapper.createObjectNode();
        addCommentRequest.put("content", "This is a test comment");
        addCommentRequest.put("anchorType", "BLOCK");

        String commentResponse = mockMvc.perform(post("/api/docs/{docId}/comments", docId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addCommentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("This is a test comment"))
                .andReturn().getResponse().getContentAsString();
        long commentId = objectMapper.readTree(commentResponse).path("data").path("id").asLong();

        // 2. Reply to Comment
        ObjectNode replyRequest = objectMapper.createObjectNode();
        replyRequest.put("content", "This is a reply");

        mockMvc.perform(post("/api/comments/{id}/reply", commentId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(replyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("This is a reply"))
                .andExpect(jsonPath("$.data.parentId").value(commentId));

        // 3. Resolve Comment
        mockMvc.perform(post("/api/comments/{id}/resolve", commentId)
                .header("Authorization", authHeader))
                .andExpect(status().isOk());

        // 4. Update Comment
        ObjectNode updateRequest = objectMapper.createObjectNode();
        updateRequest.put("content", "Updated comment content");

        mockMvc.perform(put("/api/comments/{id}", commentId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Updated comment content"));

        // 5. Delete Comment
        mockMvc.perform(delete("/api/comments/{id}", commentId)
                .header("Authorization", authHeader))
                .andExpect(status().isOk());
    }

    @Test
    void testResolveComment_ShouldUpdateStatus() throws Exception {
        // Create a comment first
        ObjectNode addCommentRequest = objectMapper.createObjectNode();
        addCommentRequest.put("content", "Comment to be resolved");
        addCommentRequest.put("anchorType", "BLOCK");

        String commentResponse = mockMvc.perform(post("/api/docs/{docId}/comments", docId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addCommentRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long commentId = objectMapper.readTree(commentResponse).path("data").path("id").asLong();

        // Resolve the comment
        mockMvc.perform(post("/api/comments/{id}/resolve", commentId)
                .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isResolved").value(true))
                .andExpect(jsonPath("$.data.resolvedBy").exists())
                .andExpect(jsonPath("$.data.resolvedAt").exists());
    }

    @Test
    void testUnresolveComment_ShouldClearStatus() throws Exception {
        // Create and resolve a comment
        ObjectNode addCommentRequest = objectMapper.createObjectNode();
        addCommentRequest.put("content", "Comment to test unresolve");
        addCommentRequest.put("anchorType", "BLOCK");

        String commentResponse = mockMvc.perform(post("/api/docs/{docId}/comments", docId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addCommentRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long commentId = objectMapper.readTree(commentResponse).path("data").path("id").asLong();

        mockMvc.perform(post("/api/comments/{id}/resolve", commentId)
                .header("Authorization", authHeader))
                .andExpect(status().isOk());

        // Unresolve the comment
        mockMvc.perform(post("/api/comments/{id}/unresolve", commentId)
                .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isResolved").value(false));
    }

    @Test
    void testGetDocumentComments_ShouldReturnAllComments() throws Exception {
        // Create multiple comments
        ObjectNode comment1 = objectMapper.createObjectNode();
        comment1.put("content", "First comment");
        comment1.put("anchorType", "BLOCK");

        mockMvc.perform(post("/api/docs/{docId}/comments", docId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(comment1)))
                .andExpect(status().isOk());

        ObjectNode comment2 = objectMapper.createObjectNode();
        comment2.put("content", "Second comment");
        comment2.put("anchorType", "RANGE");

        mockMvc.perform(post("/api/docs/{docId}/comments", docId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(comment2)))
                .andExpect(status().isOk());

        // Get all comments
        mockMvc.perform(get("/api/docs/{docId}/comments", docId)
                .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void testGetUnresolvedCount() throws Exception {
        // Create comments with mixed resolved status
        ObjectNode comment1 = objectMapper.createObjectNode();
        comment1.put("content", "Unresolved comment 1");
        comment1.put("anchorType", "BLOCK");

        mockMvc.perform(post("/api/docs/{docId}/comments", docId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(comment1)))
                .andExpect(status().isOk());

        ObjectNode comment2 = objectMapper.createObjectNode();
        comment2.put("content", "Comment to resolve");
        comment2.put("anchorType", "BLOCK");

        String commentResponse = mockMvc.perform(post("/api/docs/{docId}/comments", docId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(comment2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long commentId = objectMapper.readTree(commentResponse).path("data").path("id").asLong();

        // Resolve one comment
        mockMvc.perform(post("/api/comments/{id}/resolve", commentId)
                .header("Authorization", authHeader))
                .andExpect(status().isOk());

        // Get unresolved count
        mockMvc.perform(get("/api/docs/{docId}/comments/unresolved-count", docId)
                .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
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
