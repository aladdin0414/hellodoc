package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.enums.NotifyType;

import java.util.Map;

public interface NotificationService {
    record NotificationTemplate(
            String titleKey,
            String contentKey,
            String channel) {
    }

    record NotificationContext(
            NotifyType notifyType,
            Long receiverUserId,
            Long senderUserId,
            Long docId,
            Long refId,
            Map<String, Object> params) {
    }

    void createNotification(NotificationTemplate template, NotificationContext context);

    Integer getUnreadCount(Long userId);

    void markAsRead(Long notificationId, Long userId);

    void markAllAsRead(Long userId);

    void deleteNotification(Long id, Long userId);
}
