package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.DocNotification;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.enums.NotifyType;
import com.nopkg.hellodoc.repositories.DocNotificationRepository;
import com.nopkg.hellodoc.repositories.UserRepository;
import com.nopkg.hellodoc.services.impl.NotificationServiceImpl;
import com.nopkg.hellodoc.websocket.DocCollabHandler;
import com.nopkg.hellodoc.websocket.WsMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private DocNotificationRepository notificationRepository;

    @Mock
    private DocCollabHandler docCollabHandler;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void createNotification_ShouldSaveAndPushToWebSocket() throws Exception {
        Long userId = 1L;
        Long senderId = 2L;
        Long docId = 10L;
        Long refId = 100L;
        NotifyType type = NotifyType.MENTION;
        NotificationService.NotificationTemplate template = new NotificationService.NotificationTemplate(
                "notification.mention.title",
                "notification.mention.content",
                "IN_APP");
        NotificationService.NotificationContext context = new NotificationService.NotificationContext(
                type,
                userId,
                senderId,
                docId,
                refId,
                java.util.Map.of("senderName", "Alice", "docName", "Doc A"));

        DocNotification savedNotification = new DocNotification();
        savedNotification.setId(500L);
        savedNotification.setUserId(userId);
        savedNotification.setNotifyType(type);
        savedNotification.setSenderId(senderId);
        savedNotification.setDocId(docId);
        savedNotification.setContent("Alice 在《Doc A》中提到了你");
        savedNotification.setCreatedAt(LocalDateTime.now());

        SysUser user = new SysUser();
        user.setId(userId);
        user.setLanguageMode("zh-CN");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"senderName\":\"Alice\"}");
        when(notificationRepository.save(any(DocNotification.class))).thenReturn(savedNotification);

        notificationService.createNotification(template, context);

        verify(notificationRepository).save(any(DocNotification.class));
        verify(docCollabHandler).sendToUser(eq(userId), any(WsMessage.class));
    }

    @Test
    void markAllAsRead_ShouldUseBatchUpdate() {
        Long userId = 1L;
        notificationService.markAllAsRead(userId);
        verify(notificationRepository).markAllAsReadByUserId(userId);
    }
}
