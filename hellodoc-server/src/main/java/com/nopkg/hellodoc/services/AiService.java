package com.nopkg.hellodoc.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nopkg.hellodoc.config.AiProperties;
import com.nopkg.hellodoc.entities.SysAiModel;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.repositories.AiModelRepository;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * AI 核心服务：支持多 OpenAI 协议模型动态路由、参数装配、流式与非流式调用
 */
@Slf4j
@Service
public class AiService {

    private final AiProperties aiProperties;
    private final ConfigService configService;
    private final AiModelRepository aiModelRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AiService(AiProperties aiProperties,
                      ConfigService configService,
                      AiModelRepository aiModelRepository,
                      ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.configService = configService;
        this.aiModelRepository = aiModelRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 默认 AI 助手定位提示词（当模型未配置自定义 Agent 提示词时兜底使用）
     */
    public static final String DEFAULT_AGENT_PROMPT =
            "你是一位学识深厚、严谨专业的 AI 知识助手与文档写作导师。\n" +
            "【核心职责】：协助用户进行高品质文档撰写、深度内容解析、文字润色、结构梳理、代码编写与专业答疑。\n\n" +
            "【核心准则与排版铁律（必须 100% 严格执行）】：\n" +
            "1. 【纯净输出，严禁客套】：直接输出核心正文！严禁在回复前或结尾添加任何寒暄客套、自我介绍、前置导语或总结陈词（如“好的，这是为您整理的……”、“希望对您有所帮助”等）。\n" +
            "2. 【严禁自言自语与思考过程】：绝对严禁输出任何内心独白、思考标记、分析思路或多余注释，严禁输出任何非标准控制标签（如 <think> 等）。\n" +
            "3. 【严禁输出 Emoji】：正文、标题与列表中严禁出现任何 Emoji 表情符号，保持严谨优雅的专业文档风格。\n" +
            "4. 【严谨规范的 Markdown 排版】：\n" +
            "   - 标题规范：使用标准 ATX 标题（#、##、### 等），且 # 与标题文字之间必须保留一个空格；\n" +
            "   - 加粗规范：加粗定界符 ** 内部首尾严禁包含空格（必须为 **加粗文本**，严禁 ** 加粗文本 **）；\n" +
            "   - 代码规范：所有代码片段或脚本必须使用标准三反引号（```）包裹并声明具体编程语言（如 ```java、```json 等）；\n" +
            "   - 结构清晰：列表项、表格、引用块前后保留规范空行，层次分明，逻辑严谨。";

    /**
     * 解析后的大模型运行期配置载荷
     */
    @Data
    @Builder
    public static class ResolvedModelConfig {
        private String id;
        private String displayName;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Double temperature;
        private String agentPrompt;
        private boolean disableThinking;
        private int timeout;
    }

    /**
     * 核心路由决策树：解析目标大模型配置
     * 1. 若用户指定了有效的已激活模型，优先使用；
     * 2. 若未指定或该模型已停用，自动 fallback 到系统默认模型（is_default = true）；
     * 3. 若仍无默认模型，选用最新一条已激活模型；
     * 4. 若数据库完全为空，优雅兜底到静态系统配置。
     */
    public ResolvedModelConfig resolveModelConfig(String requestedModelId) {
        int timeout = getResolvedTimeout();

        // 1. 尝试使用用户请求的指定模型
        if (StringUtils.hasText(requestedModelId)) {
            Optional<SysAiModel> modelOpt = aiModelRepository.findById(requestedModelId);
            if (modelOpt.isPresent() && Boolean.TRUE.equals(modelOpt.get().getIsEnabled())) {
                return buildResolvedConfig(modelOpt.get(), timeout);
            }
            log.info("Requested model ID {} not found or inactive, falling back to default", requestedModelId);
        }

        // 2. 尝试使用系统默认模型
        Optional<SysAiModel> defaultOpt = aiModelRepository.findByIsDefaultTrue();
        if (defaultOpt.isPresent() && Boolean.TRUE.equals(defaultOpt.get().getIsEnabled())) {
            return buildResolvedConfig(defaultOpt.get(), timeout);
        }

        // 3. 尝试使用任意一条激活模型
        var activeList = aiModelRepository.findByIsEnabledTrueOrderByCreatedAtDesc();
        if (!activeList.isEmpty()) {
            return buildResolvedConfig(activeList.get(0), timeout);
        }

        // 4. 全局系统配置兜底
        String apiKey = getLegacyApiKey();
        String baseUrl = getLegacyBaseUrl();
        String model = getLegacyModel();

        return ResolvedModelConfig.builder()
                .id("legacy-default")
                .displayName(model)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.70)
                .agentPrompt(DEFAULT_AGENT_PROMPT)
                .disableThinking(true)
                .timeout(timeout)
                .build();
    }

