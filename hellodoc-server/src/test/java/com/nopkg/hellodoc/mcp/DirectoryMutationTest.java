package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.repositories.*;
import com.nopkg.hellodoc.services.*;
import com.nopkg.hellodoc.services.impl.DocumentServiceImpl;
import com.nopkg.hellodoc.web.dto.kb.DocCreateDTO;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DirectoryMutationTest {
    @Test void copyingAFolderChecksEachChildPermission() {
        var repository = mock(KbDocumentRepository.class);
        var kbService = mock(KbService.class);
        var revision = mock(DirectoryRevision.class);
        var permissions = mock(PermissionChecker.class);
        var sourceKb = new KbKnowledgeBase(); sourceKb.setId(10L);
        var targetKb = new KbKnowledgeBase(); targetKb.setId(9L);
        var folder = new KbDocument(); folder.setId(1L); folder.setKb(sourceKb); folder.setType(DocType.FOLDER); folder.setName("folder");
        var child = new KbDocument(); child.setId(2L); child.setKb(sourceKb); child.setType(DocType.FILE); child.setName("private");
        when(kbService.getKnowledgeBase(9L)).thenReturn(targetKb);
        when(revision.lock(9)).thenReturn(targetKb); when(revision.lock(10)).thenReturn(sourceKb);
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(java.util.Optional.of(folder));
        when(repository.findByKbIdAndParentIdAndDeletedAtIsNull(10L, 1L)).thenReturn(new java.util.ArrayList<>(java.util.List.of(child)));
        when(repository.save(any(KbDocument.class))).thenAnswer(call -> { KbDocument d = call.getArgument(0); d.setId(3L); return d; });
        doThrow(new IllegalStateException("denied")).when(permissions).checkDocRole(7L, 2L, com.nopkg.hellodoc.enums.DocRole.VIEWER);
        var service = new DocumentServiceImpl(repository, mock(KbDocumentContentRepository.class), kbService,
            permissions, mock(RevisionService.class), mock(SearchService.class), mock(ApplicationEventPublisher.class),
            mock(KbDocRelationRepository.class), mock(PasswordEncoder.class), mock(AssetService.class), revision);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> service.copyToKb(7L, 1L, 9L));
        verify(permissions).checkDocRole(7L, 2L, com.nopkg.hellodoc.enums.DocRole.VIEWER);
        verify(revision, never()).bump(targetKb);
    }
    @Test void movingUnderAFileIsRejected() {
        var repository = mock(KbDocumentRepository.class); var kbService = mock(KbService.class);
        var revision = mock(DirectoryRevision.class); var kb = new KbKnowledgeBase(); kb.setId(9L);
        var doc = new KbDocument(); doc.setId(1L); doc.setKb(kb); doc.setType(DocType.FILE);
        var target = new KbDocument(); target.setId(2L); target.setKb(kb); target.setType(DocType.FILE);
        when(revision.lock(9)).thenReturn(kb); when(kbService.getKnowledgeBase(9L)).thenReturn(kb);
        when(repository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(doc));
        when(repository.findByIdAndDeletedAtIsNull(2L)).thenReturn(java.util.Optional.of(target));
        var service = new DocumentServiceImpl(repository, mock(KbDocumentContentRepository.class), kbService,
            mock(PermissionChecker.class), mock(RevisionService.class), mock(SearchService.class),
            mock(ApplicationEventPublisher.class), mock(KbDocRelationRepository.class), mock(PasswordEncoder.class), mock(AssetService.class), revision);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "entityManager", mock(jakarta.persistence.EntityManager.class));
        var dto = new com.nopkg.hellodoc.web.dto.kb.DocUpdateDTO(); dto.setParentId(2L);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> service.update(7L, 9L, 1L, dto));
        verify(repository, never()).save(any(KbDocument.class));
    }
    @Test void creationCannotPlaceAChildInAnotherKnowledgeBase() {
        var repository = mock(KbDocumentRepository.class);
        var kbService = mock(KbService.class);
        var revision = mock(DirectoryRevision.class);
        var kb = new KbKnowledgeBase(); kb.setId(9L);
        var other = new KbKnowledgeBase(); other.setId(10L);
        var folder = new KbDocument(); folder.setId(5L); folder.setKb(other); folder.setType(DocType.FOLDER);
        when(revision.lock(9)).thenReturn(kb); when(kbService.getKnowledgeBase(9L)).thenReturn(kb);
        when(repository.findByIdAndDeletedAtIsNull(5L)).thenReturn(java.util.Optional.of(folder));
        var service = new DocumentServiceImpl(repository, mock(KbDocumentContentRepository.class), kbService,
            mock(PermissionChecker.class), mock(RevisionService.class), mock(SearchService.class),
            mock(ApplicationEventPublisher.class), mock(KbDocRelationRepository.class), mock(PasswordEncoder.class),
            mock(AssetService.class), revision);
        var dto = new DocCreateDTO(); dto.setName("child"); dto.setType(DocType.FILE); dto.setParentId(5L);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> service.create(7L, 9L, dto));
        verify(repository, never()).save(any(KbDocument.class));
    }
    @Test void permanentDeletionOfAnActiveDocumentInvalidatesTheSnapshot() {
        var repository = mock(KbDocumentRepository.class);
        var revision = mock(DirectoryRevision.class);
        var kb = new KbKnowledgeBase(); kb.setId(9L);
        var doc = new KbDocument(); doc.setId(1L); doc.setKb(kb);
        when(revision.lock(9)).thenReturn(kb);
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(doc));
        var service = new DocumentServiceImpl(repository, mock(KbDocumentContentRepository.class), mock(KbService.class),
            mock(PermissionChecker.class), mock(RevisionService.class), mock(SearchService.class),
            mock(ApplicationEventPublisher.class), mock(KbDocRelationRepository.class), mock(PasswordEncoder.class),
            mock(AssetService.class), revision);
        service.permanentlyDelete(7L, 9L, 1L);
        verify(revision).lock(9); verify(revision).bump(kb);
    }
    @Test void copyingAcrossKnowledgeBasesLocksBothInIdOrder() {
        var repository = mock(KbDocumentRepository.class);
        var kbService = mock(KbService.class);
        var revision = mock(DirectoryRevision.class);
        var sourceKb = new KbKnowledgeBase(); sourceKb.setId(10L); sourceKb.setTitle("source");
        var targetKb = new KbKnowledgeBase(); targetKb.setId(9L);
        var doc = new KbDocument(); doc.setId(1L); doc.setKb(sourceKb); doc.setType(DocType.FILE); doc.setName("test");
        when(kbService.getKnowledgeBase(9L)).thenReturn(targetKb);
        when(revision.lock(9)).thenReturn(targetKb); when(revision.lock(10)).thenReturn(sourceKb);
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(java.util.Optional.of(doc));
        when(repository.save(any(KbDocument.class))).thenAnswer(call -> { KbDocument d = call.getArgument(0); d.setId(2L); return d; });
        var service = new DocumentServiceImpl(repository, mock(KbDocumentContentRepository.class), kbService,
            mock(PermissionChecker.class), mock(RevisionService.class), mock(SearchService.class),
            mock(ApplicationEventPublisher.class), mock(KbDocRelationRepository.class), mock(PasswordEncoder.class),
            mock(AssetService.class), revision);
        service.copyToKb(7L, 1L, 9L);
        var order = inOrder(revision);
        order.verify(revision).lock(9); order.verify(revision).lock(10); order.verify(revision).bump(targetKb);
    }
    @Test void duplicatingAChildChangesTheLockedDirectoryRevision() {
        var repository = mock(KbDocumentRepository.class);
        var kbService = mock(KbService.class);
        var revision = mock(DirectoryRevision.class);
        var kb = new KbKnowledgeBase(); kb.setId(9L);
        var doc = new KbDocument(); doc.setId(1L); doc.setKb(kb); doc.setType(DocType.FILE); doc.setName("test");
        when(kbService.getKnowledgeBase(9L)).thenReturn(kb);
        when(revision.lock(9)).thenReturn(kb);
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(java.util.Optional.of(doc));
        when(repository.save(any(KbDocument.class))).thenAnswer(call -> { KbDocument d = call.getArgument(0); d.setId(2L); return d; });
        var service = new DocumentServiceImpl(repository, mock(KbDocumentContentRepository.class), kbService,
            mock(PermissionChecker.class), mock(RevisionService.class), mock(SearchService.class),
            mock(ApplicationEventPublisher.class), mock(KbDocRelationRepository.class), mock(PasswordEncoder.class),
            mock(AssetService.class), revision);
        service.duplicate(7L, 9L, 1L);
        verify(revision).lock(9);
        verify(revision).bump(kb);
    }
    @Test void restoringAChildChangesTheLockedDirectoryRevision() {
        var repository = mock(KbDocumentRepository.class);
        var kbService = mock(KbService.class);
        var revision = mock(DirectoryRevision.class);
        var kb = new KbKnowledgeBase(); kb.setId(9L);
        var doc = new KbDocument(); doc.setId(1L); doc.setKb(kb); doc.setDeletedAt(java.time.OffsetDateTime.now());
        when(revision.lock(9)).thenReturn(kb);
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(doc));
        var service = new DocumentServiceImpl(repository, mock(KbDocumentContentRepository.class), kbService,
            mock(PermissionChecker.class), mock(RevisionService.class), mock(SearchService.class),
            mock(ApplicationEventPublisher.class), mock(KbDocRelationRepository.class), mock(PasswordEncoder.class),
            mock(AssetService.class), revision);
        service.restore(7L, 9L, 1L);
        var order = inOrder(revision, repository);
        order.verify(revision).lock(9);
        order.verify(repository, atLeastOnce()).findById(1L);
        order.verify(revision).bump(kb);
    }
    @Test void deletingAChildChangesTheLockedDirectoryRevision() {
        var repository = mock(KbDocumentRepository.class);
        var kbService = mock(KbService.class);
        var revision = mock(DirectoryRevision.class);
        var kb = new KbKnowledgeBase(); kb.setId(9L);
        var doc = new KbDocument(); doc.setId(1L); doc.setKb(kb);
        when(kbService.getKnowledgeBase(9L)).thenReturn(kb);
        when(revision.lock(9)).thenReturn(kb);
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(java.util.Optional.of(doc));
        var service = new DocumentServiceImpl(repository, mock(KbDocumentContentRepository.class), kbService,
            mock(PermissionChecker.class), mock(RevisionService.class), mock(SearchService.class),
            mock(ApplicationEventPublisher.class), mock(KbDocRelationRepository.class), mock(PasswordEncoder.class),
            mock(AssetService.class), revision);
        service.delete(7L, 9L, 1L);
        var order = inOrder(revision, repository);
        order.verify(revision).lock(9);
        order.verify(repository).save(doc);
        order.verify(revision).bump(kb);
    }
    @Test void changingAnOrderChangesTheLockedDirectoryRevision() {
        var repository = mock(KbDocumentRepository.class);
        var kbService = mock(KbService.class);
        var revision = mock(DirectoryRevision.class);
        var kb = new KbKnowledgeBase(); kb.setId(9L); kb.setTreeRevision(0L);
        var doc = new KbDocument(); doc.setId(1L); doc.setKb(kb); doc.setOrderNum(10);
        when(kbService.getKnowledgeBase(9L)).thenReturn(kb);
        when(revision.lock(9)).thenReturn(kb);
        when(repository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(doc));
        when(repository.save(any(KbDocument.class))).thenAnswer(call -> call.getArgument(0));
        var service = new DocumentServiceImpl(repository, mock(KbDocumentContentRepository.class), kbService,
            mock(PermissionChecker.class), mock(RevisionService.class), mock(SearchService.class),
            mock(ApplicationEventPublisher.class), mock(KbDocRelationRepository.class), mock(PasswordEncoder.class),
            mock(AssetService.class), revision);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "entityManager", mock(jakarta.persistence.EntityManager.class));
        var dto = new com.nopkg.hellodoc.web.dto.kb.DocUpdateDTO(); dto.setOrderNum(20);
        service.update(7L, 9L, 1L, dto);
        var order = inOrder(revision, repository);
        order.verify(revision).lock(9);
        order.verify(repository).findByIdForUpdate(1L);
        order.verify(revision).bump(kb);
    }
    @Test void creatingAChildChangesTheLockedDirectoryRevision() {
        var repository = mock(KbDocumentRepository.class);
        var kbService = mock(KbService.class);
        var revision = mock(DirectoryRevision.class);
        var kb = new KbKnowledgeBase(); kb.setId(9L); kb.setTreeRevision(0L);
        when(kbService.getKnowledgeBase(9L)).thenReturn(kb);
        when(revision.lock(9)).thenReturn(kb);
        when(repository.save(any(KbDocument.class))).thenAnswer(call -> { KbDocument d = call.getArgument(0); d.setId(1L); return d; });
        var service = new DocumentServiceImpl(repository, mock(KbDocumentContentRepository.class), kbService,
            mock(PermissionChecker.class), mock(RevisionService.class), mock(SearchService.class),
            mock(ApplicationEventPublisher.class), mock(KbDocRelationRepository.class), mock(PasswordEncoder.class),
            mock(AssetService.class), revision);
        var dto = new DocCreateDTO(); dto.setName("child"); dto.setType(DocType.FILE);
        service.create(7L, 9L, dto);
        var order = inOrder(revision, repository);
        order.verify(revision).lock(9);
        order.verify(repository).save(any(KbDocument.class));
        order.verify(revision).bump(kb);
    }
}
