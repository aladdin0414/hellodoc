package com.nopkg.hellodoc.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nopkg.hellodoc.entities.*;
import com.nopkg.hellodoc.repositories.*;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RBAC 模块测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class RbacControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthRepository userAuthRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long adminUserId;
    private Long normalUserId;
    private Long adminRoleId;
    private Long userRoleId;

    @BeforeEach
    void setUp() {
        // Clear data from DataInitializer
        userRoleRepository.deleteAll();
        userAuthRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        entityManager.flush();

        // 创建管理员角色
        SysRole adminRole = new SysRole();
        adminRole.setRoleCode("admin");
        adminRole.setRoleName("管理员");
        adminRole.setStatus((short) 0);
        adminRole.setCreateTime(Instant.now());
        adminRole = roleRepository.save(adminRole);
        adminRoleId = adminRole.getId();

        // 创建普通用户角色
        SysRole userRole = new SysRole();
        userRole.setRoleCode("user");
        userRole.setRoleName("普通用户");
        userRole.setStatus((short) 0);
        userRole.setCreateTime(Instant.now());
        userRole = roleRepository.save(userRole);
        userRoleId = userRole.getId();

        // 创建管理员用户
        SysUser adminUser = new SysUser();
        adminUser.setNickname("admin");
        adminUser.setStatus((short) 0);
        adminUser.setCreateTime(Instant.now());
        adminUser.setUpdateTime(Instant.now());
        adminUser = userRepository.save(adminUser);
        adminUserId = adminUser.getId();

        SysUserAuth adminAuth = new SysUserAuth();
        adminAuth.setUser(adminUser);
        adminAuth.setIdentityType("PASSWORD");
        adminAuth.setIdentifier("admin");
        adminAuth.setCredential(passwordEncoder.encode("admin123"));
        adminAuth.setStatus((short) 0);
        adminAuth.setVerified(true);
        adminAuth.setCreateTime(Instant.now());
        userAuthRepository.save(adminAuth);

        // 分配管理员角色
        SysUserRole adminUserRole = new SysUserRole();
        SysUserRoleId adminUserRoleId = new SysUserRoleId();
        adminUserRoleId.setUserId(adminUserId);
        adminUserRoleId.setRoleId(adminRoleId);
        adminUserRole.setId(adminUserRoleId);
        adminUserRole.setUser(adminUser);
        adminUserRole.setRole(adminRole);
        userRoleRepository.save(adminUserRole);

        // 创建普通用户
        SysUser normalUser = new SysUser();
        normalUser.setNickname("user");
        normalUser.setStatus((short) 0);
        normalUser.setCreateTime(Instant.now());
        normalUser.setUpdateTime(Instant.now());
        normalUser = userRepository.save(normalUser);
        normalUserId = normalUser.getId();

        SysUserAuth userAuth = new SysUserAuth();
        userAuth.setUser(normalUser);
        userAuth.setIdentityType("PASSWORD");
        userAuth.setIdentifier("user");
        userAuth.setCredential(passwordEncoder.encode("user123"));
        userAuth.setStatus((short) 0);
        userAuth.setVerified(true);
        userAuth.setCreateTime(Instant.now());
        userAuthRepository.save(userAuth);

        // 分配普通用户角色
        SysUserRole normalUserRole = new SysUserRole();
        SysUserRoleId normalUserRoleId = new SysUserRoleId();
        normalUserRoleId.setUserId(normalUserId);
        normalUserRoleId.setRoleId(userRoleId);
        normalUserRole.setId(normalUserRoleId);
        normalUserRole.setUser(normalUser);
        normalUserRole.setRole(userRole);
        userRoleRepository.save(normalUserRole);

        entityManager.flush();
    }

    @Nested
    @DisplayName("角色管理接口测试")
    class RoleControllerTests {

        @Test
            @DisplayName("admin 可以查看角色列表")
            void listRoles_asAdmin_shouldSucceed() throws Exception {
            String token = loginAndGetAccessToken("admin", "admin123");

            mockMvc.perform(get("/api/system/roles")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("普通用户无权查看角色列表")
        void listRoles_asNormalUser_shouldFail() throws Exception {
            String token = loginAndGetAccessToken("user", "user123");

            mockMvc.perform(get("/api/system/roles")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().is4xxClientError());
        }

        @Test
            @DisplayName("admin 可以创建角色")
            void createRole_asAdmin_shouldSucceed() throws Exception {
            String token = loginAndGetAccessToken("admin", "admin123");

            ObjectNode request = objectMapper.createObjectNode();
            request.put("roleCode", "EDITOR");
            request.put("roleName", "编辑者");
            request.put("status", 0);

            mockMvc.perform(post("/api/system/roles")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.roleCode").value("EDITOR"));
        }

        @Test
            @DisplayName("admin 可以分配角色")
            void assignRole_asAdmin_shouldSucceed() throws Exception {
            String token = loginAndGetAccessToken("admin", "admin123");

            ObjectNode request = objectMapper.createObjectNode();
            request.put("roleId", adminRoleId);

            mockMvc.perform(post("/api/system/roles/users/" + normalUserId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("可以获取用户角色列表")
        void getUserRoles_shouldSucceed() throws Exception {
            String token = loginAndGetAccessToken("admin", "admin123");

            mockMvc.perform(get("/api/system/roles/users/" + normalUserId)
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].roleCode").value("user"));
        }
    }

    @Nested
    @DisplayName("权限管理接口测试")
    class PermissionControllerTests {

        @Test
            @DisplayName("admin 可以查看权限列表")
            void listPermissions_asAdmin_shouldSucceed() throws Exception {
            String token = loginAndGetAccessToken("admin", "admin123");

            mockMvc.perform(get("/api/system/permissions")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
            @DisplayName("admin 可以创建权限")
            void createPermission_asAdmin_shouldSucceed() throws Exception {
            String token = loginAndGetAccessToken("admin", "admin123");

            ObjectNode request = objectMapper.createObjectNode();
            request.put("permCode", "kb:create");
            request.put("permName", "创建知识库");

            mockMvc.perform(post("/api/system/permissions")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.permCode").value("kb:create"));
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
