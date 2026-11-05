package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.enums.LockType;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.repositories.DocLockRepository;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Objects;

/** Call while holding the document row's write lock. */
@Component
@RequiredArgsConstructor
public class DocumentWriteLockGuard {
    private final DocLockRepository locks;

    public void check(Long docId, Long userId, String sessionId) {
        var lock = locks.findByDocIdAndLockType(docId, LockType.DOCUMENT)
                .filter(l -> l.getExpiresAt() != null && l.getExpiresAt().isAfter(LocalDateTime.now()));
        if (lock.isEmpty()) {
            if (sessionId != null && !sessionId.isBlank()) throw new BusinessException(ApiResponse.Code.DOCUMENT_LOCKED);
            return;
        }
        if (!Objects.equals(lock.get().getUserId(), userId) || sessionId == null || !sessionId.equals(lock.get().getSessionId()))
            throw new BusinessException(ApiResponse.Code.DOCUMENT_LOCKED);
    }
}
