package com.nopkg.hellodoc.services.impl;

import com.nopkg.hellodoc.components.MentionParser;
import com.nopkg.hellodoc.entities.DocComment;
import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.enums.NotifyType;
import com.nopkg.hellodoc.repositories.DocCommentRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.repositories.UserRepository;
import com.nopkg.hellodoc.services.CommentService;
import com.nopkg.hellodoc.services.NotificationService;
import com.nopkg.hellodoc.web.dto.comment.CommentCreateDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.web.ApiResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final DocCommentRepository commentRepository;
    private final NotificationService notificationService;
    private final MentionParser mentionParser;
    private final UserRepository userRepository;
    private final KbDocumentRepository documentRepository;

    @Override
    @Transactional
    public DocComment addComment(CommentCreateDTO dto, Long userId) {
        DocComment comment = new DocComment();
        comment.setDocId(dto.getDocId());
        comment.setUserId(userId);
        comment.setParentId(dto.getParentId());
        comment.setAnchorType(dto.getAnchorType());
        comment.setAnchorData(dto.getAnchorData());
        comment.setAnchorText(dto.getAnchorText());
        comment.setContent(dto.getContent());

        DocComment saved = commentRepository.save(comment);

        // 如果是回复，则通知父评论作者
        if (dto.getParentId() != null) {
            commentRepository.findById(dto.getParentId()).ifPresent(parent -> {
                if (!parent.getUserId().equals(userId)) {
                    notificationService.createNotification(
                            templateFor(NotifyType.REPLY),
                            contextFor(NotifyType.REPLY, parent.getUserId(), userId, dto.getDocId(), saved.getId(),
                                    dto.getContent()));
                }
            });
        }

        // 处理 @提及 (mentions)
        List<Long> mentionedUserIds = mentionParser.parseMentions(dto.getContent());
        for (Long mentionedUserId : mentionedUserIds) {
            if (!mentionedUserId.equals(userId)) {
                notificationService.createNotification(
                        templateFor(NotifyType.MENTION),
                        contextFor(NotifyType.MENTION, mentionedUserId, userId, dto.getDocId(), saved.getId(),
                                dto.getContent()));
            }
        }

        // 重新获取以填充用户信息
        return commentRepository.findById(saved.getId()).orElse(saved);
    }

    @Override
    @Transactional
    public DocComment replyComment(Long parentId, String content, Long userId) {
        DocComment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new EntityNotFoundException("Parent comment not found"));

        DocComment reply = new DocComment();
        reply.setDocId(parent.getDocId());
        reply.setUserId(userId);
        reply.setParentId(parentId);
        reply.setContent(content);
        // 锚点信息是从父评论继承还是为空？通常回复没有新的锚点，它们属于同一个线程。
        // 如果我们想支持嵌套锚点，需要更多字段。目前假定基于线程的回复。

        DocComment saved = commentRepository.save(reply);

        // 通知父评论作者
        if (!parent.getUserId().equals(userId)) {
            notificationService.createNotification(
                    templateFor(NotifyType.REPLY),
                    contextFor(NotifyType.REPLY, parent.getUserId(), userId, parent.getDocId(), saved.getId(),
                            content));
        }

        // 处理 @提及 (mentions)
        List<Long> mentionedUserIds = mentionParser.parseMentions(content);
        for (Long mentionedUserId : mentionedUserIds) {
            if (!mentionedUserId.equals(userId)) {
                notificationService.createNotification(
                        templateFor(NotifyType.MENTION),
                        contextFor(NotifyType.MENTION, mentionedUserId, userId, parent.getDocId(), saved.getId(),
                                content));
            }
        }

        // 重新获取以填充用户信息
        return commentRepository.findById(saved.getId()).orElse(saved);
    }

    @Override
    @Transactional
    public DocComment updateComment(Long id, String content, Long userId) {
        DocComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));

        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ApiResponse.Code.NO_PERMISSION);
        }

        comment.setContent(content);
        return commentRepository.save(comment);
    }

    @Override
    @Transactional
    public void deleteComment(Long id, Long userId) {
        DocComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));

        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ApiResponse.Code.NO_PERMISSION);
        }

        comment.setDeletedAt(LocalDateTime.now());
        commentRepository.save(comment);
    }

    @Override
    @Transactional
    public DocComment resolveComment(Long id, Long userId) {
        DocComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));

        comment.setIsResolved(true);
        comment.setResolvedBy(userId);
        comment.setResolvedAt(LocalDateTime.now());
        DocComment saved = commentRepository.save(comment);

        // 通知作者
        if (!comment.getUserId().equals(userId)) {
            notificationService.createNotification(
                    templateFor(NotifyType.RESOLVE),
                    contextFor(NotifyType.RESOLVE, comment.getUserId(), userId, comment.getDocId(), comment.getId(),
                            "已解决评论"));
        }
        return saved;
    }

    @Override
    @Transactional
    public DocComment unresolveComment(Long id, Long userId) {
        DocComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));

        comment.setIsResolved(false);
        comment.setResolvedBy(null);
        comment.setResolvedAt(null);
        return commentRepository.save(comment);
    }

    @Override
    public List<DocComment> getDocumentComments(Long docId) {
        return commentRepository.findByDocIdAndDeletedAtIsNull(docId);
    }

    @Override
    public Integer getUnresolvedCount(Long docId) {
        return commentRepository.countByDocIdAndParentIdIsNullAndIsResolvedFalseAndDeletedAtIsNull(docId);
    }

    private NotificationService.NotificationTemplate templateFor(NotifyType type) {
        String keyPrefix = switch (type) {
            case MENTION -> "notification.mention";
            case REPLY -> "notification.reply";
            case RESOLVE -> "notification.resolve";
            default -> "notification.common";
        };
        return new NotificationService.NotificationTemplate(
                keyPrefix + ".title",
                keyPrefix + ".content",
                "IN_APP");
    }

    private NotificationService.NotificationContext contextFor(
            NotifyType type,
            Long receiverUserId,
            Long senderUserId,
            Long docId,
            Long refId,
            String content) {
        Map<String, Object> params = new HashMap<>();
        String senderName = userRepository.findById(senderUserId)
                .map(u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : "User" + senderUserId)
                .orElse("User" + senderUserId);
        String docName = documentRepository.findById(docId)
                .map(KbDocument::getName)
                .filter(StringUtils::hasText)
                .orElse("Document");
        params.put("senderName", senderName);
        params.put("docName", docName);
        if (StringUtils.hasText(content)) {
            String snippet = content.length() > 80 ? content.substring(0, 80) + "..." : content;
            params.put("commentSnippet", snippet);
        }
        return new NotificationService.NotificationContext(
                type,
                receiverUserId,
                senderUserId,
                docId,
                refId,
                params);
    }
}
