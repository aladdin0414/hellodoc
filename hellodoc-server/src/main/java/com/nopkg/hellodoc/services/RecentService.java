package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.web.dto.ux.RecentDocVO;
import java.util.List;

public interface RecentService {
    void recordVisit(Long userId, Long docId);

    List<RecentDocVO> getRecentDocuments(Long userId, int limit);

    void clearRecentHistory(Long userId);
}
