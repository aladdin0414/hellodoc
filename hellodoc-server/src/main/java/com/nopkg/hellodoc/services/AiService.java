package com.nopkg.hellodoc.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nopkg.hellodoc.config.AiProperties;
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
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AiService {

    private final AiProperties aiProperties;
    private final ConfigService configService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AiService(AiProperties aiProperties, ConfigService configService, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    private int getResolvedTimeout() {
        String dbValue = configService.getConfigValue("ai.openai.timeout");
        if (StringUtils.hasText(dbValue)) {
            try {
                return Integer.parseInt(dbValue);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return aiProperties.getTimeout();
    }

    private String getResolvedApiKey() {
        String dbValue = configService.getConfigValue("ai.openai.api-key");
        return StringUtils.hasText(dbValue) ? dbValue : aiProperties.getApiKey();
    }

    private String getResolvedBaseUrl() {
        String dbValue = configService.getConfigValue("ai.openai.base-url");
        return StringUtils.hasText(dbValue) ? dbValue : aiProperties.getBaseUrl();
    }

    public String getResolvedModel() {
        String dbValue = configService.getConfigValue("ai.openai.model");
        return StringUtils.hasText(dbValue) ? dbValue : aiProperties.getModel();
    }

    private String getResolvedAgent() {
        String dbValue = configService.getConfigValue("ai.openai.agent");
        String agent = StringUtils.hasText(dbValue) ? dbValue : aiProperties.getAgent();
        if (!StringUtils.hasText(agent)) {
            return "You are a professional AI assistant. Please output response content directly using standard Github-flavored markdown (GFM) format. Please adhere to the following rules:\n" +
                   "1. Do not include extraneous pleasantries, prefaces, transitions, or self-introductions. Output main content directly.\n" +
                   "2. Ensure proper structure using standard heading hierarchy (#, ##, etc., with exactly one space after #).\n" +
                   "3. Code must be wrapped in standard Markdown code blocks (```) with explicit language identifiers.\n" +
                   "4. Tables, lists, and blockquotes must have blank lines before and after to ensure clean rendering.\n" +
                   "5. Do not output non-standard text control tags.";
        }
        return agent;
    }

    private String buildUserContent(String context, String prompt, String lang) {
        String promptLabel = "Instruction: ";
        String contextLabel = "Text Context: ";
        return promptLabel + prompt + "\n\n" + contextLabel + context;
    }

    public String getCompletion(String context, String prompt) {
        return getCompletion(context, prompt, null);
    }

    public String getCompletion(String context, String prompt, String lang) {
        String apiKey = getResolvedApiKey();
        String baseUrl = getResolvedBaseUrl();
        String model = getResolvedModel();
        String agent = getResolvedAgent();
        int timeout = getResolvedTimeout();

        log.info("AI Completion Request - Model: {}, URL: {}, Timeout: {}ms", model, baseUrl, timeout);

        if (!StringUtils.hasText(apiKey)) {
            throw new com.nopkg.hellodoc.exceptions.BusinessException(com.nopkg.hellodoc.web.ApiResponse.Code.SYSTEM_ERROR, "AI API Key is not configured.");
        }

        // Apply dynamic timeout
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(timeout);
        restTemplate.setRequestFactory(factory);

        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        
        ArrayNode messages = body.putArray("messages");
        
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", agent);
        
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
                    return choices.get(0).path("message").path("content").asText();
                }
            }
            throw new com.nopkg.hellodoc.exceptions.BusinessException(com.nopkg.hellodoc.web.ApiResponse.Code.SYSTEM_ERROR, "AI response parsing failed or empty choices.");
        } catch (org.springframework.web.client.RestClientResponseException e) {
            String errorMsg = "AI API response exception: " + e.getStatusCode();
            try {
                String responseBody = e.getResponseBodyAsString();
                JsonNode errorNode = objectMapper.readTree(responseBody);
                if (errorNode.has("error") && errorNode.get("error").has("message")) {
                    errorMsg = "AI service notice: " + errorNode.get("error").get("message").asText();
                }
            } catch (Exception parseEx) {
                // ignore
            }
            throw new com.nopkg.hellodoc.exceptions.BusinessException(com.nopkg.hellodoc.web.ApiResponse.Code.SYSTEM_ERROR, errorMsg);
        } catch (Exception e) {
            throw new com.nopkg.hellodoc.exceptions.BusinessException(com.nopkg.hellodoc.web.ApiResponse.Code.SYSTEM_ERROR, "Failed to call AI service: " + e.getMessage());
        }
    }

    public void streamCompletion(String context, String prompt, Consumer<String> onChunk) {
        streamCompletion(context, prompt, null, onChunk);
    }

    public void streamCompletion(String context, String prompt, String lang, Consumer<String> onChunk) {
        String apiKey = getResolvedApiKey();
        String baseUrl = getResolvedBaseUrl();
        String model = getResolvedModel();
        String agent = getResolvedAgent();

        if (!StringUtils.hasText(apiKey)) {
            throw new com.nopkg.hellodoc.exceptions.BusinessException(com.nopkg.hellodoc.web.ApiResponse.Code.SYSTEM_ERROR, "AI API Key is not configured.");
        }

        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL(url);
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            // 流式模式关闭读取超时，避免长文本生成时被中途断流
            connection.setReadTimeout(0);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("stream", true);

            ArrayNode messages = body.putArray("messages");
            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", agent);

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
                        // ignore parse error
                    }
                }
                throw new com.nopkg.hellodoc.exceptions.BusinessException(com.nopkg.hellodoc.web.ApiResponse.Code.SYSTEM_ERROR, errorMsg);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
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
                        if (StringUtils.hasText(chunk)) {
                            onChunk.accept(chunk);
                        }
                    }
                }
            }
        } catch (com.nopkg.hellodoc.exceptions.BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new com.nopkg.hellodoc.exceptions.BusinessException(com.nopkg.hellodoc.web.ApiResponse.Code.SYSTEM_ERROR, "Failed to stream AI response: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
