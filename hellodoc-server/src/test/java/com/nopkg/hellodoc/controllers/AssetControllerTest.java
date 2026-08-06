package com.nopkg.hellodoc.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nopkg.hellodoc.entities.KbStorageConfig;
import com.nopkg.hellodoc.entities.KbStorageFile;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.entities.SysUserAuth;
import com.nopkg.hellodoc.repositories.KbStorageConfigRepository;
import com.nopkg.hellodoc.repositories.KbStorageFileRepository;
import com.nopkg.hellodoc.repositories.UserAuthRepository;
import com.nopkg.hellodoc.repositories.UserRepository;
import com.nopkg.hellodoc.services.StorageFileService;
import com.nopkg.hellodoc.storage.StorageUrlSigner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.cache.type=none",
        "spring.sql.init.mode=never",
        "file.upload-dir=build/test-uploads-assets",
        "storage.orphan.grace-hours=0",
        "storage.url-expiration-seconds=3600"
})
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class AssetControllerTest {

    private static final Path UPLOAD_DIR = Paths.get("build", "test-uploads-assets");

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
    private KbStorageConfigRepository storageConfigRepository;

    @Autowired
    private KbStorageFileRepository storageFileRepository;

    @Autowired
    private StorageFileService storageFileService;

    @Autowired
    private StorageUrlSigner signer;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        ensureDefaultLocalStorageConfig();

        SysUser liyc = new SysUser();
        liyc.setNickname("liyc");
        liyc.setStatus((short) 0);
        liyc.setCreateTime(Instant.now());
        liyc.setUpdateTime(Instant.now());
        liyc = userRepository.save(liyc);

        SysUserAuth auth = new SysUserAuth();
        auth.setUser(liyc);
        auth.setIdentityType("PASSWORD");
        auth.setIdentifier("liyc_asset_test");
        auth.setCredential(passwordEncoder.encode("11111"));
        auth.setStatus((short) 0);
        auth.setVerified(true);
        auth.setCreateTime(Instant.now());
        userAuthRepository.save(auth);

        authHeader = "Bearer " + loginAndGetAccessToken("liyc_asset_test", "11111");
    }

    @AfterEach
    void tearDown() throws Exception {
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

    @Test
    @DisplayName("附件：上传/去重/获取URL/公开访问/删除/清理孤立文件")
    void asset_full_workflow_shouldSucceed() throws Exception {
        long kbId = createKb();
        long docId = createDoc(kbId);

        byte[] bytes = "hello-storage".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", MediaType.TEXT_PLAIN_VALUE, bytes);

        UploadResult kbUpload = uploadToKb(kbId, file);
        long asset1Id = kbUpload.assetId();
        long storageFileId1 = kbUpload.storageFileId();

        UploadResult docUpload = uploadToDoc(kbId, docId, file);
        long asset2Id = docUpload.assetId();
        long storageFileId2 = docUpload.storageFileId();

        org.junit.jupiter.api.Assertions.assertEquals(storageFileId1, storageFileId2);
        org.junit.jupiter.api.Assertions.assertEquals(1, storageFileRepository.count());

        KbStorageFile storageFile = storageFileRepository.findById(storageFileId1).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(2, (storageFile.getRefCount() == null) ? 0 : storageFile.getRefCount());

        mockMvc.perform(get("/api/kb/{kbId}/assets", kbId).header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/docs/{docId}/assets", docId).header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        String url1 = getAssetUrl(asset1Id);
        String url2 = getAssetUrl(asset1Id);
        org.junit.jupiter.api.Assertions.assertEquals(url1, url2);

        Map<String, String> query = parseQueryParams(url1);
        mockMvc.perform(get("/api/storage/public")
                        .param("key", query.get("key"))
                        .param("exp", query.get("exp"))
                        .param("sig", query.get("sig")))
                .andExpect(status().isOk());

        byte[] body = mockMvc.perform(get("/api/storage/public")
                        .param("key", query.get("key"))
                        .param("exp", query.get("exp"))
                        .param("sig", query.get("sig"))
                        .param("filename", "hello.txt"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String cd = result.getResponse().getHeader(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION);
                    org.junit.jupiter.api.Assertions.assertNotNull(cd);
                    org.junit.jupiter.api.Assertions.assertTrue(cd.contains("filename*=UTF-8''hello.txt"));
                })
                .andReturn().getResponse().getContentAsByteArray();
        org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, body);

        mockMvc.perform(delete("/api/assets/{id}", asset1Id).header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        KbStorageFile afterFirstDelete = storageFileRepository.findById(storageFileId1).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(1, (afterFirstDelete.getRefCount() == null) ? 0 : afterFirstDelete.getRefCount());
        org.junit.jupiter.api.Assertions.assertNull(afterFirstDelete.getDeletedAt());

        mockMvc.perform(delete("/api/assets/{id}", asset1Id).header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004));

        mockMvc.perform(delete("/api/assets/{id}", asset2Id).header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        KbStorageFile afterAllDelete = storageFileRepository.findById(storageFileId1).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(0, (afterAllDelete.getRefCount() == null) ? 0 : afterAllDelete.getRefCount());
        org.junit.jupiter.api.Assertions.assertNotNull(afterAllDelete.getDeletedAt());
    }

    @Test
    @DisplayName("附件：最后引用删除后触发物理清理")
    void asset_cleanupOrphanFiles_shouldDeleteDbAndFile() throws Exception {
        long kbId = createKb();

        byte[] bytes = "cleanup-me".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "cleanup.txt", MediaType.TEXT_PLAIN_VALUE, bytes);

        UploadResult upload = uploadToKb(kbId, file);
        long assetId = upload.assetId();
        long fileId = upload.storageFileId();

        KbStorageFile created = storageFileRepository.findById(fileId).orElseThrow();
        Path physical = Paths.get("build", "test-uploads-assets", "storage", normalizeKeyToPath(created.getStorageKey()));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(physical));

        mockMvc.perform(delete("/api/assets/{id}", assetId).header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        KbStorageFile marked = storageFileRepository.findById(fileId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(0, (marked.getRefCount() == null) ? 0 : marked.getRefCount());
        org.junit.jupiter.api.Assertions.assertNotNull(marked.getDeletedAt());

        marked.setDeletedAt(OffsetDateTime.now().minusHours(1));
        storageFileRepository.save(marked);

        storageFileService.cleanupOrphanFiles();

        org.junit.jupiter.api.Assertions.assertTrue(storageFileRepository.findById(fileId).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(physical));
    }

    @Test
    @DisplayName("公共访问：路径穿越 key 直接拒绝")
    void storagePublic_shouldRejectTraversalKey() throws Exception {
        long exp = Instant.now().plusSeconds(60).getEpochSecond();
        String key = "../x";
        String sig = signer.sign(key, exp);

        mockMvc.perform(get("/api/storage/public")
                        .param("key", key)
                        .param("exp", String.valueOf(exp))
                        .param("sig", sig))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1007));
    }

    private long createKb() throws Exception {
        ObjectNode createKbRequest = objectMapper.createObjectNode();
        createKbRequest.put("title", "Asset Test KB");
        createKbRequest.put("visibility", "PRIVATE");

        String kbResponse = mockMvc.perform(post("/api/kb/createKnowledgeBase")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createKbRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(kbResponse).path("data").path("id").asLong();
    }

    private long createDoc(long kbId) throws Exception {
        ObjectNode createDocRequest = objectMapper.createObjectNode();
        createDocRequest.put("name", "Asset Test Doc");
        createDocRequest.put("type", "FILE");
        createDocRequest.put("content", "doc content");

        String docResponse = mockMvc.perform(post("/api/kb/{kbId}/documents", kbId)
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDocRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(docResponse).path("data").path("id").asLong();
    }

    private UploadResult uploadToKb(long kbId, MockMultipartFile file) throws Exception {
        String resp = mockMvc.perform(multipart("/api/kb/{kbId}/assets", kbId)
                        .file(file)
                        .param("fileName", "hello.txt")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(resp);
        long assetId = root.path("data").path("id").asLong();
        long storageFileId = root.path("data").path("storageFileId").asLong();
        return new UploadResult(assetId, storageFileId);
    }

    private UploadResult uploadToDoc(long kbId, long docId, MockMultipartFile file) throws Exception {
        String resp = mockMvc.perform(multipart("/api/kb/{kbId}/docs/{docId}/assets", kbId, docId)
                        .file(file)
                        .param("fileName", "hello.txt")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(resp);
        long assetId = root.path("data").path("id").asLong();
        long storageFileId = root.path("data").path("storageFileId").asLong();
        return new UploadResult(assetId, storageFileId);
    }

    private String getAssetUrl(long assetId) throws Exception {
        String resp = mockMvc.perform(get("/api/assets/{id}/url", assetId)
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("url").asText();
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

    private void ensureDefaultLocalStorageConfig() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
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
            storageConfigRepository.saveAndFlush(cfg);
        });
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

    private static String normalizeKeyToPath(String key) {
        String k = key == null ? "" : key.trim();
        String normalized = k.startsWith("/") ? k.substring(1) : k;
        return normalized;
    }

    private record UploadResult(long assetId, long storageFileId) {
    }
}
