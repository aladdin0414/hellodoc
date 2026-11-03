package com.nopkg.hellodoc.mcp;

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
class DocumentWriteConcurrencyPostgresTest {
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("HELLODOC_TEST_PG_INTEGRATION_URL"));
        p.add("spring.datasource.username", () -> "postgres"); p.add("spring.datasource.password", () -> "");
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper mapper;
    @Autowired com.nopkg.hellodoc.services.SearchService search;
    @Autowired McpUpdateService updates;
    @Autowired McpReadService reads;
    @Autowired com.nopkg.hellodoc.services.DocumentService documentService;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Test void staleHttpEditCannotOverwriteNewBody() throws Exception {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('version test') RETURNING id", Long.class);
        String username = "version-test-" + user;
        jdbc.update("INSERT INTO sys_user_auth (user_id, identity_type, identifier, credential) VALUES (?, 'PASSWORD', ?, 'unused')", user, username);
        long kb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('version test', ?) RETURNING id", Long.class, user);
        long doc = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, 'version doc', 'file', 'draft', 0) RETURNING id", Long.class, kb);
        jdbc.update("INSERT INTO kb_document_content (doc_id) VALUES (?)", doc);
        int version = jdbc.queryForObject("SELECT coalesce(current_version,0) FROM kb_document WHERE id = ?", Integer.class, doc);
        String token = tokens.generateToken(User.withUsername(username).password("unused").authorities("ROLE_USER").build());
        var client = HttpClient.newHttpClient();
        var uri = URI.create("http://127.0.0.1:" + port + "/api/kb/" + kb + "/documents/" + doc);
        var first = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("content", "new", "expectedVersion", version)))).build();
        var stale = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("content", "stale", "expectedVersion", version)))).build();
        var firstBody = mapper.readTree(client.send(first, HttpResponse.BodyHandlers.ofString()).body());
        assertEquals(0, firstBody.path("code").asInt(), firstBody.toString());
        var secondBody = mapper.readTree(client.send(stale, HttpResponse.BodyHandlers.ofString()).body());
        assertNotEquals(0, secondBody.path("code").asInt(), "a stale editor must receive a conflict");
        assertEquals("new", jdbc.queryForObject("SELECT convert_from(lo_get(content), 'UTF8') FROM kb_document_content WHERE doc_id = ?", String.class, doc));
        var restoreUri = URI.create("http://127.0.0.1:" + port + "/api/docs/" + doc + "/revisions/1/restore");
        var legacyRestore = HttpRequest.newBuilder(restoreUri).header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        var restoreBody = mapper.readTree(client.send(legacyRestore, HttpResponse.BodyHandlers.ofString()).body());
        assertNotEquals(0, restoreBody.path("code").asInt(), "restore without an expected version must be rejected");
        var legacyManual = HttpRequest.newBuilder(URI.create(uri + "/revisions")).header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("content", "manual stale", "message", "test")))).build();
        var manualBody = mapper.readTree(client.send(legacyManual, HttpResponse.BodyHandlers.ofString()).body());
        assertNotEquals(0, manualBody.path("code").asInt(), "manual revision without version must be rejected");
        var legacy = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("content", "legacy")))).build();
        var legacyBody = mapper.readTree(client.send(legacy, HttpResponse.BodyHandlers.ofString()).body());
        assertNotEquals(0, legacyBody.path("code").asInt(), "legacy content writes must be rejected");
        assertEquals("new", jdbc.queryForObject("SELECT convert_from(lo_get(content), 'UTF8') FROM kb_document_content WHERE doc_id = ?", String.class, doc));
        int updatedVersion = jdbc.queryForObject("SELECT current_version FROM kb_document WHERE id = ?", Integer.class, doc);
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> submit = () -> {
                start.await();
                var request = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                        .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("content", Thread.currentThread().getName(), "expectedVersion", updatedVersion)))).build();
                return mapper.readTree(client.send(request, HttpResponse.BodyHandlers.ofString()).body()).path("code").asInt();
            };
            var one = pool.submit(submit); var two = pool.submit(submit); start.countDown();
            int a = one.get(20, java.util.concurrent.TimeUnit.SECONDS), b = two.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue((a == 0) ^ (b == 0), "exactly one concurrent writer should succeed; codes=" + a + "," + b);
        } finally { pool.shutdownNow(); }
        int currentVersion = jdbc.queryForObject("SELECT current_version FROM kb_document WHERE id = ?", Integer.class, doc);
        var restore = HttpRequest.newBuilder(URI.create(restoreUri + "?expectedVersion=" + currentVersion)).header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        var restoredBody = mapper.readTree(client.send(restore, HttpResponse.BodyHandlers.ofString()).body());
        assertEquals(0, restoredBody.path("code").asInt(), restoredBody.toString());
        assertEquals("new", jdbc.queryForObject("SELECT convert_from(lo_get(content), 'UTF8') FROM kb_document_content WHERE doc_id = ?", String.class, doc));


    }

    @Test void titleSubstringOutranksBodyFullTextAndFiltersUnauthorized() {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('search owner') RETURNING id", Long.class);
        long other = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('search outsider') RETURNING id", Long.class);
        long kb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('search own', ?) RETURNING id", Long.class, user);
        long secretKb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('search secret', ?) RETURNING id", Long.class, other);
        long title = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, '部署知识库检索手册', 'file', 'draft', 0) RETURNING id", Long.class, kb);
        long body = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, '说明', 'file', 'draft', 0) RETURNING id", Long.class, kb);
        long secret = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, '知识库检索', 'file', 'draft', 0) RETURNING id", Long.class, secretKb);
        jdbc.update("INSERT INTO kb_document_content (doc_id, content) VALUES (?, lo_from_bytea(0, convert_to(?, 'UTF8')))", title, "无关正文");
        jdbc.update("INSERT INTO kb_document_content (doc_id, content) VALUES (?, lo_from_bytea(0, convert_to(?, 'UTF8')))", body, "知识库检索");
        jdbc.update("INSERT INTO kb_doc_search (doc_id,kb_id,title_tsv,content_tsv) VALUES (?,?,to_tsvector('simple',?),to_tsvector('simple',?))", title, kb, "部署知识库检索手册", "无关正文");
        jdbc.update("INSERT INTO kb_doc_search (doc_id,kb_id,title_tsv,content_tsv) VALUES (?,?,to_tsvector('simple',?),to_tsvector('simple',?))", body, kb, "说明", "知识库检索");
        jdbc.update("INSERT INTO kb_doc_search (doc_id,kb_id,title_tsv,content_tsv) VALUES (?,?,to_tsvector('simple',?),to_tsvector('simple',?))", secret, secretKb, "知识库检索", "");
        long started = System.nanoTime();
        var result = search.searchAll(user, "知识库检索", 10);
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        System.out.println("SEARCH_BASELINE_MS=" + elapsed + " docs=3");
        assertEquals(java.util.List.of(title, body), result.stream().map(x -> x.getDocId()).toList());
        assertEquals(java.util.List.of(title), search.searchAll(user, "知识库检索", 1).stream().map(x -> x.getDocId()).toList());
    }

    @Test void benchmarkKeywordSearchOnRepresentativeFixture() {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('benchmark') RETURNING id", Long.class);
        long kb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('benchmark', ?) RETURNING id", Long.class, user);
        jdbc.update("INSERT INTO kb_document (kb_id,name,type,status,order_num) SELECT ?, 'benchmark普通资料' || n, 'file', 'draft', n FROM generate_series(1,300) n", kb);
        long target = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, 'benchmark唯一检索词', 'file', 'draft', 301) RETURNING id", Long.class, kb);
        search.searchAll(user, "benchmark唯一检索词", 10);
        long started = System.nanoTime();
        var result = search.searchAll(user, "benchmark唯一检索词", 10);
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        System.out.println("SEARCH_BENCHMARK_MS=" + elapsed + " fixture_docs=301 keyword=benchmark唯一检索词");
        assertEquals(java.util.List.of(target), result.stream().map(x -> x.getDocId()).toList());
    }

    @Test void searchSkipsUnauthorizedBatchesAndUsesFreshBodyWhenIndexStale() {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('batch owner') RETURNING id", Long.class);
        long other = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('batch other') RETURNING id", Long.class);
        long kb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('batch own', ?) RETURNING id", Long.class, user);
        long secretKb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('batch secret', ?) RETURNING id", Long.class, other);
        jdbc.update("INSERT INTO kb_document (kb_id,name,type,status,order_num) SELECT ?, '排名相同关键词', 'file', 'draft', n FROM generate_series(1,70) n", secretKb);
        long visible = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, '排名相同关键词', 'file', 'draft', 0) RETURNING id", Long.class, kb);
        assertEquals(java.util.List.of(visible), search.searchAll(user, "排名相同关键词", 1).stream().map(x -> x.getDocId()).toList());
        long stale = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num,updated_at) VALUES (?, '过期索引案例', 'file', 'draft', 0, now()) RETURNING id", Long.class, kb);
        jdbc.update("INSERT INTO kb_document_content (doc_id, content) VALUES (?, lo_from_bytea(0, convert_to(?, 'UTF8')))", stale, "新正文独有关键词");
        jdbc.update("INSERT INTO kb_doc_search (doc_id,kb_id,title_tsv,content_tsv,content_plain,source_updated_at) VALUES (?,?,to_tsvector('simple',?),to_tsvector('simple',?),?,TIMESTAMPTZ '2000-01-01 00:00:00+00')", stale, kb, "过期索引案例", "旧正文独有关键词", "旧正文独有关键词");
        assertTrue(search.searchAll(user, "新正文独有关键词", 10).stream().anyMatch(x -> x.getDocId().equals(stale)));
        assertTrue(search.searchAll(user, "旧正文独有关键词", 10).stream().noneMatch(x -> x.getDocId().equals(stale)));
        search.updateIndexSynchronously(stale);
        assertEquals("新正文独有关键词", jdbc.queryForObject("SELECT content_plain FROM kb_doc_search WHERE doc_id = ?", String.class, stale));
        assertTrue(search.searchAll(user, "新正文独有关键词", 10).stream().anyMatch(x -> x.getDocId().equals(stale)));

        long percent = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, '实际100%完成', 'file', 'draft', 0) RETURNING id", Long.class, kb);
        jdbc.update("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, '实际100x完成', 'file', 'draft', 0)", kb);
        assertEquals(java.util.List.of(percent), search.searchAll(user, "100%", 10).stream().map(x -> x.getDocId()).toList());
    }

    @Test void mcpAndWebConcurrentEditsCannotBothSucceed() throws Exception {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('mcp race') RETURNING id", Long.class);
        String username = "mcp-race-" + user;
        jdbc.update("INSERT INTO sys_user_auth (user_id, identity_type, identifier, credential) VALUES (?, 'PASSWORD', ?, 'unused')", user, username);
        long kb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('mcp race', ?) RETURNING id", Long.class, user);
        long doc = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, 'race', 'file', 'draft', 0) RETURNING id", Long.class, kb);
        jdbc.update("INSERT INTO kb_document_content (doc_id) VALUES (?)", doc);
        String token = tokens.generateToken(User.withUsername(username).password("unused").authorities("ROLE_USER").build());
        var uri = URI.create("http://127.0.0.1:" + port + "/api/kb/" + kb + "/documents/" + doc);
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var web = pool.submit(() -> { start.await(); var request = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("content", "web", "expectedVersion", 0)))).build();
                return mapper.readTree(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body()).path("code").asInt() == 0; });
            var mcp = pool.submit(() -> { start.await();
                var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username, null, java.util.List.of()));
                org.springframework.security.core.context.SecurityContextHolder.setContext(context);
                try { updates.update(user, doc, 0, "replace", "mcp", "race-once"); return true; }
                catch (McpUpdateException conflict) { assertEquals("VERSION_CONFLICT", conflict.getMessage()); return false; }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            });
            start.countDown();
            boolean webOk = web.get(20, java.util.concurrent.TimeUnit.SECONDS), mcpOk = mcp.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(webOk ^ mcpOk, "exactly one of the web and MCP updates must succeed");
            assertEquals(webOk ? "web" : "mcp", jdbc.queryForObject("SELECT convert_from(lo_get(content), 'UTF8') FROM kb_document_content WHERE doc_id = ?", String.class, doc));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM kb_document_revision WHERE doc_id = ?", Integer.class, doc));
        } finally { pool.shutdownNow(); }
    }

    @Test void crossKnowledgeBaseCopyExposesItsInitialRevisionAsCurrentVersion() {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('copy version') RETURNING id", Long.class);
        String username = "copy-version-" + user;
        jdbc.update("INSERT INTO sys_user_auth (user_id, identity_type, identifier, credential) VALUES (?, 'PASSWORD', ?, 'unused')", user, username);
        long sourceKb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('source', ?) RETURNING id", Long.class, user);
        long destKb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('destination', ?) RETURNING id", Long.class, user);
        long source = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, 'copy source', 'file', 'draft', 0) RETURNING id", Long.class, sourceKb);
        jdbc.update("INSERT INTO kb_document_content (doc_id,content) VALUES (?,lo_from_bytea(0,convert_to('body','UTF8')))", source);
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username, null, java.util.List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            var copy = documentService.copyToKb(user, source, destKb);
            assertEquals(1, copy.getCurrentVersion());
            assertEquals(1, jdbc.queryForObject("SELECT current_version FROM kb_document WHERE id = ?", Integer.class, copy.getId()));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Test void failedOuterTransactionRollsBackBodyRevisionVersionAndOperation() {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('rollback') RETURNING id", Long.class);
        String username = "rollback-" + user;
        jdbc.update("INSERT INTO sys_user_auth (user_id, identity_type, identifier, credential) VALUES (?, 'PASSWORD', ?, 'unused')", user, username);
        long kb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('rollback', ?) RETURNING id", Long.class, user);
        long doc = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, 'rollback', 'file', 'draft', 0) RETURNING id", Long.class, kb);
        jdbc.update("INSERT INTO kb_document_content (doc_id,content) VALUES (?,lo_from_bytea(0,convert_to('before','UTF8')))", doc);
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username, null, java.util.List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
                updates.update(user, doc, 0, "replace", "after", "rollback-request");
                throw new IllegalStateException("simulate failure after update");
            }));
            assertEquals("before", jdbc.queryForObject("SELECT convert_from(lo_get(content), 'UTF8') FROM kb_document_content WHERE doc_id = ?", String.class, doc));
            assertEquals(0, jdbc.queryForObject("SELECT coalesce(current_version,0) FROM kb_document WHERE id = ?", Integer.class, doc));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM kb_document_revision WHERE doc_id = ?", Integer.class, doc));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM mcp_document_update_operation WHERE doc_id = ?", Integer.class, doc));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Test void permanentDocumentDeletionRemovesIdempotencyRecords() {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('cascade') RETURNING id", Long.class);
        long kb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title,owner_id) VALUES ('cascade',?) RETURNING id", Long.class, user);
        long doc = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, 'cascade', 'file', 'draft', 0) RETURNING id", Long.class, kb);
        jdbc.update("INSERT INTO mcp_document_update_operation (user_id,doc_id,request_id,input_hash,result_version,result_updated_at) VALUES (?,?,?, ?,1,now())", user, doc, "id", "hash");
        jdbc.update("DELETE FROM kb_document WHERE id = ?", doc);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM mcp_document_update_operation WHERE doc_id = ?", Integer.class, doc));
    }

    @Test void paginationRejectsChangedDocumentInsteadOfMixingVersions() {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('pagination') RETURNING id", Long.class);
        String username = "pagination-" + user;
        jdbc.update("INSERT INTO sys_user_auth (user_id, identity_type, identifier, credential) VALUES (?, 'PASSWORD', ?, 'unused')", user, username);
        long kb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title,owner_id) VALUES ('pagination',?) RETURNING id", Long.class, user);
        long doc = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, 'pagination', 'file', 'draft', 0) RETURNING id", Long.class, kb);
        jdbc.update("INSERT INTO kb_document_content (doc_id,content) VALUES (?,lo_from_bytea(0,convert_to(?,'UTF8')))", doc, "a".repeat(12001));
        var first = reads.read(user, doc, 0, null);
        assertEquals(true, first.get("hasMore")); assertEquals(0, first.get("version"));
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username, null, java.util.List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try { updates.update(user, doc, 0, "replace", "b".repeat(12001), "pagination-update"); }
        finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        assertThrows(McpVersionConflictException.class, () -> reads.read(user, doc, 12000, 0));
        assertEquals("b", reads.read(user, doc, 12000, 1).get("content"));
    }
}
