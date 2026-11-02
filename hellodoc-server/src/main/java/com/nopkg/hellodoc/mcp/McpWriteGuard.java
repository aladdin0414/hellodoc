package com.nopkg.hellodoc.mcp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.LongSupplier;

/** Serializes MCP writes per knowledge base and recognizes recorded request IDs. */
@Component
public class McpWriteGuard {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    @org.springframework.beans.factory.annotation.Autowired
    public McpWriteGuard(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this(jdbc, new TransactionTemplate(manager));
    }

    McpWriteGuard(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public long execute(long userId, long kbId, String operation, String requestId, String hash, LongSupplier create) {
        if (userId <= 0 || kbId <= 0 || operation == null || operation.isBlank()
                || requestId == null || requestId.isBlank() || requestId.length() > 128
                || hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Invalid operation identity");
        }
        Long result = transactions.execute(status -> {
            jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> null, kbId ^ 0x48444d4350000000L);
            List<Previous> previous = jdbc.query("""
                    SELECT id, extra_meta->'hellodoc_mcp_request'->>'hash' AS request_hash, deleted_at
                    FROM kb_document WHERE kb_id = ? AND author_id = ?
                    AND extra_meta->'hellodoc_mcp_request'->>'type' = ?
                    AND extra_meta->'hellodoc_mcp_request'->>'id' = ? ORDER BY id LIMIT 2
                    """, (rs, row) -> new Previous(rs.getLong("id"), rs.getString("request_hash"), rs.getTimestamp("deleted_at") != null),
                    kbId, userId, operation, requestId);
            if (previous.size() > 1) throw new IllegalStateException("Request ID is ambiguous");
            if (!previous.isEmpty()) {
                Previous item = previous.get(0);
                if (!hash.equals(item.hash())) throw new IllegalArgumentException("Request ID was already used for different input");
                if (item.deleted()) throw new IllegalStateException("Previously created document was deleted");
                return item.id();
            }
            long id = create.getAsLong();
            Integer count = jdbc.queryForObject("""
                    SELECT count(*) FROM kb_document WHERE id = ? AND kb_id = ? AND author_id = ?
                    AND extra_meta->'hellodoc_mcp_request'->>'type' = ?
                    AND extra_meta->'hellodoc_mcp_request'->>'id' = ?
                    AND extra_meta->'hellodoc_mcp_request'->>'hash' = ?
                    """, Integer.class, id, kbId, userId, operation, requestId, hash);
            if (count == null || count != 1) throw new IllegalStateException("Created document lacks request metadata");
            return id;
        });
        if (result == null) throw new IllegalStateException("Write transaction returned no result");
        return result;
    }

    public java.util.OptionalLong find(long userId, long kbId, String operation, String requestId) {
        if (userId <= 0 || kbId <= 0 || requestId == null || requestId.isBlank() || requestId.length() > 128
                || !("item".equals(operation) || "document".equals(operation))) {
            throw new IllegalArgumentException("Invalid operation identity");
        }
        List<Previous> previous = jdbc.query("""
                SELECT id, extra_meta->'hellodoc_mcp_request'->>'hash' AS request_hash, deleted_at
                FROM kb_document WHERE kb_id = ? AND author_id = ?
                AND extra_meta->'hellodoc_mcp_request'->>'type' = ?
                AND extra_meta->'hellodoc_mcp_request'->>'id' = ? ORDER BY id LIMIT 2
                """, (rs, row) -> new Previous(rs.getLong("id"), rs.getString("request_hash"), rs.getTimestamp("deleted_at") != null),
                kbId, userId, operation, requestId);
        if (previous.size() > 1) throw new IllegalStateException("Request ID is ambiguous");
        if (previous.isEmpty()) return java.util.OptionalLong.empty();
        if (previous.get(0).deleted()) throw new IllegalStateException("Previously created document was deleted");
        return java.util.OptionalLong.of(previous.get(0).id());
    }

    private record Previous(long id, String hash, boolean deleted) {}
}
