package com.nopkg.hellodoc.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "HELLODOC_TEST_PG_URL", matches = ".+")
class McpWriteGuardPostgresTest {
    @Test
    void serializesConcurrentReplaysAndRejectsDifferentInput() throws Exception {
        String url = System.getenv("HELLODOC_TEST_PG_URL");
        String schema = "mcp_test_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", ""));
        admin.execute("CREATE SCHEMA " + schema);
        var ds = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, "postgres", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE kb_document (id BIGSERIAL PRIMARY KEY, kb_id BIGINT NOT NULL, author_id BIGINT NOT NULL, extra_meta JSONB, deleted_at TIMESTAMPTZ)");
        var guard = new McpWriteGuard(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)));
        AtomicInteger writes = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return guard.execute(7, 9, "document", "request-1", "hash-1", () -> {
                    writes.incrementAndGet();
                    Long id = jdbc.queryForObject("INSERT INTO kb_document (kb_id, author_id, extra_meta) VALUES (9, 7, '{\"hellodoc_mcp_request\":{\"type\":\"document\",\"id\":\"request-1\",\"hash\":\"hash-1\"}}'::jsonb) RETURNING id", Long.class);
                    return id;
                });
            })).toList();
            start.countDown();
            long first = tasks.get(0).get(10, TimeUnit.SECONDS);
            assertEquals(first, tasks.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(1, writes.get());
            assertThrows(IllegalArgumentException.class, () -> guard.execute(7, 9, "document", "request-1", "different", () -> 2L));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM kb_document", Integer.class));
            assertThrows(IllegalStateException.class, () -> guard.execute(7, 9, "document", "bad-write", "hash", () -> {
                jdbc.update("INSERT INTO kb_document (kb_id, author_id) VALUES (9, 7)");
                return 123L;
            }));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM kb_document", Integer.class));
        } finally {
            pool.shutdownNow();
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
