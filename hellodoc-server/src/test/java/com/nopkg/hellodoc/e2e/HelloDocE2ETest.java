package com.nopkg.hellodoc.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nopkg.hellodoc.entities.KbStorageConfig;
import com.nopkg.hellodoc.entities.SysRole;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.entities.SysUserAuth;
import com.nopkg.hellodoc.entities.SysUserRole;
import com.nopkg.hellodoc.entities.SysUserRoleId;
import com.nopkg.hellodoc.repositories.KbStorageConfigRepository;
import com.nopkg.hellodoc.repositories.RoleRepository;
import com.nopkg.hellodoc.repositories.UserAuthRepository;
import com.nopkg.hellodoc.repositories.UserRepository;
import com.nopkg.hellodoc.repositories.UserRoleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.cache.type=none",
        "file.upload-dir=build/test-uploads-e2e",
        "storage.orphan.grace-hours=0",
        "storage.url-expiration-seconds=3600"
})
@AutoConfigureMockMvc
public class HelloDocE2ETest {

    private static final Path UPLOAD_DIR = Paths.get("build", "test-uploads-e2e");
    private static final Path APD_DOC_PATH = Paths.get("/Users/liyc/code/github-me/hellodoc/api-doc/api-doc.md");

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

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private KbStorageConfigRepository storageConfigRepository;

    private Long adminUserId;
    private Long bobUserId;
    private String adminAuthHeader;
    private String bobAuthHeader;
    private final StringBuilder apiDoc = new StringBuilder();
    private int apiDocSeq = 0;

    @BeforeEach
    void setUp() throws Exception {
        ensureDefaultLocalStorageConfig();
        initApiDoc();

        SysRole adminRole = new SysRole();
        adminRole.setRoleCode("admin");
        adminRole.setRoleName("管理员");
        adminRole.setStatus((short) 0);
        adminRole.setCreateTime(Instant.now());
        adminRole = roleRepository.save(adminRole);

        SysRole userRole = new SysRole();
        userRole.setRoleCode("user");
        userRole.setRoleName("普通用户");
        userRole.setStatus((short) 0);
        userRole.setCreateTime(Instant.now());
        userRole = roleRepository.save(userRole);

        SysUser admin = new SysUser();
        admin.setNickname("liyc");
        admin.setStatus((short) 0);
        admin.setCreateTime(Instant.now());
        admin.setUpdateTime(Instant.now());
        admin = userRepository.save(admin);
        adminUserId = admin.getId();

        SysUserAuth adminAuth = new SysUserAuth();
        adminAuth.setUser(admin);
        adminAuth.setIdentityType("PASSWORD");
        adminAuth.setIdentifier("liyc");
        adminAuth.setCredential(passwordEncoder.encode("111111"));
        adminAuth.setStatus((short) 0);
        adminAuth.setVerified(true);
        adminAuth.setCreateTime(Instant.now());
        userAuthRepository.save(adminAuth);

        assignRole(admin, adminRole);

        SysUser bob = new SysUser();
        bob.setNickname("bob");
        bob.setStatus((short) 0);
        bob.setCreateTime(Instant.now());
        bob.setUpdateTime(Instant.now());
        bob = userRepository.save(bob);
        bobUserId = bob.getId();

        SysUserAuth bobAuth = new SysUserAuth();
        bobAuth.setUser(bob);
        bobAuth.setIdentityType("PASSWORD");
        bobAuth.setIdentifier("bob");
        bobAuth.setCredential(passwordEncoder.encode("111111"));
        bobAuth.setStatus((short) 0);
        bobAuth.setVerified(true);
        bobAuth.setCreateTime(Instant.now());
        userAuthRepository.save(bobAuth);

        assignRole(bob, userRole);

        adminAuthHeader = "Bearer " + loginAndGetAccessToken("liyc", "111111");
        bobAuthHeader = "Bearer " + loginAndGetAccessToken("bob", "111111");
    }

