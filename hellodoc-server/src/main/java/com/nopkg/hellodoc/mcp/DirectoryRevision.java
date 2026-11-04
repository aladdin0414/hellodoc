package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.repositories.KbKnowledgeBaseRepository;
import org.springframework.stereotype.Service;

@Service
public class DirectoryRevision {
    private final KbKnowledgeBaseRepository repository;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    public DirectoryRevision(KbKnowledgeBaseRepository repository) { this.repository = repository; }

    public KbKnowledgeBase lock(long knowledgeBaseId) {
        var kb = repository.findByIdForTreeUpdate(knowledgeBaseId)
                .orElseThrow(() -> new IllegalStateException("DIRECTORY_UNAVAILABLE"));
        entityManager.flush();
        entityManager.refresh(kb, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return kb;
    }

    public String bump(KbKnowledgeBase knowledgeBase) {
        long revision = Math.addExact(knowledgeBase.getTreeRevision(), 1L);
        knowledgeBase.setTreeRevision(revision);
        return Long.toString(revision);
    }
}
