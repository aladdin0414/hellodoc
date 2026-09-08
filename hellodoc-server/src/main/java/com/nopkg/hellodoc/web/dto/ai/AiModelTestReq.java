package com.nopkg.hellodoc.web.dto.ai;

import lombok.Data;

/**
 * AI 大模型连通性测试请求体
 */
@Data
public class AiModelTestReq {

    /**
     * 已经保存的模型ID（如果传递，且未指定 baseUrl/apiKey，则读取已保存模型数据进行测试）
     */
    private String id;

    /**
     * OpenAI 协议 Base URL
     */
    private String baseUrl;

    /**
     * API Key
     */
    private String apiKey;

    /**
     * 模型标识
     */
    private String modelName;

    /**
     * 测试提示词
     */
    private String prompt;

    /**
     * 是否禁用深度思考标签（若模型为推理模型，禁用后可大幅降低探测耗时与 token 消耗）
     */
    private Boolean disableThinking;
}