    @AfterEach
    void tearDown() throws Exception {
        writeApiDoc();
        if (Files.exists(UPLOAD_DIR)) {
            try (var s = Files.walk(UPLOAD_DIR)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    private ResultActions perform(RequestBuilder requestBuilder) throws Exception {
        return mockMvc.perform(requestBuilder).andDo(this::appendApiDoc);
    }

    private void initApiDoc() {
        apiDoc.setLength(0);
        apiDocSeq = 0;
        apiDoc.append("# HelloDocE2E 接口请求记录\n\n");
        apiDoc.append("- 来源：`HelloDocE2ETest.e2e_full_workflow_shouldSucceed`\n");
        apiDoc.append("- 生成时间：").append(java.time.OffsetDateTime.now()).append("\n\n");
    }

    private void writeApiDoc() throws Exception {
        Files.createDirectories(APD_DOC_PATH.getParent());
        Files.writeString(APD_DOC_PATH, apiDoc.toString(), StandardCharsets.UTF_8);
    }

    private void appendApiDoc(MvcResult result) throws Exception {
        apiDocSeq++;
        MockHttpServletRequest req = result.getRequest();
        MockHttpServletResponse resp = result.getResponse();

        String uri = toDocUri(req);

        apiDoc.append("## ").append(apiDocSeq).append(". ").append(req.getMethod()).append(" ").append(uri)
                .append("\n\n");
        String apiDesc = apiDescription(result.getHandler());
        if (apiDesc != null && !apiDesc.isBlank()) {
            apiDoc.append("- 描述：").append(apiDesc).append("\n\n");
        }

        apiDoc.append("**请求参数格式**\n\n");
        apiDoc.append("- Headers\n\n");
        apiDoc.append("```text\n");
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            Enumeration<String> values = req.getHeaders(name);
            while (values != null && values.hasMoreElements()) {
                String value = values.nextElement();
                apiDoc.append(name).append(": ").append(redactHeaderValue(name, value)).append("\n");
            }
        }
        apiDoc.append("```\n\n");

        apiDoc.append("- Params\n\n");
        apiDoc.append("```json\n");
        apiDoc.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(req.getParameterMap()));
        apiDoc.append("\n```\n\n");

        String reqContentType = req.getContentType();
        if (reqContentType != null && reqContentType.toLowerCase().startsWith("multipart/")
                && req instanceof MockMultipartHttpServletRequest multipartReq) {
            apiDoc.append("- Multipart\n\n");
            apiDoc.append("```json\n");
            ObjectNode mp = objectMapper.createObjectNode();
            ObjectNode files = objectMapper.createObjectNode();
            multipartReq.getFileMap().forEach((k, v) -> {
                ObjectNode f = objectMapper.createObjectNode();
                f.put("originalFilename", v.getOriginalFilename());
                f.put("contentType", v.getContentType());
                f.put("size", v.getSize());
                files.set(k, f);
            });
            mp.set("files", files);
            mp.set("params", objectMapper.valueToTree(multipartReq.getParameterMap()));
            apiDoc.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mp));
            apiDoc.append("\n```\n\n");
        } else {
            byte[] reqBodyBytes = req.getContentAsByteArray();
            if (reqBodyBytes != null && reqBodyBytes.length > 0) {
                String reqCt = reqContentType == null ? "" : reqContentType.toLowerCase();
                apiDoc.append("- Body\n\n");
                if (reqCt.contains("json") || reqCt.contains("+json")) {
                    String reqBody = new String(reqBodyBytes, StandardCharsets.UTF_8).trim();
                    apiDoc.append("```json\n");
                    apiDoc.append(prettyIfJson(reqBody));
                    apiDoc.append("\n```\n\n");
                } else if (isTextContentType(reqContentType)
                        || looksLikeJson(new String(reqBodyBytes, StandardCharsets.UTF_8).trim())) {
                    String reqBody = new String(reqBodyBytes, StandardCharsets.UTF_8).trim();
                    if (looksLikeJson(reqBody)) {
                        apiDoc.append("```json\n");
                        apiDoc.append(prettyIfJson(reqBody));
                        apiDoc.append("\n```\n\n");
                    } else {
                        apiDoc.append("```text\n");
                        apiDoc.append(reqBody);
                        apiDoc.append("\n```\n\n");
                    }
                } else {
                    apiDoc.append("```text\n");
                    apiDoc.append("<binary ").append(reqBodyBytes.length).append(" bytes>");
                    apiDoc.append("\n```\n\n");
                }
            }
        }

