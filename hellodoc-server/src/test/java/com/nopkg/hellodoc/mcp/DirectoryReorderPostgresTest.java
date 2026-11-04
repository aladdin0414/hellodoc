package com.nopkg.hellodoc.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "HELLODOC_TEST_PG_INTEGRATION_URL", matches = ".+")
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false", "app.database.initializer.enabled=false"})
class DirectoryReorderPostgresTest {
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("HELLODOC_TEST_PG_INTEGRATION_URL"));
        p.add("spring.datasource.username", () -> "postgres"); p.add("spring.datasource.password", () -> "");
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired McpReorderService reorder;
    @Autowired McpReadService read;
    @Autowired com.nopkg.hellodoc.services.DocumentService documents;

    @Test void paginatesAndSortsMixedChildrenInNestedFolder() {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('nested reorder') RETURNING id", Long.class);
        long kb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('nested reorder', ?) RETURNING id", Long.class, user);
        long parent = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, 'parent', 'folder', 'draft', 1) RETURNING id", Long.class, kb);
        var ids = new java.util.ArrayList<Long>();
        for (int i = 0; i < 51; i++) {
            ids.add(jdbc.queryForObject("INSERT INTO kb_document (kb_id,parent_id,name,type,status,order_num) VALUES (?, ?, ?, ?, 'draft', ?) RETURNING id", Long.class,
                    kb, parent, "child-" + i, i % 2 == 0 ? "file" : "folder", i));
        }
        var first = read.listFolderChildren(user, kb, parent, 0, null);
        String snapshot = first.get("snapshot").toString();
        assertEquals(50, ((List<?>) first.get("children")).size());
        assertEquals(1, ((List<?>) read.listFolderChildren(user, kb, parent, 50, snapshot).get("children")).size());
        java.util.Collections.reverse(ids);
        assertEquals(true, reorder.reorder(user, kb, parent, snapshot, ids).get("changed"));
        assertEquals("ORDER_CONFLICT", assertThrows(IllegalStateException.class,
                () -> read.listFolderChildren(user, kb, parent, 50, snapshot)).getMessage());
        var result = (List<?>) read.listFolderChildren(user, kb, parent, 0, null).get("children");
        assertEquals(ids.get(0), ((java.util.Map<?, ?>) result.get(0)).get("documentId"));
    }

    @Test void concurrentCreateCannotBeLostByReorder() throws Exception {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('reorder create test') RETURNING id", Long.class);
        String username = "reorder-create-" + user;
        jdbc.update("INSERT INTO sys_user_auth (user_id, identity_type, identifier, credential) VALUES (?, 'PASSWORD', ?, 'unused')", user, username);
        long kb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('reorder create test', ?) RETURNING id", Long.class, user);
        long a = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, 'a', 'file', 'draft', 1) RETURNING id", Long.class, kb);
        long b = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, 'b', 'folder', 'draft', 2) RETURNING id", Long.class, kb);
        String snapshot = read.listFolderChildren(user, kb, null, 0).get("snapshot").toString();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var sort = pool.submit(() -> run(start, user, kb, snapshot, List.of(b, a)));
            var create = pool.submit(() -> {
                start.await();
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username, "unused"));
                try {
                    var dto = new com.nopkg.hellodoc.web.dto.kb.DocCreateDTO(); dto.setName("new child"); dto.setType(com.nopkg.hellodoc.enums.DocType.FILE);
                    return documents.create(user, kb, dto).getId();
                } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            });
            start.countDown();
            String outcome = sort.get(20, TimeUnit.SECONDS);
            assertTrue(List.of("OK", "ORDER_CONFLICT").contains(outcome), outcome);
            assertNotNull(create.get(20, TimeUnit.SECONDS));
            assertEquals(3L, jdbc.queryForObject("SELECT count(*) FROM kb_document WHERE kb_id = ? AND deleted_at IS NULL", Long.class, kb));
            assertNotEquals(snapshot, read.listFolderChildren(user, kb, null, 0).get("snapshot"));
        } finally { pool.shutdownNow(); }
    }

    @Test void simultaneousDifferentOrdersCannotOverwriteEachOther() throws Exception {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('reorder test') RETURNING id", Long.class);
        long kb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title, owner_id) VALUES ('reorder test', ?) RETURNING id", Long.class, user);
        long a = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, 'a', 'file', 'draft', 1) RETURNING id", Long.class, kb);
        long b = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, 'b', 'folder', 'draft', 2) RETURNING id", Long.class, kb);
        long c = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, 'c', 'file', 'draft', 3) RETURNING id", Long.class, kb);
        String snapshot = read.listFolderChildren(user, kb, null, 0).get("snapshot").toString();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> left = () -> run(start, user, kb, snapshot, List.of(b, a, c));
            Callable<String> right = () -> run(start, user, kb, snapshot, List.of(a, c, b));
            var one = pool.submit(left); var two = pool.submit(right); start.countDown();
            var results = List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("OK"::equals).count(), results.toString());
            assertEquals(1, results.stream().filter("ORDER_CONFLICT"::equals).count(), results.toString());
            assertEquals(3L, jdbc.queryForObject("SELECT count(*) FROM kb_document WHERE kb_id = ? AND deleted_at IS NULL", Long.class, kb));
        } finally { pool.shutdownNow(); }
    }

    private String run(CountDownLatch start, long user, long kb, String snapshot, List<Long> ids) throws Exception {
        start.await();
        try { reorder.reorder(user, kb, 0, snapshot, ids); return "OK"; }
        catch (IllegalStateException e) { return e.getMessage(); }
    }
}
