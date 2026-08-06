package com.nopkg.hellodoc.services.impl;

import com.nopkg.hellodoc.entities.KbDocFavorite;
import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.repositories.KbDocFavoriteRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.services.FavoriteService;
import com.nopkg.hellodoc.web.dto.ux.FavoriteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final KbDocFavoriteRepository favoriteRepository;
    private final KbDocumentRepository documentRepository;

    @Override
    @Transactional
    public void addFavorite(Long userId, Long docId) {
        if (!favoriteRepository.existsByUserIdAndDocId(userId, docId)) {
            KbDocument doc = documentRepository.findById(docId)
                    .orElseThrow(() -> new RuntimeException("Document not found"));
            KbDocFavorite favorite = new KbDocFavorite();
            favorite.setUserId(userId);
            favorite.setDoc(doc);
            favorite.setCreatedAt(OffsetDateTime.now());
            favoriteRepository.save(favorite);
        }
    }

    @Override
    @Transactional
    public void removeFavorite(Long userId, Long docId) {
        favoriteRepository.findByUserIdAndDocId(userId, docId)
                .ifPresent(favoriteRepository::delete);
    }

    @Override
    @Transactional
    public void toggleFavorite(Long userId, Long docId) {
        favoriteRepository.findByUserIdAndDocId(userId, docId)
                .ifPresentOrElse(
                        favoriteRepository::delete,
                        () -> addFavorite(userId, docId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FavoriteVO> getFavorites(Long userId) {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, Long docId) {
        return favoriteRepository.existsByUserIdAndDocId(userId, docId);
    }

    private FavoriteVO convertToVO(KbDocFavorite favorite) {
        FavoriteVO vo = new FavoriteVO();
        KbDocument doc = favorite.getDoc();
        vo.setId(doc.getId());
        vo.setKbId(doc.getKb().getId());
        vo.setKbTitle(doc.getKb().getTitle());
        vo.setName(doc.getName());
        vo.setType(doc.getType().name());
        vo.setCreatedAt(favorite.getCreatedAt());
        return vo;
    }
}
