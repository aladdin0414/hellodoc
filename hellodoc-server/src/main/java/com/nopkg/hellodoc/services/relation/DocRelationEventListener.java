package com.nopkg.hellodoc.services.relation;

import com.nopkg.hellodoc.services.DocRelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocRelationEventListener {

    private final DocRelationService docRelationService;

    @Async("relationSyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDocumentSaved(DocumentSavedEvent event) {
        try {
            docRelationService.syncRelationsFromContent(event.docId(), event.content());
        } catch (Exception e) {
            log.error("Failed to sync doc relations for docId: {}", event.docId(), e);
        }
    }
}
