package com.nopkg.hellodoc.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nopkg.hellodoc.entities.DocNotification;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.entities.SysUserAuth;
import com.nopkg.hellodoc.enums.NotifyType;
import com.nopkg.hellodoc.repositories.DocNotificationRepository;
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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class NotificationControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private UserAuthRepository userAuthRepository;

        @Autowired
        private DocNotificationRepository notificationRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        private String authHeader;
        private SysUser user;

        @BeforeEach
        void setUp() throws Exception {
                user = new SysUser();
                user.setNickname("notify_tester");
                user.setStatus((short) 0);
                user.setCreateTime(Instant.now());
                user.setUpdateTime(Instant.now());
                user = userRepository.save(user);

                SysUserAuth userAuth = new SysUserAuth();
                userAuth.setUser(user);
                userAuth.setIdentityType("PASSWORD");
                userAuth.setIdentifier("notify_tester");
                userAuth.setCredential(passwordEncoder.encode("123456"));
                userAuth.setStatus((short) 0);
                userAuth.setVerified(true);
                userAuth.setCreateTime(Instant.now());
                userAuthRepository.save(userAuth);

                authHeader = "Bearer " + loginAndGetAccessToken("notify_tester", "123456");

                // Create initial notifications
                createNotification(user.getId(), NotifyType.COMMENT, "Test Notification 1");
                createNotification(user.getId(), NotifyType.MENTION, "Test Notification 2");
        }

        private void createNotification(Long userId, NotifyType type, String content) {
                DocNotification notification = new DocNotification();
                notification.setUserId(userId);
                notification.setNotifyType(type);
                notification.setSenderId(userId); // Self sender for test
                notification.setDocId(1L); // Dummy docId
                notification.setContent(content);
                notification.setIsRead(false);
                notification.setCreatedAt(LocalDateTime.now());
                notificationRepository.save(notification);
        }

        @Test
        void testNotificationController_workflow() throws Exception {
                // 1. Get Unread Count
                mockMvc.perform(get("/api/notifications/unread-count")
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").value(2));

                // 2. List Notifications
                String listResponse = mockMvc.perform(get("/api/notifications")
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.content.length()").value(2))
                                .andReturn().getResponse().getContentAsString();

                long notificationId = objectMapper.readTree(listResponse).path("data").path("content").get(0).path("id")
                                .asLong();

                // 3. Mark as Read
                mockMvc.perform(put("/api/notifications/{id}/read", notificationId)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk());

                // Verify Unread Count Decreased
                mockMvc.perform(get("/api/notifications/unread-count")
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").value(1));

                // 4. Delete Notification
                mockMvc.perform(delete("/api/notifications/{id}", notificationId)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk());

                // Verify Deleted
                mockMvc.perform(get("/api/notifications")
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.content.length()").value(1));

                // 5. Mark All as Read
                mockMvc.perform(put("/api/notifications/read-all")
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk());

                // Verify All Read (Unread Count = 0)
                mockMvc.perform(get("/api/notifications/unread-count")
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").value(0));
        }

        @Test
        void testGetNotifications_ShouldOrderByTimeDesc() throws Exception {
                // Create notifications with delays to ensure different timestamps
                DocNotification notification1 = new DocNotification();
                notification1.setUserId(user.getId());
                notification1.setNotifyType(NotifyType.COMMENT);
                notification1.setSenderId(user.getId());
                notification1.setDocId(1L);
                notification1.setContent("Oldest notification");
                notification1.setIsRead(false);
                notification1.setCreatedAt(LocalDateTime.now().minusMinutes(10));
                notificationRepository.save(notification1);

                DocNotification notification2 = new DocNotification();
                notification2.setUserId(user.getId());
                notification2.setNotifyType(NotifyType.MENTION);
                notification2.setSenderId(user.getId());
                notification2.setDocId(1L);
                notification2.setContent("Newest notification");
                notification2.setIsRead(false);
                notification2.setCreatedAt(LocalDateTime.now());
                notificationRepository.save(notification2);

                // Get notifications and verify order (newest first)
                String response = mockMvc.perform(get("/api/notifications")
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                JsonNode notifications = objectMapper.readTree(response).path("data").path("content");
                assertTrue(notifications.get(0).path("content").asText().contains("Newest"));
                assertTrue(notifications.get(1).path("content").asText().contains("Test Notification 2"));
        }

        @Test
        void testGetNotifications_WithUnreadOnlyFilter() throws Exception {
                // Mark one notification as read
                String listResponse = mockMvc.perform(get("/api/notifications")
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                long notificationId = objectMapper.readTree(listResponse).path("data").path("content").get(0).path("id")
                                .asLong();

                mockMvc.perform(put("/api/notifications/{id}/read", notificationId)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk());

                // Get unread only
                mockMvc.perform(get("/api/notifications")
                                .header("Authorization", authHeader)
                                .param("unreadOnly", "true"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.content.length()").value(1));

                // Get all notifications
                mockMvc.perform(get("/api/notifications")
                                .header("Authorization", authHeader)
                                .param("unreadOnly", "false"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.content.length()").value(2));
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
