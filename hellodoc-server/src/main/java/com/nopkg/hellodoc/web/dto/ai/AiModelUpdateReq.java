package com.nopkg.hellodoc.web.dto.ai;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新 AI 大模型请求体
 */
@Data
public class AiModelUpdateReq {

    @Size(max = 100, message = "模型名称长度不能超过100")
    private String name;

    private String provider;

    @Size(max = 500, message = "URL 长度不能超过500")
    private String baseUrl;

    @Size(max = 500, message = "API Key 长度不能超过500")
    private String apiKey;

    @Size(max = 100, message = "模型标识长度不能超过100")
    private String modelName;

    private Double temperature;

    private String agentPrompt;

    private Boolean isDefault;

    private Boolean isEnabled;

    private Boolean disableThinking;
}
