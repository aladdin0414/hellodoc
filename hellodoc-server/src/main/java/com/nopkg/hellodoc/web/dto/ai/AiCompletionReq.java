package com.nopkg.hellodoc.web.dto.ai;

import lombok.Data;

/**
 * AI 补全/对话请求体
 */
@Data
public class AiCompletionReq {
    /**
     * 用户指定的已激活大模型 ID（可选；未传或无效时自动回退默认大模型）
     */
    private String modelId;

    /**
     * 选中文本上下文或参考内容
     */
    private String context;

    /**
     * 用户输入的指令提示词
     */
    private String prompt;

    /**
     * 语言偏好（如 zh-CN）
     */
    private String lang;
}
