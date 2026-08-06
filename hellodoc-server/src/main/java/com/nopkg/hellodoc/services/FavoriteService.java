package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.web.dto.ux.FavoriteVO;
import java.util.List;

public interface FavoriteService {
    void addFavorite(Long userId, Long docId);

    void removeFavorite(Long userId, Long docId);

    void toggleFavorite(Long userId, Long docId);

    List<FavoriteVO> getFavorites(Long userId);

    boolean isFavorite(Long userId, Long docId);
}
