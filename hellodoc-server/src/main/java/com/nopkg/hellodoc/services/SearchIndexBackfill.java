package com.nopkg.hellodoc.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.util.Locale;

/** Builds optional indexes and incrementally upgrades old search records without blocking application startup. */
@Component
@ConditionalOnProperty(name = "app.search.backfill.enabled", havingValue = "true", matchIfMissing = true)
public class SearchIndexBackfill {
    private static final Logger log = LoggerFactory.getLogger(SearchIndexBackfill.class);
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final SearchService search;
    private final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean();

    public SearchIndexBackfill(JdbcTemplate jdbc, DataSource dataSource, SearchService search) {
        this.jdbc = jdbc; this.dataSource = dataSource; this.search = search;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${app.search.backfill.interval-ms:300000}", initialDelayString = "${app.search.backfill.interval-ms:300000}")
    public void backfill() {
        if (!running.compareAndSet(false, true)) return;
        try { runBackfill(); } finally { running.set(false); }
    }

    private void runBackfill() {
        try (var connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null || !product.toLowerCase(Locale.ROOT).contains("postgres")) return;
        } catch (Exception e) { log.warn("Search backfill could not detect database"); return; }
        try {
            jdbc.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            jdbc.execute("CREATE INDEX CONCURRENTLY IF NOT EXISTS kb_doc_search_content_trgm_idx ON kb_doc_search USING gin (lower(content_plain) gin_trgm_ops)");
            jdbc.execute("CREATE INDEX CONCURRENTLY IF NOT EXISTS kb_document_name_trgm_idx ON kb_document USING gin (lower(name) gin_trgm_ops)");
        } catch (Exception e) {
            log.warn("Optional search index unavailable; correct unindexed search remains available");
        }
        long lastId = 0;
        while (true) {
            java.util.List<Long> ids;
            try {
                ids = jdbc.queryForList("SELECT d.id FROM kb_document d LEFT JOIN kb_doc_search s ON s.doc_id = d.id "
                        + "WHERE d.id > ? AND d.deleted_at IS NULL AND coalesce(d.is_encrypted,false) = false "
                        + "AND (s.source_updated_at IS NULL OR s.content_plain IS NULL OR s.source_updated_at < d.updated_at) "
                        + "ORDER BY d.id LIMIT 100", Long.class, lastId);
            } catch (Exception e) { log.warn("Search backfill stopped; fallback remains available"); return; }
            if (ids.isEmpty()) return;
            for (long id : ids) {
                lastId = id;
                try { search.updateIndexSynchronously(id); }
                catch (Exception e) { log.warn("Search backfill skipped a document"); }
            }
        }
    }
}
