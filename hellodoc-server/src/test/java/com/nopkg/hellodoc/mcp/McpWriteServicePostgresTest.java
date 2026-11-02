package com.nopkg.hellodoc.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "HELLODOC_TEST_PG_INTEGRATION_URL", matches = ".+")
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false", "app.database.initializer.enabled=false"})
class McpWriteServicePostgresTest {
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("HELLODOC_TEST_PG_INTEGRATION_URL"));
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired McpWriteService writes;
    @Autowired com.nopkg.hellodoc.services.DocumentService documents;

    @Test
    void concurrentRequestsUseExistingDocumentServiceAndPersistOnlyOnce() throws Exception {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('mcp integration') RETURNING id", Long.class);
        String username = "mcp-integration-" + user;
        jdbc.update("INSERT INTO sys_user_auth (user_id, identity_type, identifier) VALUES (?, 'PASSWORD', ?)", user, username);
        long kb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('mcp integration', ?) RETURNING id", Long.class, user);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return withAuth(username, () -> writes.createItem(user, kb, "事项", "说明", List.of("别名"), "item-id")); });
            var b = pool.submit(() -> { start.await(); return withAuth(username, () -> writes.createItem(user, kb, "事项", "说明", List.of("别名"), "item-id")); });
            start.countDown();
            long item = a.get(20, TimeUnit.SECONDS);
            assertEquals(item, b.get(20, TimeUnit.SECONDS));
            var start2 = new CountDownLatch(1);
            var c = pool.submit(() -> { start2.await(); return withAuth(username, () -> writes.createDocument(user, kb, item, "标题", "# 内容", "doc-id")); });
            var d = pool.submit(() -> { start2.await(); return withAuth(username, () -> writes.createDocument(user, kb, item, "标题", "# 内容", "doc-id")); });
            start2.countDown();
            long doc = c.get(20, TimeUnit.SECONDS);
            assertEquals(doc, d.get(20, TimeUnit.SECONDS));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM kb_document WHERE kb_id = ?", Integer.class, kb));
            assertEquals("# 内容", documents.getContent(doc));
            assertTrue(jdbc.queryForObject("SELECT count(*) FROM kb_document_revision WHERE doc_id = ?", Integer.class, doc) > 0);
            assertThrows(IllegalArgumentException.class, () -> withAuth(username, () -> writes.createDocument(user, kb, item, "不同", "# 内容", "doc-id")));
            var nameStart = new CountDownLatch(1);
            var x = pool.submit(() -> { nameStart.await(); return withAuth(username, () -> writes.createItem(user, kb, "另一事项", "说明", List.of(), "name-a")); });
            var y = pool.submit(() -> { nameStart.await(); return withAuth(username, () -> writes.createItem(user, kb, "另一事项", "说明", List.of(), "name-b")); });
            nameStart.countDown();
            int succeeded = 0;
            for (var f : List.of(x, y)) {
                try { f.get(20, TimeUnit.SECONDS); succeeded++; }
                catch (java.util.concurrent.ExecutionException e) { assertInstanceOf(IllegalArgumentException.class, e.getCause()); }
            }
            assertEquals(1, succeeded);
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM kb_document WHERE kb_id = ? AND name = '另一事项'", Integer.class, kb));
        } finally {
            pool.shutdownNow();
        }
    }
    private static long withAuth(String username, java.util.concurrent.Callable<Long> action) throws Exception {
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username, null, List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try { return action.call(); } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
}
