CREATE TABLE IF NOT EXISTS ai_conversation (
    user_id BIGINT NOT NULL,
    doc_id BIGINT NOT NULL,
    messages TEXT NOT NULL,
    PRIMARY KEY (user_id, doc_id)
);
