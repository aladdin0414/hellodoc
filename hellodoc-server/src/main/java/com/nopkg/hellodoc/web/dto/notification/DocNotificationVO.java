package com.nopkg.hellodoc.web.dto.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.entities.DocNotification;
import com.nopkg.hellodoc.enums.NotifyType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class DocNotificationVO {
    private Long id;
    private Long docId;
    private Long userId;
    private Long senderId;
    private NotifyType notifyType;
    private Long refId;
    private String content;
    private String titleKey;
    private String contentKey;
    private Map<String, Object> params;
    private String channel;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public static DocNotificationVO from(DocNotification notification, ObjectMapper objectMapper) {
        if (notification == null) {
            return null;
        }
        DocNotificationVO vo = new DocNotificationVO();
        vo.setId(notification.getId());
        vo.setDocId(notification.getDocId());
        vo.setUserId(notification.getUserId());
        vo.setSenderId(notification.getSenderId());
        vo.setNotifyType(notification.getNotifyType());
        vo.setRefId(notification.getRefId());
        vo.setContent(notification.getContent());
        vo.setTitleKey(notification.getTitleKey());
        vo.setContentKey(notification.getContentKey());
        vo.setChannel(notification.getChannel());
        vo.setIsRead(notification.getIsRead());
        vo.setReadAt(notification.getReadAt());
        vo.setCreatedAt(notification.getCreatedAt());
        try {
            if (notification.getTemplateParams() != null && !notification.getTemplateParams().isBlank()) {
                vo.setParams(objectMapper.readValue(notification.getTemplateParams(), new TypeReference<>() {
                }));
            }
        } catch (Exception ignored) {
            vo.setParams(Map.of());
        }
        return vo;
    }
}
