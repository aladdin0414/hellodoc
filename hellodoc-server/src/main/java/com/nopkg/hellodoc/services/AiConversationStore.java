package com.nopkg.hellodoc.services;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiConversationStore {
    private final JdbcTemplate jdbc;
    public AiConversationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public String load(Long userId, Long docId) {
        return jdbc.query("SELECT messages FROM ai_conversation WHERE user_id = ? AND doc_id = ?",
                rs -> rs.next() ? rs.getString(1) : "[]", userId, docId);
    }
    @Transactional
    public void save(Long userId, Long docId, String messages) {
        if (jdbc.update("UPDATE ai_conversation SET messages = ? WHERE user_id = ? AND doc_id = ?", messages, userId, docId) == 0) {
            jdbc.update("INSERT INTO ai_conversation (user_id, doc_id, messages) VALUES (?, ?, ?)", userId, docId, messages);
        }
    }
}
