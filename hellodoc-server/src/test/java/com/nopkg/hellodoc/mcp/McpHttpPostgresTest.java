package com.nopkg.hellodoc.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "HELLODOC_TEST_PG_INTEGRATION_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false", "app.database.initializer.enabled=false"})
class McpHttpPostgresTest {
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("HELLODOC_TEST_PG_INTEGRATION_URL"));
        p.add("spring.datasource.username", () -> "postgres"); p.add("spring.datasource.password", () -> "");
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired com.nopkg.hellodoc.services.DocumentService documents;
    final HttpClient client = HttpClient.newHttpClient();

    @Test
    void actualMcpClientCanCreateAndRetryWithoutDuplicates() throws Exception {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('http test') RETURNING id", Long.class);
        String username = "http-mcp-" + user;
        jdbc.update("INSERT INTO sys_user_auth (user_id, identity_type, identifier, credential) VALUES (?, 'PASSWORD', ?, 'unused')", user, username);
        long kb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('http test', ?) RETURNING id", Long.class, user);
        String token = tokens.generateToken(User.withUsername(username).password("unused").authorities("ROLE_USER").build());
        var initialize = send(token, null, Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize", "params", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(), "clientInfo", Map.of("name", "test", "version", "1"))));
        assertEquals(200, initialize.statusCode(), initialize.body());
        String session = initialize.headers().firstValue("Mcp-Session-Id").orElseThrow();
        Map<String, Object> itemArgs = Map.of("knowledgeBaseId", kb, "name", "开发事项", "description", "集成测试", "requestId", "http-item");
        JsonNode first = call(token, session, "create_item", itemArgs);
        assertFalse(first.path("isError").asBoolean(), first.toString());
        long item = mapper.readTree(first.path("content").get(0).path("text").asText()).path("itemId").asLong();
        assertTrue(item > 0);
        JsonNode repeat = call(token, session, "create_item", itemArgs);
        assertEquals(item, mapper.readTree(repeat.path("content").get(0).path("text").asText()).path("itemId").asLong());
        JsonNode saved = call(token, session, "create_document", Map.of("knowledgeBaseId", kb, "itemId", item, "name", "测试总结", "markdown", "# 总结", "requestId", "http-doc"));
        assertFalse(saved.path("isError").asBoolean(), saved.toString());
        long doc = mapper.readTree(saved.path("content").get(0).path("text").asText()).path("documentId").asLong();
        assertTrue(doc > 0);
        assertTrue(mapper.readTree(saved.path("content").get(0).path("text").asText()).path("version").isInt());
        assertEquals("/kb/" + kb + "/" + doc, mapper.readTree(saved.path("content").get(0).path("text").asText()).path("path").asText());
        assertEquals("# 总结", documents.getContent(doc));
        long kb2 = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('second kb', ?) RETURNING id", Long.class, user);
        JsonNode secondItemResult = call(token, session, "create_item", Map.of("knowledgeBaseId", kb2, "name", "跨库事项", "requestId", "cross-item"));
        long secondItem = mapper.readTree(secondItemResult.path("content").get(0).path("text").asText()).path("itemId").asLong();
        JsonNode secondDocResult = call(token, session, "create_document", Map.of("knowledgeBaseId", kb2, "itemId", secondItem, "name", "跨库文档", "markdown", "这是中文知识库检索操作说明", "requestId", "cross-doc"));
        long secondDoc = mapper.readTree(secondDocResult.path("content").get(0).path("text").asText()).path("documentId").asLong();
        JsonNode searched = call(token, session, "search_documents", Map.of("query", "知识库检索"));
        JsonNode searchData = mapper.readTree(searched.path("content").get(0).path("text").asText());
        assertTrue(searchData.path("results").toString().contains("跨库文档"), searchData.toString());
        JsonNode read = call(token, session, "read_document", Map.of("documentId", secondDoc));
        JsonNode readData = mapper.readTree(read.path("content").get(0).path("text").asText());
        assertEquals("这是中文知识库检索操作说明", readData.path("content").asText());
        assertEquals("draft", readData.path("status").asText());
        int baseVersion = readData.path("version").asInt();
        Map<String, Object> updateArgs = Map.of("documentId", secondDoc, "expectedVersion", baseVersion, "mode", "append", "markdown", "追加验证", "requestId", "http-update");
        JsonNode update = call(token, session, "update_document", updateArgs);
        assertFalse(update.path("isError").asBoolean(), update.toString());
        JsonNode retry = call(token, session, "update_document", updateArgs);
        assertFalse(retry.path("isError").asBoolean(), retry.toString());
        assertEquals("这是中文知识库检索操作说明追加验证", documents.getContent(secondDoc));
        var concurrentArgs = Map.<String, Object>of("documentId", secondDoc, "expectedVersion", baseVersion + 1, "mode", "append", "markdown", "并发追加", "requestId", "concurrent-id");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            var firstConcurrent = pool.submit(() -> { start.await(); return call(token, session, "update_document", concurrentArgs); });
            var secondConcurrent = pool.submit(() -> { start.await(); return call(token, session, "update_document", concurrentArgs); });
            start.countDown();
            JsonNode a = firstConcurrent.get(20, java.util.concurrent.TimeUnit.SECONDS);
            JsonNode b = secondConcurrent.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertFalse(a.path("isError").asBoolean(), a.toString());
            assertFalse(b.path("isError").asBoolean(), b.toString());
            assertEquals(a.path("content").get(0).path("text").asText(), b.path("content").get(0).path("text").asText());
            assertEquals("这是中文知识库检索操作说明追加验证并发追加", documents.getContent(secondDoc));
        } finally { pool.shutdownNow(); }

        assertTrue(call(token, session, "update_document", Map.of("documentId", secondDoc, "expectedVersion", baseVersion, "mode", "append", "markdown", "不同", "requestId", "http-update")).path("isError").asBoolean());
        assertTrue(call(token, session, "update_document", Map.of("documentId", secondDoc, "expectedVersion", baseVersion, "mode", "replace", "markdown", "旧版", "requestId", "other-update")).path("isError").asBoolean());


        JsonNode lookup = call(token, session, "get_operation", Map.of("knowledgeBaseId", kb, "operation", "document", "requestId", "http-doc"));
        assertFalse(lookup.path("isError").asBoolean(), lookup.toString());
        assertEquals(doc, mapper.readTree(lookup.path("content").get(0).path("text").asText()).path("id").asLong());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM kb_document WHERE kb_id = ?", Integer.class, kb));
        long outsider = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('other') RETURNING id", Long.class);
        String outsiderName = "http-outsider-" + outsider;
        jdbc.update("INSERT INTO sys_user_auth (user_id, identity_type, identifier, credential) VALUES (?, 'PASSWORD', ?, 'unused')", outsider, outsiderName);
        String outsiderToken = tokens.generateToken(User.withUsername(outsiderName).password("unused").authorities("ROLE_USER").build());
        JsonNode deniedUpdate = call(outsiderToken, session, "update_document", Map.of("documentId", secondDoc, "expectedVersion", 0, "mode", "replace", "markdown", "no", "requestId", "unauthorized"));
        assertTrue(deniedUpdate.path("isError").asBoolean(), deniedUpdate.toString());
        jdbc.update("INSERT INTO kb_doc_permission (doc_id, target_type, target_id, role) VALUES (?, 'user', ?, 'viewer')", secondDoc, outsider);
        JsonNode viewerRead = call(outsiderToken, session, "read_document", Map.of("documentId", secondDoc));
        assertFalse(viewerRead.path("isError").asBoolean(), viewerRead.toString());
        JsonNode viewerUpdate = call(outsiderToken, session, "update_document", Map.of("documentId", secondDoc, "expectedVersion", 0, "mode", "replace", "markdown", "no", "requestId", "viewer"));
        assertTrue(viewerUpdate.path("isError").asBoolean(), viewerUpdate.toString());
        jdbc.update("UPDATE kb_doc_permission SET role = 'editor' WHERE doc_id = ? AND target_id = ?", secondDoc, outsider);
        JsonNode sharedRead = call(outsiderToken, session, "read_document", Map.of("documentId", secondDoc));
        assertFalse(sharedRead.path("isError").asBoolean(), sharedRead.toString());
        JsonNode sharedSearch = call(outsiderToken, session, "search_documents", Map.of("query", "知识库检索"));
        assertFalse(sharedSearch.path("isError").asBoolean(), sharedSearch.toString());
        assertTrue(mapper.readTree(sharedSearch.path("content").get(0).path("text").asText()).path("results").toString().contains("跨库文档"));

        int sharedVersion = mapper.readTree(sharedRead.path("content").get(0).path("text").asText()).path("version").asInt();
        JsonNode sharedUpdate = call(outsiderToken, session, "update_document", Map.of("documentId", secondDoc, "expectedVersion", sharedVersion, "mode", "append", "markdown", "共享编辑", "requestId", "shared-editor"));
        assertFalse(sharedUpdate.path("isError").asBoolean(), sharedUpdate.toString());
        assertTrue(documents.getContent(secondDoc).endsWith("共享编辑"));
        jdbc.update("DELETE FROM kb_doc_permission WHERE doc_id = ? AND target_id = ?", secondDoc, outsider);
        JsonNode revokedRetry = call(outsiderToken, session, "update_document", Map.of("documentId", secondDoc, "expectedVersion", sharedVersion, "mode", "append", "markdown", "共享编辑", "requestId", "shared-editor"));
        assertTrue(revokedRetry.path("isError").asBoolean(), revokedRetry.toString());

        JsonNode deniedRead = call(outsiderToken, session, "read_document", Map.of("documentId", secondDoc));
        assertTrue(deniedRead.path("isError").asBoolean(), deniedRead.toString());
        JsonNode outsiderSearch = call(outsiderToken, session, "search_documents", Map.of("query", "知识库检索"));
        assertFalse(outsiderSearch.toString().contains("跨库文档"), outsiderSearch.toString());

        JsonNode denied = call(outsiderToken, session, "create_document", Map.of("knowledgeBaseId", kb, "itemId", item, "name", "越权", "markdown", "内容", "requestId", "other"));
        assertTrue(denied.path("isError").asBoolean(), denied.toString());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM kb_document WHERE kb_id = ?", Integer.class, kb));
        JsonNode second = call(token, session, "create_item", Map.of("knowledgeBaseId", kb, "name", "第二事项", "requestId", "http-item-2"));
        assertFalse(second.path("isError").asBoolean(), second.toString());
        JsonNode page = call(token, session, "list_items", Map.of("knowledgeBaseId", kb, "limit", 1));
        JsonNode pageData = mapper.readTree(page.path("content").get(0).path("text").asText());
        assertEquals(item, pageData.path("items").get(0).path("id").asLong());
        assertEquals(1, pageData.path("nextOffset").asInt());
        JsonNode next = call(token, session, "list_items", Map.of("knowledgeBaseId", kb, "limit", 1, "offset", 1));
        JsonNode nextData = mapper.readTree(next.path("content").get(0).path("text").asText());
        assertEquals("第二事项", nextData.path("items").get(0).path("name").asText());
        assertFalse(nextData.has("nextOffset"));
    }
    @Test
    void stdioBridgeLogsInAndSavesToConfiguredKnowledgeBase() throws Exception {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('bridge test') RETURNING id", Long.class);
        String username = "bridge-" + user;
        jdbc.update("INSERT INTO sys_user_auth (user_id, identity_type, identifier, credential) VALUES (?, 'PASSWORD', ?, ?)", user, username, encoder.encode("bridge-test-password"));
        long kb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('bridge test', ?) RETURNING id", Long.class, user);
        var script = java.nio.file.Path.of("..", "hellodoc-mcp-bridge", "test", "e2e-check.mjs").toAbsolutePath().normalize();
        var process = new ProcessBuilder("node", script.toString()).redirectErrorStream(true);
        process.environment().put("HELLODOC_BASE_URL", "http://127.0.0.1:" + port);
        process.environment().put("HELLODOC_KB_ID", Long.toString(kb));
        process.environment().put("HELLODOC_USERNAME", username);
        process.environment().put("HELLODOC_PASSWORD", "bridge-test-password");
        Process running = process.start();
        assertTrue(running.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
        String output = new String(running.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, running.exitValue(), output);
        JsonNode result = mapper.readTree(output);
        assertEquals("# 桥接\n更新", documents.getContent(result.path("documentId").asLong()));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM kb_document WHERE kb_id = ?", Integer.class, kb));
    }
    private JsonNode call(String token, String session, String tool, Map<String, Object> args) throws Exception {
        var response = send(token, session, Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/call", "params", Map.of("name", tool, "arguments", args)));
        assertEquals(200, response.statusCode(), response.body());
        String body = response.body();
        if (body.lines().anyMatch(line -> line.startsWith("data:"))) {
            body = body.lines().filter(line -> line.startsWith("data:")).map(line -> line.substring(5).trim()).findFirst().orElseThrow();
        }
        JsonNode json = mapper.readTree(body);
        assertFalse(json.has("error"), json.toString());
        return json.path("result");
    }
    private HttpResponse<String> send(String token, String session, Map<String, Object> body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/mcp"))
                .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
                .header("Authorization", "Bearer " + token);
        if (session != null) builder.header("Mcp-Session-Id", session);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
}
