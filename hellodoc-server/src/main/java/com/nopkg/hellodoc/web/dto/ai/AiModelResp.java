package com.nopkg.hellodoc.web.dto.ai;

import com.nopkg.hellodoc.entities.SysAiModel;
import lombok.Data;

import java.time.Instant;

/**
 * AI 大模型响应对象
 */
@Data
public class AiModelResp {

    private String id;
    private String name;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Double temperature;
    private String agentPrompt;
    private Boolean isDefault;
    private Boolean isEnabled;
    private Boolean disableThinking;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * 将实体转换为响应对象，对普通用户进行安全性脱敏
     *
     * @param entity  大模型实体
     * @param isAdmin 是否为管理员视角
     * @return 响应传输对象
     */
    public static AiModelResp fromEntity(SysAiModel entity, boolean isAdmin) {
        if (entity == null) {
            return null;
        }
        AiModelResp resp = new AiModelResp();
        resp.setId(entity.getId());
        resp.setName(entity.getName());
        resp.setProvider(entity.getProvider());
        resp.setModelName(entity.getModelName());
        resp.setTemperature(entity.getTemperature());
        resp.setIsDefault(entity.getIsDefault());
        resp.setIsEnabled(entity.getIsEnabled());
        resp.setDisableThinking(entity.getDisableThinking());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());

        if (isAdmin) {
            resp.setBaseUrl(entity.getBaseUrl());
            resp.setAgentPrompt(entity.getAgentPrompt());
            // 管理员界面展示脱敏的 API Key（保留前4后4）
            String rawKey = entity.getApiKey();
            if (rawKey != null && rawKey.length() > 8) {
                resp.setApiKey(rawKey.substring(0, 4) + "****" + rawKey.substring(rawKey.length() - 4));
            } else {
                resp.setApiKey("********");
            }
        } else {
            // 普通前台用户严格隐藏 API Key 与 Base URL，避免敏感信息外泄
            resp.setBaseUrl(null);
            resp.setApiKey(null);
            resp.setAgentPrompt(null);
        }
        return resp;
    }
}
