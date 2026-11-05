package com.nopkg.hellodoc.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.entities.SysUserAuth;
import com.nopkg.hellodoc.repositories.UserRepository;
import com.nopkg.hellodoc.repositories.UserAuthRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealApiFullE2ETest {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired UserAuthRepository auths;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired com.nopkg.hellodoc.services.DocLockService locks;
    @Autowired com.nopkg.hellodoc.services.ConfigService configs;
    private final RestTemplate http = new RestTemplate();

    @Test void realHttpRegistrationDocumentVersionAndRevisionFlow() throws Exception {
        SysUser admin = new SysUser();
        admin.setNickname("e2e_admin"); admin.setStatus((short) 0);
        admin.setCreateTime(Instant.now()); admin.setUpdateTime(Instant.now());
        admin = users.save(admin);
        SysUserAuth auth = new SysUserAuth();
        auth.setUser(admin); auth.setIdentityType("PASSWORD"); auth.setIdentifier("e2e_admin");
        auth.setCredential(encoder.encode("e2e-test-password")); auth.setStatus((short) 0);
        auth.setVerified(true); auth.setCreateTime(Instant.now()); auths.save(auth);
        assertFalse(login("e2e_admin", "e2e-test-password").isBlank());
        JsonNode config = request(HttpMethod.GET, "/api/public/configs/frontend", null, null);
        assertEquals(0, config.path("code").asInt());
        String username = "e2e_" + System.nanoTime();
        JsonNode registration = request(HttpMethod.POST, "/api/auth/register", null,
                Map.of("username", username, "password", "111111", "nickname", "E2E User"));
        assertEquals(0, registration.path("code").asInt(), registration.toString());
        String token = login(username, "111111");
        JsonNode kb = request(HttpMethod.POST, "/api/kb/createKnowledgeBase", token,
                Map.of("title", "E2E KB", "visibility", "private"));
        assertEquals(0, kb.path("code").asInt(), kb.toString());
        long kbId = kb.path("data").path("id").asLong();
        JsonNode created = request(HttpMethod.POST, "/api/kb/" + kbId + "/documents", token,
                Map.of("name", "E2E Doc", "type", "file", "content", "Initial Content"));
        assertEquals(0, created.path("code").asInt(), created.toString());
        long docId = created.path("data").path("id").asLong();
        var collabConfig = new com.nopkg.hellodoc.entities.SysConfig();
        collabConfig.setConfigKey("app.collab.enabled"); collabConfig.setConfigName("collaboration");
        collabConfig.setConfigValue("true"); collabConfig.setIsFrontend(true); collabConfig.setIsSystem(false); collabConfig.setStatus((short) 0);
        configs.createConfig(collabConfig);
        assertThrows(java.util.concurrent.CompletionException.class, () ->
                java.net.http.HttpClient.newHttpClient().newWebSocketBuilder()
                        .buildAsync(java.net.URI.create("ws://127.0.0.1:" + port + "/ws/doc/" + docId),
                                new java.net.http.WebSocket.Listener() {}).join());
        var sync = new java.util.concurrent.CompletableFuture<String>();
        var socket = java.net.http.HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(java.net.URI.create("ws://127.0.0.1:" + port + "/ws/doc/" + docId + "?token=" + token),
                        new java.net.http.WebSocket.Listener() {
                            @Override public java.util.concurrent.CompletionStage<?> onText(java.net.http.WebSocket ws, CharSequence data, boolean last) {
                                sync.complete(data.toString()); ws.request(1); return null;
                            }
                        }).join();
        assertTrue(sync.get(3, java.util.concurrent.TimeUnit.SECONDS).contains("\"sync\""));
        socket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "done").join();
        int version = created.path("data").path("version").asInt();
        String doc = "/api/kb/" + kbId + "/documents/" + docId;
        long userId = auths.findByIdentifierAndIdentityType(username, "PASSWORD").orElseThrow().getUser().getId();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var first = pool.submit(() -> { start.await(); return locks.acquireLock(docId, userId, "one", com.nopkg.hellodoc.enums.LockType.DOCUMENT, null); });
        var second = pool.submit(() -> { start.await(); return locks.acquireLock(docId, userId, "two", com.nopkg.hellodoc.enums.LockType.DOCUMENT, null); });
        start.countDown();
        boolean a = first.get(); boolean b = second.get(); pool.shutdown();
        assertNotEquals(a, b, "only one simultaneous lock acquisition may succeed");
        String owner = a ? "one" : "two";
        JsonNode rejected = request(HttpMethod.PUT, doc, token, Map.of("content", "Intruder", "expectedVersion", version));
        assertEquals(1016, rejected.path("code").asInt(), rejected.toString());
        JsonNode rejectedRevision = request(HttpMethod.POST, doc + "/revisions", token,
                Map.of("content", "Intruder revision", "message", "test", "expectedVersion", version));
        assertEquals(1016, rejectedRevision.path("code").asInt(), rejectedRevision.toString());
        JsonNode rejectedRestore = request(HttpMethod.POST, "/api/docs/" + docId + "/revisions/1/restore?expectedVersion=" + version, token, null);
        assertEquals(1016, rejectedRestore.path("code").asInt(), rejectedRestore.toString());
        JsonNode accepted = request(HttpMethod.PUT, doc, token, Map.of("content", "Owner", "expectedVersion", version, "collabSessionId", owner));
        assertEquals(0, accepted.path("code").asInt(), accepted.toString());
        version = accepted.path("data").path("version").asInt();
        locks.releaseLock(docId, owner, com.nopkg.hellodoc.enums.LockType.DOCUMENT, null);
        JsonNode staleLock = request(HttpMethod.PUT, doc, token, Map.of("content", "Stale owner", "expectedVersion", version, "collabSessionId", owner));
        assertEquals(1016, staleLock.path("code").asInt(), staleLock.toString());
        JsonNode missingVersion = request(HttpMethod.PUT, doc, token, Map.of("content", "Unversioned"));
        assertEquals(1015, missingVersion.path("code").asInt(), missingVersion.toString());
        JsonNode updated = request(HttpMethod.PUT, doc, token, Map.of("content", "Updated Content", "expectedVersion", version));
        assertEquals(0, updated.path("code").asInt(), updated.toString());
        int updatedVersion = updated.path("data").path("version").asInt();
        assertTrue(updatedVersion > version);
        JsonNode stale = request(HttpMethod.PUT, doc, token, Map.of("content", "Stale Content", "expectedVersion", version));
        assertEquals(1014, stale.path("code").asInt(), stale.toString());
        JsonNode revision = request(HttpMethod.POST, doc + "/revisions", token,
                Map.of("content", "Revision Content", "message", "v1.0", "expectedVersion", updatedVersion));
        assertEquals(0, revision.path("code").asInt(), revision.toString());
        JsonNode fetched = request(HttpMethod.GET, doc, token, null);
        assertEquals(0, fetched.path("code").asInt(), fetched.toString());
        assertEquals("Revision Content", fetched.path("data").path("content").asText());
        JsonNode deleted = request(HttpMethod.DELETE, "/api/kb/" + kbId, token, null);
        assertEquals(0, deleted.path("code").asInt(), deleted.toString());
    }
    private String login(String username, String password) throws Exception {
        JsonNode result = request(HttpMethod.POST, "/api/auth/login", null, Map.of("username", username, "password", password));
        assertEquals(0, result.path("code").asInt(), result.toString());
        return result.path("data").path("accessToken").asText();
    }
    private JsonNode request(HttpMethod method, String path, String token, Object body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        ResponseEntity<String> response = http.exchange("http://127.0.0.1:" + port + path, method, new HttpEntity<>(body, headers), String.class);
        return mapper.readTree(response.getBody());
    }
}
