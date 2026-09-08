package com.nopkg.hellodoc.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

/**
 * AI 大模型独立配置实体
 */
@Getter
@Setter
@Entity
@Table(name = "sys_ai_model", indexes = {
    @Index(name = "idx_ai_model_status", columnList = "is_enabled, is_default")
})
public class SysAiModel {

    /**
     * 模型唯一标识 (UUID)
     */
    @Id
    @Size(max = 64)
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /**
     * 模型展示别名（例如：DeepSeek 满血版、GPT-4o、通义千问 Plus）
     */
    @Size(max = 100)
    @NotNull
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * 服务商标识（例如：openai, deepseek, qwen, custom 等）
     */
    @Size(max = 50)
    @ColumnDefault("'custom'")
    @Column(name = "provider", length = 50)
    private String provider;

    /**
     * OpenAI 协议请求端点 URL (例如：https://api.deepseek.com/v1)
     */
    @Size(max = 500)
    @NotNull
    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    /**
     * 大模型 API Key（后端安全保管，不暴露给前端普通用户）
     */
    @Size(max = 500)
    @NotNull
    @Column(name = "api_key", nullable = false, length = 500)
    private String apiKey;

    /**
     * 实际调用的模型名称标识（例如：deepseek-chat, gpt-4o）
     */
    @Size(max = 100)
    @NotNull
    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    /**
     * 采样温度（0.0 ~ 2.0），控制生成随机性
     */
    @ColumnDefault("0.70")
    @Column(name = "temperature")
    private Double temperature;

    /**
     * Agent 定位提示词（系统角色设定）；为空时自动回退系统默认提示词
     */
    @Column(name = "agent_prompt", columnDefinition = "TEXT")
    private String agentPrompt;

    /**
     * 是否系统默认模型（全局唯一为 true）
     */
    @ColumnDefault("false")
    @Column(name = "is_default")
    private Boolean isDefault;

    /**
     * 是否激活状态（激活后普通用户才可在 AI 助理中选择）
     */
    @ColumnDefault("true")
    @Column(name = "is_enabled")
    private Boolean isEnabled;

    /**
     * 是否禁用思考/推理过程标签（针对 DeepSeek R1 / 通义千问等模型）
     */
    @ColumnDefault("true")
    @Column(name = "disable_thinking")
    private Boolean disableThinking;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private Instant createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null || this.id.trim().isEmpty()) {
            this.id = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.provider == null) {
            this.provider = "custom";
        }
        if (this.temperature == null) {
            this.temperature = 0.70;
        }
        if (this.isDefault == null) {
            this.isDefault = false;
        }
        if (this.isEnabled == null) {
            this.isEnabled = true;
        }
        if (this.disableThinking == null) {
            this.disableThinking = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
