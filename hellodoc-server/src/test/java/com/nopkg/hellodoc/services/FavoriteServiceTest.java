package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.KbDocFavorite;
import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.repositories.KbDocFavoriteRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.services.impl.FavoriteServiceImpl;
import com.nopkg.hellodoc.web.dto.ux.FavoriteVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FavoriteServiceTest {

    @Mock
    private KbDocFavoriteRepository favoriteRepository;
    @Mock
    private KbDocumentRepository documentRepository;

    @InjectMocks
    private FavoriteServiceImpl favoriteService;

    private Long userId = 1L;
    private Long docId = 100L;
    private KbDocument mockDoc;

    @BeforeEach
    void setUp() {
        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setId(10L);
        kb.setTitle("Test KB");

        mockDoc = new KbDocument();
        mockDoc.setId(docId);
        mockDoc.setName("Test Doc");
        mockDoc.setType(DocType.FILE);
        mockDoc.setKb(kb);
    }

    @Test
    void toggleFavorite_ShouldAdd_WhenNotExists() {
        when(favoriteRepository.findByUserIdAndDocId(userId, docId)).thenReturn(Optional.empty());
        when(favoriteRepository.existsByUserIdAndDocId(userId, docId)).thenReturn(false);
        when(documentRepository.findById(docId)).thenReturn(Optional.of(mockDoc));

        favoriteService.toggleFavorite(userId, docId);

        verify(favoriteRepository).save(any(KbDocFavorite.class));
    }

    @Test
    void toggleFavorite_ShouldRemove_WhenExists() {
        KbDocFavorite favorite = new KbDocFavorite();
        when(favoriteRepository.findByUserIdAndDocId(userId, docId)).thenReturn(Optional.of(favorite));

        favoriteService.toggleFavorite(userId, docId);

        verify(favoriteRepository).delete(favorite);
    }

    @Test
    void getFavorites_ShouldReturnVOList() {
        KbDocFavorite favorite = new KbDocFavorite();
        favorite.setDoc(mockDoc);
        favorite.setCreatedAt(OffsetDateTime.now());

        when(favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(favorite));

        List<FavoriteVO> result = favoriteService.getFavorites(userId);

        assertEquals(1, result.size());
        assertEquals(docId, result.get(0).getId());
    }

    @Test
    void isFavorite_ShouldReturnTrue_WhenExists() {
        when(favoriteRepository.existsByUserIdAndDocId(userId, docId)).thenReturn(true);
        assertTrue(favoriteService.isFavorite(userId, docId));
    }
}