    private ResolvedModelConfig buildResolvedConfig(SysAiModel m, int timeout) {
        String prompt = StringUtils.hasText(m.getAgentPrompt()) ? m.getAgentPrompt().trim() : DEFAULT_AGENT_PROMPT;
        return ResolvedModelConfig.builder()
                .id(m.getId())
                .displayName(m.getName())
                .baseUrl(m.getBaseUrl())
                .apiKey(m.getApiKey())
                .modelName(m.getModelName())
                .temperature(m.getTemperature() != null ? m.getTemperature() : 0.70)
                .agentPrompt(prompt)
                .disableThinking(m.getDisableThinking() == null || m.getDisableThinking())
                .timeout(timeout)
                .build();
    }

    public String getResolvedModel(String requestedModelId) {
        return resolveModelConfig(requestedModelId).getDisplayName();
    }

    public String getResolvedModel() {
        return getResolvedModel(null);
    }

    private int getResolvedTimeout() {
        String dbValue = configService.getConfigValue("ai.openai.timeout");
        if (StringUtils.hasText(dbValue)) {
            try {
                return Integer.parseInt(dbValue);
            } catch (NumberFormatException ignored) {
            }
        }
        return aiProperties.getTimeout();
    }

    private String getLegacyApiKey() {
        String dbValue = configService.getConfigValue("ai.openai.api-key");
        return StringUtils.hasText(dbValue) ? dbValue : aiProperties.getApiKey();
    }

    private String getLegacyBaseUrl() {
        String dbValue = configService.getConfigValue("ai.openai.base-url");
        return StringUtils.hasText(dbValue) ? dbValue : aiProperties.getBaseUrl();
    }

    private String getLegacyModel() {
        String dbValue = configService.getConfigValue("ai.openai.model");
        return StringUtils.hasText(dbValue) ? dbValue : aiProperties.getModel();
    }

    private String buildUserContent(String context, String prompt, String lang) {
        if (!StringUtils.hasText(context)) {
            return prompt;
        }
        String promptLabel = "Instruction: ";
        String contextLabel = "Text Context: ";
        return promptLabel + prompt + "\n\n" + contextLabel + context;
    }

    public String getCompletion(String context, String prompt) {
        return getCompletion(null, context, prompt, null);
    }

    public String getCompletion(String context, String prompt, String lang) {
        return getCompletion(null, context, prompt, lang);
    }

    /**
     * 非流式同步调用大模型补全
     */
    public String getCompletion(String modelId, String context, String prompt, String lang) {
        ResolvedModelConfig config = resolveModelConfig(modelId);

        log.info("AI Completion Request - Model: {} ({}), URL: {}, Timeout: {}ms",
                config.getDisplayName(), config.getModelName(), config.getBaseUrl(), config.getTimeout());

        if (!StringUtils.hasText(config.getApiKey())) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "AI API Key is not configured.");
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(config.getTimeout());
        restTemplate.setRequestFactory(factory);

