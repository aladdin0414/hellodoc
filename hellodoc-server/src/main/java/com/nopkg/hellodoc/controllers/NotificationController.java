package com.nopkg.hellodoc.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.entities.DocNotification;
import com.nopkg.hellodoc.entities.SysUserAuth;
import com.nopkg.hellodoc.repositories.DocNotificationRepository;
import com.nopkg.hellodoc.repositories.UserAuthRepository;
import com.nopkg.hellodoc.services.NotificationService;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.notification.DocNotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification Management", description = "Notification query, read and deletion APIs")
public class NotificationController {

    private final NotificationService notificationService;
    private final DocNotificationRepository notificationRepository;
    private final UserAuthRepository userAuthRepository;
    private final ObjectMapper objectMapper;

    @GetMapping
    @Operation(summary = "Get notification list", description = "Get current user notifications with pagination and unread filtering")
    public ApiResponse<Page<DocNotificationVO>> getUserNotifications(
            @RequestParam(required = false) Boolean unreadOnly,
            Pageable pageable) {
        Long userId = currentUserId();
        Page<DocNotification> page;
        if (Boolean.TRUE.equals(unreadOnly)) {
            page = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false, pageable);
        } else {
            page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return ApiResponse.success(page.map(item -> DocNotificationVO.from(item, objectMapper)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread count", description = "Get unread notification count for current user")
    public ApiResponse<Integer> getUnreadCount() {
        Long userId = currentUserId();
        return ApiResponse.success(notificationService.getUnreadCount(userId));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark as read", description = "Mark specific notification as read")
    public ApiResponse<Void> markAsRead(@PathVariable Long id) {
        Long userId = currentUserId();
        notificationService.markAsRead(id, userId);
        return ApiResponse.success();
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all as read", description = "Mark all unread notifications as read for current user")
    public ApiResponse<Void> markAllAsRead() {
        Long userId = currentUserId();
        notificationService.markAllAsRead(userId);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete notification", description = "Delete specific notification")
    public ApiResponse<Void> deleteNotification(@PathVariable Long id) {
        Long userId = currentUserId();
        notificationService.deleteNotification(id, userId);
        return ApiResponse.success();
    }

    private Long currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUserAuth auth = userAuthRepository.findByIdentifierAndIdentityType(username, "PASSWORD")
                .orElseThrow(() -> new RuntimeException("User not found"));
        return auth.getUser().getId();
    }
}
