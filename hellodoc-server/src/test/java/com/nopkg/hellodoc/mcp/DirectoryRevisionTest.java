package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.repositories.KbKnowledgeBaseRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectoryRevisionTest {
    @Test void lockAndBumpARevisionOnTheLockedKnowledgeBase() {
        var repository = mock(KbKnowledgeBaseRepository.class);
        var kb = new KbKnowledgeBase();
        kb.setId(8L);
        kb.setTreeRevision(4L);
        when(repository.findByIdForTreeUpdate(8L)).thenReturn(Optional.of(kb));
        var revisions = new DirectoryRevision(repository);
        var entityManager = mock(jakarta.persistence.EntityManager.class);
        org.springframework.test.util.ReflectionTestUtils.setField(revisions, "entityManager", entityManager);
        assertSame(kb, revisions.lock(8L));
        verify(entityManager).refresh(kb, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        assertEquals("5", revisions.bump(kb));
        assertEquals(5L, kb.getTreeRevision());
    }
}
