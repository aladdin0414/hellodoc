package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.KbStorageFile;
import com.nopkg.hellodoc.repositories.KbStorageFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DbService {
    private final KbStorageFileRepository storageFileRepository;

    /**
     * 在独立事务中保存，如果失败则回滚该短事务，不影响调用方事务
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KbStorageFile saveStorageFileInNewTransaction(KbStorageFile file) {
        return storageFileRepository.saveAndFlush(file);
    }
}
