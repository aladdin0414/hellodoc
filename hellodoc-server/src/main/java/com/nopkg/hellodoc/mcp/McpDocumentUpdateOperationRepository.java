package com.nopkg.hellodoc.mcp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface McpDocumentUpdateOperationRepository extends JpaRepository<McpDocumentUpdateOperation, Long> {
    Optional<McpDocumentUpdateOperation> findByUserIdAndDocument_IdAndRequestId(Long userId, Long documentId, String requestId);
}
