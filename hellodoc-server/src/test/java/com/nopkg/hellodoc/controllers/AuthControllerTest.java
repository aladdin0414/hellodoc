package com.nopkg.hellodoc.controllers;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证接口测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AuthControllerTest {

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

    @BeforeEach
    void setUp() {
        // 创建测试用户
        SysUser testUser = new SysUser();
        testUser.setNickname("testuser");
        testUser.setEmail("test@example.com");
        testUser.setStatus((short) 0);
        testUser.setCreateTime(Instant.now());
        testUser.setUpdateTime(Instant.now());
        testUser = userRepository.save(testUser);

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
    @DisplayName("POST /api/auth/login - 用户登录")
    class LoginTests {

        @Test
        @DisplayName("正确的用户名密码应登录成功")
        void login_withValidCredentials_shouldSucceed() throws Exception {
            ObjectNode loginRequest = objectMapper.createObjectNode();
            loginRequest.put("username", "testuser");
            loginRequest.put("password", "test123");

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.accessToken").exists())
                    .andExpect(jsonPath("$.data.refreshToken").exists())
                    .andExpect(jsonPath("$.data.nickname").value("testuser"));
        }

        @Test
        @DisplayName("错误的密码应返回错误")
        void login_withWrongPassword_shouldFail() throws Exception {
            ObjectNode loginRequest = objectMapper.createObjectNode();
            loginRequest.put("username", "testuser");
            loginRequest.put("password", "wrongpassword");

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk()) // 业务错误返回 HTTP 200
                    .andExpect(jsonPath("$.code").value(1001)); // USERNAME_OR_PASSWORD_ERROR
        }

        @Test
        @DisplayName("不存在的用户应返回错误")
        void login_withNonExistentUser_shouldFail() throws Exception {
            ObjectNode loginRequest = objectMapper.createObjectNode();
            loginRequest.put("username", "nonexistent");
            loginRequest.put("password", "password");

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk()) // 业务错误返回 HTTP 200
                    .andExpect(jsonPath("$.code").value(1001)); // USERNAME_OR_PASSWORD_ERROR
        }

        @Test
        @DisplayName("被禁用用户登录应返回账号禁用错误")
        void login_withDisabledUser_shouldFail() throws Exception {
            SysUser user = userRepository.findAll().stream()
                    .filter(u -> "testuser".equals(u.getNickname()))
                    .findFirst()
                    .orElseThrow();
            user.setStatus((short) 1);
            user.setUpdateTime(Instant.now());
            userRepository.save(user);

            ObjectNode loginRequest = objectMapper.createObjectNode();
            loginRequest.put("username", "testuser");
            loginRequest.put("password", "test123");

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1002)); // ACCOUNT_DISABLED
        }
    }

    @Nested
    @DisplayName("POST /api/auth/register - 用户注册")
    class RegisterTests {

        @Test
        @DisplayName("正常注册应成功")
        void register_withValidData_shouldSucceed() throws Exception {
            ObjectNode registerRequest = objectMapper.createObjectNode();
            registerRequest.put("username", "newuser");
            registerRequest.put("password", "newpass123");
            registerRequest.put("nickname", "New User");
            registerRequest.put("email", "new@example.com");

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.nickname").value("New User"));
        }

        @Test
        @DisplayName("重复用户名应返回错误")
        void register_withDuplicateUsername_shouldFail() throws Exception {
            ObjectNode registerRequest = objectMapper.createObjectNode();
            registerRequest.put("username", "testuser"); // 已存在
            registerRequest.put("password", "password");
            registerRequest.put("nickname", "Duplicate");

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1003)); // USERNAME_CONFLICT
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh-token - Token 刷新")
    class RefreshTokenTests {

        @Test
        @DisplayName("有效的 RefreshToken 应刷新成功")
        void refreshToken_withValidToken_shouldSucceed() throws Exception {
            // 先登录获取 refreshToken
            ObjectNode loginRequest = objectMapper.createObjectNode();
            loginRequest.put("username", "testuser");
            loginRequest.put("password", "test123");

            String loginResponse = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String refreshToken = objectMapper.readTree(loginResponse)
                    .path("data").path("refreshToken").asText();

            // 使用 refreshToken 刷新
            ObjectNode refreshRequest = objectMapper.createObjectNode();
            refreshRequest.put("refreshToken", refreshToken);

            mockMvc.perform(post("/api/auth/refresh-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.accessToken").exists())
                    .andExpect(jsonPath("$.data.refreshToken").exists());
        }

        @Test
        @DisplayName("无效的 RefreshToken 应返回错误")
        void refreshToken_withInvalidToken_shouldFail() throws Exception {
            ObjectNode refreshRequest = objectMapper.createObjectNode();
            refreshRequest.put("refreshToken", "invalid-token");

            mockMvc.perform(post("/api/auth/refresh-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1005)); // TOKEN_INVALID
        }

        @Test
        @DisplayName("使用 AccessToken 刷新应返回令牌类型错误")
        void refreshToken_withAccessToken_shouldFail() throws Exception {
            ObjectNode loginRequest = objectMapper.createObjectNode();
            loginRequest.put("username", "testuser");
            loginRequest.put("password", "test123");

            String loginResponse = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String accessToken = objectMapper.readTree(loginResponse)
                    .path("data").path("accessToken").asText();

            ObjectNode refreshRequest = objectMapper.createObjectNode();
            refreshRequest.put("refreshToken", accessToken);

            mockMvc.perform(post("/api/auth/refresh-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1006)); // TOKEN_TYPE_ERROR
        }

        @Test
        @DisplayName("RefreshToken 复用应触发家族吊销")
        void refreshToken_reuseShouldRevokeTokenFamily() throws Exception {
            ObjectNode loginRequest = objectMapper.createObjectNode();
            loginRequest.put("username", "testuser");
            loginRequest.put("password", "test123");

            String loginResponse = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String refreshToken1 = objectMapper.readTree(loginResponse)
                    .path("data").path("refreshToken").asText();

            ObjectNode refreshReq1 = objectMapper.createObjectNode();
            refreshReq1.put("refreshToken", refreshToken1);
            String refreshResponse = mockMvc.perform(post("/api/auth/refresh-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshReq1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String refreshToken2 = objectMapper.readTree(refreshResponse)
                    .path("data").path("refreshToken").asText();

            // 复用旧 token，应该失败并触发家族吊销
            mockMvc.perform(post("/api/auth/refresh-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshReq1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1005)); // TOKEN_INVALID

            // 家族已被吊销，新 token 也不再可用
            ObjectNode refreshReq2 = objectMapper.createObjectNode();
            refreshReq2.put("refreshToken", refreshToken2);
            mockMvc.perform(post("/api/auth/refresh-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshReq2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1005)); // TOKEN_INVALID
        }
    }

    @Nested
    @DisplayName("POST /api/auth/logout - 用户登出")
    class LogoutTests {

        @Test
        @DisplayName("登出应成功")
        void logout_shouldSucceed() throws Exception {
            // 先登录
            String accessToken = loginAndGetAccessToken("testuser", "test123");

            mockMvc.perform(post("/api/auth/logout")
                    .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
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