        String url = config.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModelName());
        if (config.getTemperature() != null) {
            body.put("temperature", config.getTemperature());
        }
        if (config.isDisableThinking()) {
            applyDisableThinking(body);
        }

        ArrayNode messages = body.putArray("messages");
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", config.getAgentPrompt());

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", buildUserContent(context, prompt, lang));

        HttpEntity<String> request = new HttpEntity<>(body.toString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    String content = choices.get(0).path("message").path("content").asText();
                    return cleanThinkingTags(content);
                }
            }
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "AI response parsing failed or empty choices.");
        } catch (org.springframework.web.client.RestClientResponseException e) {
            String errorMsg = "AI API response exception: " + e.getStatusCode();
            try {
                String responseBody = e.getResponseBodyAsString();
                JsonNode errorNode = objectMapper.readTree(responseBody);
                if (errorNode.has("error") && errorNode.get("error").has("message")) {
                    errorMsg = "AI service notice: " + errorNode.get("error").get("message").asText();
                }
            } catch (Exception ignored) {
            }
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, errorMsg);
        } catch (Exception e) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "Failed to call AI service: " + e.getMessage());
        }
    }

    public void streamCompletion(String context, String prompt, Consumer<String> onChunk) {
        streamCompletion(null, context, prompt, null, onChunk);
    }

    public void streamCompletion(String context, String prompt, String lang, Consumer<String> onChunk) {
        streamCompletion(null, context, prompt, lang, onChunk);
    }

    /**
     * SSE 流式调用大模型补全
     */
    public void streamCompletion(String modelId, String context, String prompt, String lang, Consumer<String> onChunk) {
        ResolvedModelConfig config = resolveModelConfig(modelId);

        log.info("AI Stream Request - Model: {} ({}), URL: {}",
                config.getDisplayName(), config.getModelName(), config.getBaseUrl());

        if (!StringUtils.hasText(config.getApiKey())) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "AI API Key is not configured.");
        }

        String url = config.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL(url);
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(0); // 避免长文本断流
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey());

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.getModelName());
            body.put("stream", true);
            if (config.getTemperature() != null) {
                body.put("temperature", config.getTemperature());
            }
            if (config.isDisableThinking()) {
                applyDisableThinking(body);
            }

            ArrayNode messages = body.putArray("messages");
            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", config.getAgentPrompt());

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", buildUserContent(context, prompt, lang));

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload);
            }

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                InputStream errorStream = connection.getErrorStream();
                String errorBody = "";
                if (errorStream != null) {
                    errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                }
                String errorMsg = "AI API response exception: " + statusCode;
                if (StringUtils.hasText(errorBody)) {
                    try {
                        JsonNode errorNode = objectMapper.readTree(errorBody);
                        if (errorNode.has("error") && errorNode.get("error").has("message")) {
                            errorMsg = "AI service notice: " + errorNode.get("error").get("message").asText();
                        }
                    } catch (Exception ignored) {
                    }
                }
                throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, errorMsg);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean inThinkTag = false;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("data:")) {
                        continue;
                    }
                    String jsonPayload = trimmed.substring(5).trim();
                    if ("[DONE]".equals(jsonPayload)) {
                        break;
                    }
                    JsonNode root = objectMapper.readTree(jsonPayload);
                    JsonNode contentNode = root.path("choices").path(0).path("delta").path("content");
                    if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                        String chunk = contentNode.asText();
                        if (!StringUtils.hasText(chunk)) {
                            continue;
                        }

                        // 过滤 <think> 思考标签
                        if (chunk.contains("<think>")) {
                            inThinkTag = true;
                            chunk = chunk.substring(0, chunk.indexOf("<think>"));
                        }
                        if (inThinkTag) {
                            if (chunk.contains("</think>")) {
                                inThinkTag = false;
                                chunk = chunk.substring(chunk.indexOf("</think>") + 8);
                            } else {
                                continue;
                            }
                        }

                        // 剔除特殊停止符
                        if (chunk.contains("<|im_end|>") || chunk.contains("<|endoftext|>")) {
                            chunk = chunk.replaceAll("<\\|im_end\\|>|<\\|endoftext\\|>", "");
                            if (StringUtils.hasText(chunk)) {
                                onChunk.accept(chunk);
                            }
                            break;
                        }

                        if (StringUtils.hasText(chunk)) {
                            onChunk.accept(chunk);
                        }
                    }
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "Failed to stream AI response: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void applyDisableThinking(ObjectNode body) {
        ObjectNode thinking = body.putObject("thinking");
        thinking.put("type", "disabled");
        body.put("enable_thinking", false);
        ObjectNode chatTemplateArgs = body.putObject("chat_template_args");
        chatTemplateArgs.put("enable_thinking", false);
        ObjectNode extraBody = body.putObject("extra_body");
        extraBody.put("enable_thinking", false);
        ObjectNode extraThinking = extraBody.putObject("thinking");
        extraThinking.put("type", "disabled");
    }

    private String cleanThinkingTags(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replaceAll("(?s)<think>.*?(?:</think>|$)", "")
                   .replaceAll("(?s)<thought>.*?(?:</thought>|$)", "")
                   .replaceAll("(?s)<thinking>.*?(?:</thinking>|$)", "")
                   .replaceAll("<\\|im_end\\|>|<\\|endoftext\\|>|<\\|im_start\\|>|<\\|end\\|>", "")
                   .trim();
    }
}
