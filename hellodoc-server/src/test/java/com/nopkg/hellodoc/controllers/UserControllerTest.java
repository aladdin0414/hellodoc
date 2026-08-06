package com.nopkg.hellodoc.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.entities.SysUserAuth;
import com.nopkg.hellodoc.repositories.UserAuthRepository;
import com.nopkg.hellodoc.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户接口测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class UserControllerTest {

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

    private Long testUserId;

    @BeforeEach
    void setUp() {
        // 创建测试用户
        SysUser testUser = new SysUser();
        testUser.setNickname("testuser");
        testUser.setRealName("Test User");
        testUser.setEmail("test@example.com");
        testUser.setPhone("13800138000");
        testUser.setAvatar("https://example.com/avatar.png");
        testUser.setStatus((short) 0);
        testUser.setCreateTime(Instant.now());
        testUser.setUpdateTime(Instant.now());
        testUser = userRepository.save(testUser);
        testUserId = testUser.getId();

        SysUserAuth testAuth = new SysUserAuth();
        testAuth.setUser(testUser);
        testAuth.setIdentityType("PASSWORD");
        testAuth.setIdentifier("testuser");
        testAuth.setCredential(passwordEncoder.encode("test123"));
        testAuth.setStatus((short) 0);
        testAuth.setVerified(true);
        testAuth.setCreateTime(Instant.now());
        userAuthRepository.save(testAuth);
    }

    @Nested
    @DisplayName("GET /api/users/me - 获取当前用户")
    class GetCurrentUserTests {

        @Test
        @DisplayName("已登录用户应返回用户信息")
        void getCurrentUser_whenLoggedIn_shouldReturnUserInfo() throws Exception {
            String accessToken = loginAndGetAccessToken("testuser", "test123");

            mockMvc.perform(get("/api/users/me")
                    .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(testUserId))
                    .andExpect(jsonPath("$.data.nickname").value("testuser"))
                    .andExpect(jsonPath("$.data.realName").value("Test User"))
                    .andExpect(jsonPath("$.data.email").value("test@example.com"))
                    .andExpect(jsonPath("$.data.phone").value("13800138000"))
                    .andExpect(jsonPath("$.data.avatar").value("https://example.com/avatar.png"));
        }

        @Test
        @DisplayName("未登录应返回 4xx 错误")
        void getCurrentUser_whenNotLoggedIn_shouldReturn4xx() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().is4xxClientError()); // 401 或 403
        }

        @Test
        @DisplayName("无效 Token 应返回 4xx 错误")
        void getCurrentUser_withInvalidToken_shouldReturn4xx() throws Exception {
            mockMvc.perform(get("/api/users/me")
                    .header("Authorization", "Bearer invalid-token"))
                    .andExpect(status().is4xxClientError()); // 401 或 403
        }

        @Test
        @DisplayName("query 参数 token 不应通过认证")
        void getCurrentUser_withQueryToken_shouldReturn4xx() throws Exception {
            String accessToken = loginAndGetAccessToken("testuser", "test123");
            mockMvc.perform(get("/api/users/me").param("token", accessToken))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("用户被禁用后旧 token 应失效")
        void getCurrentUser_whenUserDisabledAfterLogin_shouldReturnUnauthorized() throws Exception {
            String accessToken = loginAndGetAccessToken("testuser", "test123");
            SysUser user = userRepository.findById(testUserId).orElseThrow();
            user.setStatus((short) 1);
            user.setUpdateTime(Instant.now());
            userRepository.save(user);

            mockMvc.perform(get("/api/users/me")
                    .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("管理员禁用用户后应吊销 refresh token 且 access token 失效")
        void disableUserByAdmin_shouldRevokeRefreshAndInvalidateAccess() throws Exception {
            ObjectNode loginRequest = objectMapper.createObjectNode();
            loginRequest.put("username", "testuser");
            loginRequest.put("password", "test123");
            String loginResponse = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String accessToken = objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();
            String refreshToken = objectMapper.readTree(loginResponse).path("data").path("refreshToken").asText();

            ObjectNode disableReq = objectMapper.createObjectNode();
            disableReq.put("status", 1);
            mockMvc.perform(put("/api/users/" + testUserId)
                    .with(user("admin").roles("admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(disableReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            mockMvc.perform(get("/api/users/me")
                    .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isUnauthorized());

            ObjectNode refreshReq = objectMapper.createObjectNode();
            refreshReq.put("refreshToken", refreshToken);
            mockMvc.perform(post("/api/auth/refresh-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1005)); // TOKEN_INVALID
        }

        @Test
        @DisplayName("管理员更新用户不传 status 不应修改账户状态")
        void updateUserWithoutStatus_shouldKeepOriginalStatus() throws Exception {
            ObjectNode updateReq = objectMapper.createObjectNode();
            updateReq.put("nickname", "new-name");
            mockMvc.perform(put("/api/users/" + testUserId)
                    .with(user("admin").roles("admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            Short status = userRepository.findById(testUserId).orElseThrow().getStatus();
            assertEquals((short) 0, status);
        }
    }

    @Nested
    @DisplayName("PUT /api/users/profile - 更新个人资料")
    class UpdateProfileTests {

        @Test
        @DisplayName("更新资料应成功")
        void updateProfile_shouldSucceed() throws Exception {
            String accessToken = loginAndGetAccessToken("testuser", "test123");

            ObjectNode request = objectMapper.createObjectNode();
            request.put("nickname", "Updated Name");
            request.put("realName", "Real Updated");
            request.put("email", "updated@example.com");
            request.put("phone", "13900139000");

            mockMvc.perform(put("/api/users/profile")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            // 验证更新生效
            mockMvc.perform(get("/api/users/me")
                    .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("Updated Name"))
                    .andExpect(jsonPath("$.data.realName").value("Real Updated"))
                    .andExpect(jsonPath("$.data.email").value("updated@example.com"))
                    .andExpect(jsonPath("$.data.phone").value("13900139000"));
        }
    }

    @Nested
    @DisplayName("PUT /api/users/change-pwd - 修改密码")
    class ChangePasswordTests {

        @Test
        @DisplayName("正确的旧密码应修改成功")
        void changePassword_withCorrectOldPassword_shouldSucceed() throws Exception {
            String accessToken = loginAndGetAccessToken("testuser", "test123");

            ObjectNode request = objectMapper.createObjectNode();
            request.put("oldPassword", "test123");
            request.put("newPassword", "newpass456");

            mockMvc.perform(put("/api/users/change-pwd")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            // 验证新密码可以登录
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"testuser\",\"password\":\"newpass456\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("错误的旧密码应返回错误")
        void changePassword_withWrongOldPassword_shouldFail() throws Exception {
            String accessToken = loginAndGetAccessToken("testuser", "test123");

            ObjectNode request = objectMapper.createObjectNode();
            request.put("oldPassword", "wrongpassword");
            request.put("newPassword", "newpass456");

            mockMvc.perform(put("/api/users/change-pwd")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1008))
                    .andExpect(jsonPath("$.message").value("旧密码错误"));
        }
    }

    @Nested
    @DisplayName("PUT /api/users/avatar - 更新头像")
    class UpdateAvatarTests {

        @Test
        @DisplayName("更新头像应成功")
        void updateAvatar_shouldSucceed() throws Exception {
            String accessToken = loginAndGetAccessToken("testuser", "test123");

            ObjectNode request = objectMapper.createObjectNode();
            request.put("avatar", "https://example.com/new-avatar.png");

            mockMvc.perform(put("/api/users/avatar")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            // 验证更新生效
            mockMvc.perform(get("/api/users/me")
                    .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.avatar").value("https://example.com/new-avatar.png"));
        }

        @Test
        @DisplayName("上传头像文件应成功并更新资料")
        void uploadAvatar_shouldSucceedAndUpdateProfile() throws Exception {
            String accessToken = loginAndGetAccessToken("testuser", "test123");

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test-avatar.png",
                    MediaType.IMAGE_PNG_VALUE,
                    "test image content".getBytes());

            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/users/avatar")
                    .file(file)
                    .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.url").exists())
                    .andExpect(jsonPath("$.data.filename").exists());

            // 验证个人资料已更新
            mockMvc.perform(get("/api/users/me")
                    .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.avatar")
                            .value(org.hamcrest.Matchers.containsString("/api/files/avatars/avatar_")));
        }
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

        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
