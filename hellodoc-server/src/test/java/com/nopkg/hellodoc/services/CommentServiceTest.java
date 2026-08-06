package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.components.MentionParser;
import com.nopkg.hellodoc.entities.DocComment;
import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.enums.AnchorType;
import com.nopkg.hellodoc.enums.NotifyType;
import com.nopkg.hellodoc.repositories.DocCommentRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.repositories.UserRepository;
import com.nopkg.hellodoc.services.impl.CommentServiceImpl;
import com.nopkg.hellodoc.web.dto.comment.CommentCreateDTO;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private DocCommentRepository commentRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MentionParser mentionParser;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KbDocumentRepository documentRepository;

    @InjectMocks
    private CommentServiceImpl commentService;

    @Test
    void addComment_ShouldSaveAndNotifyMentions() {
        Long userId = 1L;
        CommentCreateDTO dto = new CommentCreateDTO();
        dto.setDocId(10L);
        dto.setContent("Hello @alice");
        dto.setAnchorType(AnchorType.BLOCK);

        DocComment savedComment = new DocComment();
        savedComment.setId(100L);
        savedComment.setUserId(userId);

        when(commentRepository.save(any(DocComment.class))).thenReturn(savedComment);
        when(mentionParser.parseMentions(dto.getContent())).thenReturn(List.of(2L));
        SysUser sender = new SysUser();
        sender.setId(userId);
        sender.setNickname("alice");
        when(userRepository.findById(userId)).thenReturn(Optional.of(sender));
        KbDocument doc = new KbDocument();
        doc.setId(10L);
        doc.setName("Doc 10");
        when(documentRepository.findById(10L)).thenReturn(Optional.of(doc));

        DocComment result = commentService.addComment(dto, userId);

        assertEquals(100L, result.getId());
        verify(commentRepository).save(any(DocComment.class));
        verify(notificationService).createNotification(
                argThat(template -> template != null
                        && "notification.mention.title".equals(template.titleKey())
                        && "notification.mention.content".equals(template.contentKey())),
                argThat(context -> context != null
                        && context.notifyType() == NotifyType.MENTION
                        && Long.valueOf(2L).equals(context.receiverUserId())
                        && userId.equals(context.senderUserId())
                        && Long.valueOf(10L).equals(context.docId())
                        && Long.valueOf(100L).equals(context.refId())));
    }

    @Test
    void replyComment_ShouldNotifyParentAuthorAndMentions() {
        Long userId = 1L;
        Long parentId = 50L;
        Long parentAuthorId = 2L;
        Long mentionedUserId = 3L;
        String content = "Reply @bob";

        DocComment parent = new DocComment();
        parent.setId(parentId);
        parent.setUserId(parentAuthorId);
        parent.setDocId(10L);

        DocComment savedReply = new DocComment();
        savedReply.setId(101L);
        savedReply.setUserId(userId);

        when(commentRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(DocComment.class))).thenReturn(savedReply);
        when(mentionParser.parseMentions(content)).thenReturn(List.of(mentionedUserId));
        SysUser sender = new SysUser();
        sender.setId(userId);
        sender.setNickname("alice");
        when(userRepository.findById(userId)).thenReturn(Optional.of(sender));
        KbDocument doc = new KbDocument();
        doc.setId(10L);
        doc.setName("Doc 10");
        when(documentRepository.findById(10L)).thenReturn(Optional.of(doc));

        DocComment result = commentService.replyComment(parentId, content, userId);

        verify(notificationService).createNotification(
                argThat(template -> template != null
                        && "notification.reply.title".equals(template.titleKey())
                        && "notification.reply.content".equals(template.contentKey())),
                argThat(context -> context != null
                        && context.notifyType() == NotifyType.REPLY
                        && parentAuthorId.equals(context.receiverUserId())
                        && userId.equals(context.senderUserId())
                        && Long.valueOf(10L).equals(context.docId())
                        && Long.valueOf(101L).equals(context.refId())));

        verify(notificationService).createNotification(
                argThat(template -> template != null
                        && "notification.mention.title".equals(template.titleKey())
                        && "notification.mention.content".equals(template.contentKey())),
                argThat(context -> context != null
                        && context.notifyType() == NotifyType.MENTION
                        && mentionedUserId.equals(context.receiverUserId())
                        && userId.equals(context.senderUserId())
                        && Long.valueOf(10L).equals(context.docId())
                        && Long.valueOf(101L).equals(context.refId())));
    }

    @Test
    void testUpdateComment_OnlyOwnerCanEdit() {
        Long ownerId = 1L;
        Long otherId = 99L;
        Long commentId = 50L;
        String newContent = "Updated content";

        DocComment comment = new DocComment();
        comment.setId(commentId);
        comment.setUserId(ownerId);
        comment.setContent("Original content");

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(DocComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Owner can edit
        DocComment updated = commentService.updateComment(commentId, newContent, ownerId);
        assertEquals(newContent, updated.getContent());

        // Other user should fail
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            commentService.updateComment(commentId, newContent, otherId);
        });
        assertEquals(ApiResponse.Code.NO_PERMISSION, exception.getCode());
    }

    @Test
    void testDeleteComment_OnlyOwnerCanDelete() {
        Long ownerId = 1L;
        Long otherId = 99L;
        Long commentId = 50L;

        DocComment comment = new DocComment();
        comment.setId(commentId);
        comment.setUserId(ownerId);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        // Owner can delete
        commentService.deleteComment(commentId, ownerId);
        verify(commentRepository).save(any(DocComment.class));

        // Reset for next test
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        // Other user should fail
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            commentService.deleteComment(commentId, otherId);
        });
        assertEquals(ApiResponse.Code.NO_PERMISSION, exception.getCode());
    }

    @Test
    void testResolveComment_ShouldSetResolvedFields() {
        Long userId = 1L;
        Long commentId = 50L;

        DocComment comment = new DocComment();
        comment.setId(commentId);
        comment.setIsResolved(false);
        comment.setUserId(2L); // Different user to avoid notification to self

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(DocComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocComment result = commentService.resolveComment(commentId, userId);

        // Verify that save was called with comment that has resolved fields set
        assertNotNull(result);
        assertTrue(Boolean.TRUE.equals(result.getIsResolved()));
        assertEquals(userId, result.getResolvedBy());
        assertNotNull(result.getResolvedAt());
    }
}
