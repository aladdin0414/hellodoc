package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.Visibility;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRevisionRepository;
import com.nopkg.hellodoc.repositories.KbKbMemberRepository;
import com.nopkg.hellodoc.repositories.KbKbUserPrefRepository;
import com.nopkg.hellodoc.repositories.KbKnowledgeBaseRepository;
import com.nopkg.hellodoc.repositories.UserRepository;
import com.nopkg.hellodoc.web.dto.kb.KbCreateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class KbServiceTest {

    @Mock
    private KbKnowledgeBaseRepository kbRepository;
    @Mock
    private KbDocumentRepository documentRepository;
    @Mock
    private KbDocumentRevisionRepository revisionRepository;
    @Mock
    private KbKbMemberRepository memberRepository;
    @Mock
    private KbKbUserPrefRepository prefRepository;
    @Mock
    private UserService userService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private KbService kbService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testCreateKnowledgeBase() {
        Long userId = 1L;
        KbCreateDTO dto = new KbCreateDTO();
        dto.setTitle("Test KB");
        dto.setVisibility(Visibility.PRIVATE);

        when(kbRepository.save(any(KbKnowledgeBase.class))).thenAnswer(i -> i.getArgument(0));

        KbKnowledgeBase kb = kbService.createKnowledgeBase(userId, dto);

        assertNotNull(kb);
        assertEquals("Test KB", kb.getTitle());
        assertEquals(userId, kb.getOwnerId());
        assertEquals(Visibility.PRIVATE, kb.getVisibility());
        verify(kbRepository, times(1)).save(any(KbKnowledgeBase.class));
    }

    @Test
    public void testGetKnowledgeBase() {
        Long kbId = 1L;
        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setId(kbId);
        kb.setTitle("Test KB");

        when(kbRepository.findById(kbId)).thenReturn(Optional.of(kb));

        KbKnowledgeBase result = kbService.getKnowledgeBase(kbId);

        assertNotNull(result);
        assertEquals("Test KB", result.getTitle());
    }

    @Test
    public void testSoftDeleteKnowledgeBase() {
        Long userId = 1L;
        Long kbId = 1L;
        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setId(kbId);
        kb.setOwnerId(userId);

        when(kbRepository.findById(kbId)).thenReturn(Optional.of(kb));

        kbService.softDeleteKnowledgeBase(userId, kbId);

        assertNotNull(kb.getDeletedAt());
        verify(kbRepository, times(1)).save(kb);
    }
}
