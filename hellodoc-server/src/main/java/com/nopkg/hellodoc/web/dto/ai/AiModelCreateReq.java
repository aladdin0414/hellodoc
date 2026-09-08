package com.nopkg.hellodoc.web.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 AI 大模型请求体
 */
@Data
public class AiModelCreateReq {

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称长度不能超过100")
    private String name;

    private String provider = "custom";

    @NotBlank(message = "API Base URL 不能为空")
    @Size(max = 500, message = "URL 长度不能超过500")
    private String baseUrl;

    @NotBlank(message = "API Key 不能为空")
    @Size(max = 500, message = "API Key 长度不能超过500")
    private String apiKey;

    @NotBlank(message = "模型标识不能为空")
    @Size(max = 100, message = "模型标识长度不能超过100")
    private String modelName;

    private Double temperature = 0.70;

    private String agentPrompt;

    private Boolean isDefault = false;

    private Boolean isEnabled = true;

    private Boolean disableThinking = true;
}
