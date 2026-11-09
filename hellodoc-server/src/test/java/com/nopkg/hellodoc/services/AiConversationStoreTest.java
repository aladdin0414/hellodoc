package com.nopkg.hellodoc.services;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class AiConversationStoreTest {
    @Test void savesAndSeparatesConversationsByUserAndDocument() {
        var db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:ai_conversation_test;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("CREATE TABLE ai_conversation (user_id BIGINT NOT NULL, doc_id BIGINT NOT NULL, messages TEXT NOT NULL, PRIMARY KEY(user_id, doc_id))");
        var store = new AiConversationStore(db);
        assertEquals("[]", store.load(1L, 1L));
        store.save(1L, 1L, "[1]");
        store.save(1L, 1L, "[2]");
        assertEquals("[2]", store.load(1L, 1L));
        assertEquals("[]", store.load(2L, 1L));
        assertEquals("[]", store.load(1L, 2L));
        store.save(1L, 1L, "[]");
        assertEquals("[]", store.load(1L, 1L));
    }
}
