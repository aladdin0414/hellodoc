package com.nopkg.hellodoc.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.entities.DocNotification;
import com.nopkg.hellodoc.i18n.LanguageContext;
import com.nopkg.hellodoc.repositories.DocNotificationRepository;
import com.nopkg.hellodoc.repositories.UserRepository;
import com.nopkg.hellodoc.services.NotificationService;
import com.nopkg.hellodoc.websocket.DocCollabHandler;
import com.nopkg.hellodoc.websocket.WsMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final DocNotificationRepository notificationRepository;
    private final DocCollabHandler docCollabHandler;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void createNotification(NotificationTemplate template, NotificationContext context) {
        Map<String, Object> params = context.params() == null ? Map.of() : context.params();
        String receiverLocale = resolveUserLocale(context.receiverUserId());
        DocNotification notification = new DocNotification();
        notification.setNotifyType(context.notifyType());
        notification.setUserId(context.receiverUserId());
        notification.setSenderId(context.senderUserId());
        notification.setDocId(context.docId());
        notification.setRefId(context.refId());
        notification.setTitleKey(template.titleKey());
        notification.setContentKey(template.contentKey());
        notification.setChannel(template.channel());
        notification.setTemplateParams(writeJson(params));
        notification.setContent(renderFallbackContent(context.notifyType(), receiverLocale, params));
        notificationRepository.save(notification);

        Map<String, Object> data = new HashMap<>();
        data.put("id", notification.getId());
        data.put("notifyType", notification.getNotifyType());
        data.put("senderId", notification.getSenderId());
        data.put("titleKey", notification.getTitleKey());
        data.put("contentKey", notification.getContentKey());
        data.put("params", params);
        data.put("content", notification.getContent());
        data.put("docId", notification.getDocId());
        data.put("createdAt", notification.getCreatedAt().toString());

        docCollabHandler.sendToUser(context.receiverUserId(), new WsMessage("notification", data));
    }

    @Override
    public Integer getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                n.setIsRead(true);
                n.setReadAt(java.time.LocalDateTime.now());
                notificationRepository.save(n);
            }
        });
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Override
    @Transactional
    public void deleteNotification(Long id, Long userId) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                notificationRepository.delete(n);
            }
        });
    }

    private String writeJson(Map<String, Object> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String resolveUserLocale(Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    String mode = user.getLanguageMode();
                    if ("AUTO".equalsIgnoreCase(mode) || !StringUtils.hasText(mode)) {
                        return LanguageContext.getLocale();
                    }
                    if ("en-US".equals(mode)) {
                        return "en-US";
                    }
                    return "zh-CN";
                })
                .orElse(LanguageContext.getLocale());
    }

    private String renderFallbackContent(com.nopkg.hellodoc.enums.NotifyType type, String locale, Map<String, Object> params) {
        String defaultUser = com.nopkg.hellodoc.utils.MessageUtils.get("notify.user", "User");
        String defaultDoc = com.nopkg.hellodoc.utils.MessageUtils.get("notify.doc", "Document");
        String senderName = toStringOrDefault(params.get("senderName"), defaultUser);
        String docName = toStringOrDefault(params.get("docName"), defaultDoc);
        String commentSnippet = toStringOrDefault(params.get("commentSnippet"), "");
        boolean enUS = "en-US".equals(locale);
        if (type == com.nopkg.hellodoc.enums.NotifyType.MENTION) {
            return enUS
                    ? senderName + " mentioned you in \"" + docName + "\""
                    : senderName + " " + com.nopkg.hellodoc.utils.MessageUtils.get("notify.mentioned_you_in", "mentioned you in") + "《" + docName + "》";
        }
        if (type == com.nopkg.hellodoc.enums.NotifyType.REPLY) {
            return enUS
                    ? senderName + " replied to your comment in \"" + docName + "\""
                    : senderName + " " + com.nopkg.hellodoc.utils.MessageUtils.get("notify.replied_comment_in", "replied to your comment in") + "《" + docName + "》";
        }
        if (type == com.nopkg.hellodoc.enums.NotifyType.RESOLVE) {
            return enUS
                    ? senderName + " resolved a comment in \"" + docName + "\""
                    : senderName + " " + com.nopkg.hellodoc.utils.MessageUtils.get("notify.resolved_comment_in", "resolved a comment in") + "《" + docName + "》";
        }
        if (StringUtils.hasText(commentSnippet)) {
            return enUS ? senderName + ": " + commentSnippet : senderName + "：" + commentSnippet;
        }
        return enUS ? "You have a new notification" : com.nopkg.hellodoc.utils.MessageUtils.get("notify.new_notification", "You have a new notification");
    }

    private String toStringOrDefault(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String str = String.valueOf(value);
        return StringUtils.hasText(str) ? str : defaultValue;
    }
}
