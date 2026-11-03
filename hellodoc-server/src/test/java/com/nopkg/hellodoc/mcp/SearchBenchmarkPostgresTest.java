package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.services.SearchService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "HELLODOC_TEST_PG_INTEGRATION_URL", matches = ".+")
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false", "app.database.initializer.enabled=false", "app.search.backfill.enabled=false"})
class SearchBenchmarkPostgresTest {
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("HELLODOC_TEST_PG_INTEGRATION_URL"));
        p.add("spring.datasource.username", () -> "postgres"); p.add("spring.datasource.password", () -> "");
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired SearchService search;
    @Autowired EntityManagerFactory emf;
    @Test void benchmark() {
        long user = jdbc.queryForObject("INSERT INTO sys_user (nickname) VALUES ('benchmark') RETURNING id", Long.class);
        long kb = jdbc.queryForObject("INSERT INTO kb_knowledge_base (title,owner_id) VALUES ('benchmark',?) RETURNING id", Long.class,user);
        jdbc.update("INSERT INTO kb_document (kb_id,name,type,status,order_num) SELECT ?, 'benchmark普通资料' || n, 'file', 'draft', n FROM generate_series(1,300) n", kb);
        long target = jdbc.queryForObject("INSERT INTO kb_document (kb_id,name,type,status,order_num) VALUES (?, 'benchmark唯一检索词', 'file', 'draft', 301) RETURNING id", Long.class,kb);
        var stats = emf.unwrap(SessionFactory.class).getStatistics(); stats.setStatisticsEnabled(true);
        search.searchAll(user,"benchmark唯一检索词",10); stats.clear();
        long started = System.nanoTime();
        var results = search.searchAll(user,"benchmark唯一检索词",10);
        System.out.println("BENCHMARK elapsed_ms=" + ((System.nanoTime()-started)/1_000_000) + " statements=" + stats.getPrepareStatementCount() + " fixture_docs=301");
        assertEquals(java.util.List.of(target),results.stream().map(x -> x.getDocId()).toList());
    }
}