        apiDoc.append("**响应参数格式**\n\n");
        apiDoc.append("- HTTP ").append(resp.getStatus()).append("\n");
        apiDoc.append("- Content-Type ").append(resp.getContentType() == null ? "" : resp.getContentType())
                .append("\n\n");

        byte[] bodyBytes = resp.getContentAsByteArray();
        if (bodyBytes == null || bodyBytes.length == 0) {
            apiDoc.append("```text\n");
            apiDoc.append("<empty>\n");
            apiDoc.append("```\n\n");
        } else {
            String respCt = resp.getContentType() == null ? "" : resp.getContentType().toLowerCase();
            if (respCt.contains("json") || respCt.contains("+json")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8).trim();
                apiDoc.append("```json\n");
                apiDoc.append(prettyIfJson(body));
                apiDoc.append("\n```\n\n");
            } else if (isTextContentType(resp.getContentType())) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8).trim();
                if (looksLikeJson(body)) {
                    apiDoc.append("```json\n");
                    apiDoc.append(prettyIfJson(body));
                    apiDoc.append("\n```\n\n");
                } else {
                    apiDoc.append("```text\n");
                    apiDoc.append(body);
                    apiDoc.append("\n```\n\n");
                }
            } else {
                apiDoc.append("```text\n");
                apiDoc.append("<binary ").append(bodyBytes.length).append(" bytes>");
                apiDoc.append("\n```\n\n");
            }
        }
    }

    private boolean isTextContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String ct = contentType.toLowerCase();
        return ct.startsWith("text/")
                || ct.contains("application/xml")
                || ct.contains("application/xhtml")
                || ct.contains("application/html")
                || ct.contains("application/x-www-form-urlencoded");
    }

    private String redactHeaderValue(String name, String value) {
        if (name == null || value == null) {
            return value;
        }
        if ("authorization".equalsIgnoreCase(name) && value.toLowerCase().startsWith("bearer ")) {
            return "Bearer <redacted>";
        }
        return value;
    }

    private boolean looksLikeJson(String s) {
        String t = s == null ? "" : s.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }

    private String prettyIfJson(String s) {
        try {
            JsonNode node = objectMapper.readTree(s);
            JsonNode sanitized = sanitizeForDoc(node);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sanitized);
        } catch (Exception e) {
            return s;
        }
    }

    private String apiDescription(Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return "";
        }
        String tagName = "";
        Tag tag = hm.getBeanType().getAnnotation(Tag.class);
        if (tag != null && tag.name() != null && !tag.name().isBlank()) {
            tagName = tag.name().trim();
        }
        Operation op = hm.getMethodAnnotation(Operation.class);
        String summary = op == null ? null : trimToNull(op.summary());
        String description = op == null ? null : trimToNull(op.description());
        String text = summary != null ? summary : description;
        if (text == null) {
            text = hm.getBeanType().getSimpleName() + "." + hm.getMethod().getName();
        }
        if (tagName.isBlank()) {
            return text;
        }
        return "【" + tagName + "】" + text;
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private String toDocUri(MockHttpServletRequest req) {
        Object patternObj = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = patternObj instanceof String p && !p.isBlank() ? p : req.getRequestURI();
        String query = req.getQueryString();
        if (query != null && !query.isBlank()) {
            return path + "?" + toDocQueryString(query);
        }
        if ("GET".equalsIgnoreCase(req.getMethod()) && req.getParameterMap() != null
                && !req.getParameterMap().isEmpty()) {
            return path + "?" + toDocQueryStringFromKeys(req.getParameterMap().keySet().toArray(new String[0]));
        }
        return path;
    }

    private String toDocQueryString(String queryString) {
        StringBuilder sb = new StringBuilder();
        for (String pair : queryString.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] kv = pair.split("=", 2);
            String rawKey = kv[0];
            String key = rawKey.isBlank() ? "param" : rawKey;
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(key).append("={").append(key).append("}");
        }
        return sb.toString();
    }

    private String toDocQueryStringFromKeys(String[] keys) {
        java.util.Arrays.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String rawKey : keys) {
            String key = rawKey == null || rawKey.isBlank() ? "param" : rawKey;
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(key).append("={").append(key).append("}");
        }
        return sb.toString();
    }

    private JsonNode sanitizeForDoc(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node instanceof ObjectNode obj) {
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            obj.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = obj.get(name);
                if (shouldRedactKey(name)) {
                    obj.put(name, "<redacted>");
                } else {
                    sanitizeForDoc(child);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                sanitizeForDoc(child);
            }
        }
        return node;
    }

    private boolean shouldRedactKey(String key) {
        if (key == null) {
            return false;
        }
        String k = key.toLowerCase();
        return k.equals("password")
                || k.equals("credential")
                || k.equals("accesstoken")
                || k.equals("refreshtoken")
                || k.equals("secret")
                || k.equals("secretkey")
                || k.equals("secretkeyencrypted");
    }

    @Test
    @DisplayName("单类 E2E：认证/知识库/文档/搜索/关系/附件/评论通知/RBAC/字典配置/审计")
    void e2e_full_workflow_shouldSucceed() throws Exception {
        long ts = System.currentTimeMillis();
        String regUsername = "e2e_user_" + ts;
        String regNickname = "E2E-" + ts;

        ObjectNode register = objectMapper.createObjectNode();
        register.put("username", regUsername);
        register.put("password", "111111");
        register.put("nickname", regNickname);
        perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value(regNickname));

        perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));

        String regAuthHeader = "Bearer " + loginAndGetAccessToken(regUsername, "111111");
        perform(get("/api/users/me").header("Authorization", regAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(regUsername))
                .andExpect(jsonPath("$.data.nickname").value(regNickname));

        perform(get("/api/users/me").header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("liyc"));

        perform(get("/api/kb/listKnowledgeBases").header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        long kbId = createKb(adminAuthHeader, "E2E 知识库");

        perform(get("/api/kb/listKnowledgeBases").header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value((int) kbId));

        ObjectNode pin = objectMapper.createObjectNode();
        pin.put("pinned", true);
        perform(post("/api/kb/{kbId}/pin", kbId)
                .header("Authorization", adminAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        ObjectNode reorder = objectMapper.createObjectNode();
        reorder.putArray("kbIds").add(kbId);
        perform(post("/api/kb/reorder")
                .header("Authorization", adminAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reorder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ObjectNode updateKb = objectMapper.createObjectNode();
        updateKb.put("title", "E2E 知识库-已更新");
        updateKb.put("visibility", "PUBLIC");
        perform(put("/api/kb/{kbId}", kbId)
                .header("Authorization", adminAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateKb)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("E2E 知识库-已更新"));

        ObjectNode addMember = objectMapper.createObjectNode();
        addMember.put("username", "bob");
        addMember.put("role", "editor");
        perform(post("/api/kb/{kbId}/members", kbId)
                .header("Authorization", adminAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addMember)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(bobUserId.intValue()))
                .andExpect(jsonPath("$.data.role").value("editor"));

        String bobKbs = perform(get("/api/kb/listKnowledgeBases").header("Authorization", bobAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        JsonNode bobKbList = objectMapper.readTree(bobKbs).path("data");
        Assertions.assertTrue(containsId(bobKbList, kbId));
        JsonNode bobKb = findById(bobKbList, kbId);
        Assertions.assertEquals(adminUserId.longValue(), bobKb.path("ownerId").asLong());
        Assertions.assertTrue(bobKb.path("isShared").asBoolean());

        long docA = createDoc(adminAuthHeader, kbId, "文档A", "Hello HelloDocSearch @bob");
        long docB = createDoc(adminAuthHeader, kbId, "文档B", "B content");
        long folderId = createFolder(adminAuthHeader, kbId, "测试目录");
        long docInFolder = createDocInFolder(adminAuthHeader, kbId, folderId, "测试文档2", "测试文档2内容");

        String docAResp1 = perform(get("/api/kb/{kbId}/documents/{docId}", kbId, docA)
                .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value((int) docA))
                .andReturn().getResponse().getContentAsString();
        long viewCount1 = objectMapper.readTree(docAResp1).path("data").path("viewCount").asLong();
        Assertions.assertTrue(viewCount1 >= 1);

        ObjectNode updateDoc = objectMapper.createObjectNode();
        updateDoc.put("content", "参考[[文档B]]以及![[文档B]] HelloDocSearch");
        perform(put("/api/kb/{kbId}/documents/{docId}", kbId, docA)
                .header("Authorization", adminAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDoc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ObjectNode createRev = objectMapper.createObjectNode();
        createRev.put("content", "rev1 content");
        createRev.put("message", "m1");
        perform(post("/api/kb/{kbId}/documents/{docId}/revisions", kbId, docA)
                .header("Authorization", adminAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRev)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.version").value(3));

        ObjectNode createRev2 = objectMapper.createObjectNode();
        createRev2.put("content", "rev2 content");
        createRev2.put("message", "m2");
        perform(post("/api/kb/{kbId}/documents/{docId}/revisions", kbId, docA)
                .header("Authorization", adminAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRev2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.version").value(4));

        perform(post("/api/docs/{docId}/revisions/{version}/restore", docA, 3)
                .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        perform(get("/api/kb/{kbId}/documents/{docId}", kbId, docA)
                .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").value("rev1 content"));

        perform(get("/api/kb/{kbId}/documents/{docId}/revisions", kbId, docA)
                .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        perform(get("/api/docs/{docId}/revisions", docA)
                .header("Authorization", adminAuthHeader)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").isArray());

        String treeJson = perform(get("/api/kb/{kbId}/documents", kbId)
                .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn().getResponse().getContentAsString();
        JsonNode tree = objectMapper.readTree(treeJson).path("data");
        Assertions.assertEquals("folder", findById(tree, folderId).path("type").asText());
        Assertions.assertEquals(folderId, findById(tree, docInFolder).path("parentId").asLong());

        ObjectNode moveDoc = objectMapper.createObjectNode();
        moveDoc.put("parentId", folderId);
        moveDoc.put("orderNum", 7);
        perform(put("/api/kb/{kbId}/documents/{docId}", kbId, docB)
                .header("Authorization", adminAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(moveDoc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        perform(get("/api/kb/{kbId}/documents/{docId}", kbId, docB)
                .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.parentId").value((int) folderId))
                .andExpect(jsonPath("$.data.orderNum").value(7));

        ObjectNode publish = objectMapper.createObjectNode();
        publish.put("status", "published");
        perform(put("/api/kb/{kbId}/documents/{docId}", kbId, docA)
                .header("Authorization", adminAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(publish)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("published"));

        MockMultipartFile docUpload = new MockMultipartFile("file", "hello-doc.txt", MediaType.TEXT_PLAIN_VALUE,
                "hello-doc-asset".getBytes());
        long docAssetId = uploadToDoc(kbId, docA, docUpload);
        String docAssetsJson = perform(get("/api/docs/{docId}/assets", docA)
                .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        Assertions.assertTrue(containsId(objectMapper.readTree(docAssetsJson).path("data"), docAssetId));

        String recentJson = perform(
                get("/api/docs/recent").header("Authorization", adminAuthHeader).param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn().getResponse().getContentAsString();
        Assertions.assertTrue(containsId(objectMapper.readTree(recentJson).path("data"), docA));

        perform(get("/api/kb/{kbId}/search", kbId)
                .header("Authorization", adminAuthHeader)
                .param("q", "HelloDocSearch")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        perform(get("/api/search")
                .header("Authorization", adminAuthHeader)
                .param("q", "HelloDocSearch")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        waitForDocRelations(adminAuthHeader, docA);

        String docAResp2 = perform(get("/api/kb/{kbId}/documents/{docId}", kbId, docA)
                .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long viewCount2 = objectMapper.readTree(docAResp2).path("data").path("viewCount").asLong();
        Assertions.assertTrue(viewCount2 > viewCount1);

        perform(get("/api/docs/favorites").header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        perform(post("/api/docs/{docId}/favorite", docA).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        perform(get("/api/docs/{docId}/favorite", docA).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        perform(delete("/api/docs/{docId}/favorite", docA).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ObjectNode shareLink = objectMapper.createObjectNode();
        shareLink.put("role", "viewer");
        String linkResp = perform(post("/api/docs/{docId}/share/link", docA)
                .header("Authorization", adminAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shareLink)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String linkToken = objectMapper.readTree(linkResp).path("data").path("linkToken").asText();
        Assertions.assertTrue(linkToken != null && !linkToken.isBlank());

        ObjectNode shareUser = objectMapper.createObjectNode();
        shareUser.put("targetType", "user");
        shareUser.put("username", "bob");
        shareUser.put("role", "viewer");
        perform(post("/api/docs/{docId}/share", docA)
                .header("Authorization", adminAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shareUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.role").value("viewer"));

        long bobDoc = createDoc(bobAuthHeader, kbId, "Bob Doc", "Bob content");
        Assertions.assertTrue(bobDoc > 0);

        long commentId = addComment(adminAuthHeader, docA, "@bob 你好", "POINT", "{\"x\":1}");
        String commentsAfterAdd = perform(
                get("/api/docs/{docId}/comments", docA).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn().getResponse().getContentAsString();
        Assertions.assertEquals(1, objectMapper.readTree(commentsAfterAdd).path("data").size());

        perform(get("/api/docs/{docId}/comments/unresolved-count", docA).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(1));

        perform(get("/api/notifications/unread-count").header("Authorization", bobAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        String unreadResp = perform(get("/api/notifications")
                .header("Authorization", bobAuthHeader)
                .param("unreadOnly", "true")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        Assertions.assertTrue(objectMapper.readTree(unreadResp).path("data").has("content"));

        ObjectNode reply = objectMapper.createObjectNode();
        reply.put("content", "收到 @liyc");
        perform(post("/api/comments/{id}/reply", commentId)
                .header("Authorization", bobAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reply)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String commentsAfterReply = perform(
                get("/api/docs/{docId}/comments", docA).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn().getResponse().getContentAsString();
        Assertions.assertEquals(2, objectMapper.readTree(commentsAfterReply).path("data").size());

        perform(post("/api/comments/{id}/resolve", commentId).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.isResolved").value(true));

        perform(get("/api/docs/{docId}/comments/unresolved-count", docA).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(0));

        perform(put("/api/notifications/read-all").header("Authorization", bobAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        perform(get("/api/notifications/unread-count").header("Authorization", bobAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(0));

        perform(get("/api/kb/{kbId}/audit-logs", kbId)
                .header("Authorization", adminAuthHeader)
                .param("pageNum", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").isArray());

        perform(get("/api/docs/{docId}/audit-logs", docA)
                .header("Authorization", adminAuthHeader)
                .param("pageNum", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").isArray());

        long assetKbId = kbId;
        MockMultipartFile upload = new MockMultipartFile("file", "hello.txt", MediaType.TEXT_PLAIN_VALUE,
                "hello-storage".getBytes());
        long assetId = uploadToKb(assetKbId, upload);
        String url = getAssetUrl(assetId);
        Map<String, String> query = parseQueryParams(url);
        perform(get("/api/storage/public")
                .param("key", query.get("key"))
                .param("exp", query.get("exp"))
                .param("sig", query.get("sig")))
                .andExpect(status().isOk());

        perform(delete("/api/assets/{id}", assetId).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        long dictTypeId = createDictType(adminAuthHeader, "e2e_status");
        long dictDataId = createDictData(adminAuthHeader, dictTypeId, "e2e_status", "正常", "0");
        Assertions.assertTrue(dictDataId > 0);

        perform(get("/api/public/dict/e2e_status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        createConfig(adminAuthHeader, "e2e.site.name", "站点名", "HelloDoc", true);
        perform(get("/api/public/configs/frontend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data['e2e.site.name']").value("HelloDoc"));

        long roleId = createRole(adminAuthHeader, "E2E_ROLE_" + System.currentTimeMillis(), "E2E 角色");
        long permId = createPermission(adminAuthHeader, "e2e:perm:" + System.currentTimeMillis(), "E2E 权限");
        grantPermissionToRole(adminAuthHeader, roleId, permId);
        assignRoleToUser(adminAuthHeader, bobUserId, roleId);

        perform(get("/api/system/roles/users/{userId}", bobUserId).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        perform(get("/api/system/permissions/users/{userId}", bobUserId).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        perform(get("/api/system/storage/configs").header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        String avatarFilename = uploadAndGetAvatarFilename();
        perform(get("/api/files/avatars/{filename}", avatarFilename))
                .andExpect(status().isOk());

        long trashKbId = createKb(adminAuthHeader, "待删除知识库");
        perform(delete("/api/kb/{kbId}", trashKbId).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String kbsAfterDelete = perform(get("/api/kb/listKnowledgeBases").header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        Assertions.assertFalse(containsId(objectMapper.readTree(kbsAfterDelete).path("data"), trashKbId));

        String trashList = perform(get("/api/kb/trash").header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        Assertions.assertTrue(containsId(objectMapper.readTree(trashList).path("data"), trashKbId));

        perform(post("/api/kb/{kbId}/restore", trashKbId).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String kbsAfterRestore = perform(get("/api/kb/listKnowledgeBases").header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        Assertions.assertTrue(containsId(objectMapper.readTree(kbsAfterRestore).path("data"), trashKbId));

        perform(delete("/api/kb/{kbId}", trashKbId).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        perform(delete("/api/kb/{kbId}/documents/{docId}", kbId, docB)
                .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        perform(delete("/api/kb/{kbId}", kbId).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void assignRole(SysUser user, SysRole role) {
        SysUserRole ur = new SysUserRole();
        SysUserRoleId id = new SysUserRoleId();
        id.setUserId(user.getId());
        id.setRoleId(role.getId());
        ur.setId(id);
        ur.setUser(user);
        ur.setRole(role);
        userRoleRepository.save(ur);
    }

    private String loginAndGetAccessToken(String username, String password) throws Exception {
        ObjectNode loginRequest = objectMapper.createObjectNode();
        loginRequest.put("username", username);
        loginRequest.put("password", password);

        String response = perform(post("/api/auth/login")
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

    private long createKb(String authHeader, String title) throws Exception {
        ObjectNode createKbRequest = objectMapper.createObjectNode();
        createKbRequest.put("title", title);
        createKbRequest.put("description", "用于 E2E");
        createKbRequest.put("color", "blue");
        createKbRequest.put("icon", "book");
        createKbRequest.put("allowAnonymous", false);
        createKbRequest.put("visibility", "PRIVATE");

        String resp = perform(post("/api/kb/createKnowledgeBase")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createKbRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private long createDoc(String authHeader, long kbId, String name, String content) throws Exception {
        ObjectNode createDocRequest = objectMapper.createObjectNode();
        createDocRequest.put("name", name);
        createDocRequest.put("type", "FILE");
        createDocRequest.put("content", content);

        String resp = perform(post("/api/kb/{kbId}/documents", kbId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDocRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private long createFolder(String authHeader, long kbId, String name) throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("name", name);
        req.put("type", "folder");
        String resp = perform(post("/api/kb/{kbId}/documents", kbId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private long createDocInFolder(String authHeader, long kbId, long parentId, String name, String content)
            throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("name", name);
        req.put("type", "file");
        req.put("parentId", parentId);
        req.put("content", content);
        String resp = perform(post("/api/kb/{kbId}/documents", kbId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private long uploadToDoc(long kbId, long docId, MockMultipartFile file) throws Exception {
        String resp = perform(multipart("/api/kb/{kbId}/docs/{docId}/assets", kbId, docId)
                .file(file)
                .param("fileName", file.getOriginalFilename())
                .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private static boolean containsId(JsonNode arrayNode, long id) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return false;
        }
        for (JsonNode n : arrayNode) {
            if (n != null && n.path("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode findById(JsonNode arrayNode, long id) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return null;
        }
        for (JsonNode n : arrayNode) {
            if (n != null && n.path("id").asLong() == id) {
                return n;
            }
        }
        throw new AssertionError("id not found: " + id);
    }

    private void waitForDocRelations(String authHeader, long sourceDocId) throws Exception {
        boolean ok = false;
        for (int i = 0; i < 40; i++) {
            String linksJson = mockMvc.perform(get("/api/docs/{docId}/links", sourceDocId)
                    .header("Authorization", authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn().getResponse().getContentAsString();
            JsonNode data = objectMapper.readTree(linksJson).path("data");
            if (data.isArray() && data.size() >= 1) {
                ok = true;
                break;
            }
            Thread.sleep(50);
        }
        if (!ok) {
            throw new AssertionError("relation sync timeout");
        }

        perform(get("/api/docs/{docId}/links", sourceDocId)
                .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        perform(get("/api/docs/{docId}/graph", sourceDocId)
                .header("Authorization", authHeader)
                .param("depth", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.center.id").value((int) sourceDocId));
    }

    private long addComment(String authHeader, long docId, String content, String anchorType, String anchorData)
            throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("anchorType", anchorType);
        req.put("anchorData", anchorData);
        req.put("anchorText", "");
        req.put("content", content);
        String resp = perform(post("/api/docs/{docId}/comments", docId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private long uploadToKb(long kbId, MockMultipartFile file) throws Exception {
        String resp = perform(multipart("/api/kb/{kbId}/assets", kbId)
                .file(file)
                .param("fileName", file.getOriginalFilename())
                .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private String getAssetUrl(long assetId) throws Exception {
        String resp = perform(get("/api/assets/{id}/url", assetId)
                .header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("url").asText();
    }

    private static Map<String, String> parseQueryParams(String url) {
        URI uri = URI.create("http://localhost" + url);
        String query = uri.getRawQuery();
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isBlank()) {
            return result;
        }
        for (String pair : query.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] kv = pair.split("=", 2);
            String k = kv[0];
            String v = kv.length > 1 ? kv[1] : "";
            result.put(k, decode(v));
        }
        return result;
    }

    private static String decode(String s) {
        try {
            return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private void ensureDefaultLocalStorageConfig() {
        if (storageConfigRepository.findFirstByIsDefaultTrue().isPresent()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        KbStorageConfig cfg = new KbStorageConfig();
        cfg.setName("test-local");
        cfg.setProvider("local");
        cfg.setBucket(null);
        cfg.setRegion(null);
        cfg.setEndpoint(null);
        cfg.setAccessKeyId(null);
        cfg.setSecretKeyEncrypted(null);
        cfg.setCdnDomain(null);
        cfg.setIsDefault(true);
        cfg.setIsActive(true);
        cfg.setCreatedAt(now);
        cfg.setUpdatedAt(now);
        storageConfigRepository.save(cfg);
    }

    private long createDictType(String authHeader, String dictCode) throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("dictCode", dictCode);
        req.put("dictName", "E2E 状态");
        req.put("status", 0);
        String resp = perform(post("/api/system/dict/types")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private long createDictData(String authHeader, long typeId, String dictCode, String label, String value)
            throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("dictTypeId", typeId);
        req.put("label", label);
        req.put("value", value);
        req.put("valueType", "string");
        req.put("sortOrder", 1);
        req.put("isDefault", true);
        req.put("status", 0);
        String resp = perform(post("/api/system/dict/data")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private void createConfig(String authHeader, String key, String name, String value, boolean frontend)
            throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("configKey", key);
        req.put("configName", name);
        req.put("configValue", value);
        req.put("valueType", "string");
        req.put("configGroup", "e2e");
        req.put("isFrontend", frontend);
        req.put("isSystem", true);
        req.put("status", 0);

        perform(post("/api/system/configs")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private long createRole(String authHeader, String roleCode, String roleName) throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("roleCode", roleCode);
        req.put("roleName", roleName);
        req.put("status", 0);
        String resp = perform(post("/api/system/roles")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private long createPermission(String authHeader, String permCode, String permName) throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("permCode", permCode);
        req.put("permName", permName);
        String resp = perform(post("/api/system/permissions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private void grantPermissionToRole(String authHeader, long roleId, long permId) throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("permId", permId);
        perform(post("/api/system/permissions/roles/{roleId}", roleId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void assignRoleToUser(String authHeader, long userId, long roleId) throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("roleId", roleId);
        perform(post("/api/system/roles/users/{userId}", userId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String uploadAndGetAvatarFilename() throws Exception {
        byte[] png = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00 };
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", png);
        String resp = perform(multipart("/api/users/avatar").file(file).header("Authorization", adminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("filename").asText();
    }
}
