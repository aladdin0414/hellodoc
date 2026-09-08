package com.nopkg.hellodoc.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nopkg.hellodoc.entities.SysAiModel;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.repositories.AiModelRepository;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.ai.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 大模型服务层实现
 */
@Slf4j
@Service
public class AiModelService {

    private final AiModelRepository aiModelRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public AiModelService(AiModelRepository aiModelRepository,
                          ObjectMapper objectMapper) {
        this.aiModelRepository = aiModelRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 获取全部模型列表（管理员后台视图）
     */
    public List<AiModelResp> getAllModels() {
        return aiModelRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(m -> AiModelResp.fromEntity(m, true))
                .collect(Collectors.toList());
    }

    /**
     * 获取所有已激活的模型列表（普通用户视图，已脱敏）
     */
    public List<AiModelResp> getActiveModels() {
        return aiModelRepository.findByIsEnabledTrueOrderByCreatedAtDesc()
                .stream()
                .map(m -> AiModelResp.fromEntity(m, false))
                .collect(Collectors.toList());
    }

    /**
     * 获取当前系统默认模型
     */
    public Optional<SysAiModel> getDefaultModel() {
        return aiModelRepository.findByIsDefaultTrue();
    }

    /**
     * 根据 ID 获取模型实体
     */
    public Optional<SysAiModel> getModelById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return aiModelRepository.findById(id);
    }

    /**
     * 创建新大模型配置
     */
    @Transactional
    public AiModelResp createModel(AiModelCreateReq req) {
        SysAiModel model = new SysAiModel();
        model.setId(UUID.randomUUID().toString());
        model.setName(req.getName().trim());
        model.setProvider(StringUtils.hasText(req.getProvider()) ? req.getProvider().trim() : "custom");
        model.setBaseUrl(req.getBaseUrl().trim());
        model.setApiKey(req.getApiKey().trim());
        model.setModelName(req.getModelName().trim());
        model.setTemperature(req.getTemperature() != null ? req.getTemperature() : 0.70);
        model.setAgentPrompt(req.getAgentPrompt());
        model.setDisableThinking(req.getDisableThinking() != null ? req.getDisableThinking() : true);

        boolean isFirstModel = aiModelRepository.count() == 0;
        boolean wantsDefault = Boolean.TRUE.equals(req.getIsDefault()) || isFirstModel;

        if (wantsDefault) {
            aiModelRepository.clearAllDefaults();
            model.setIsDefault(true);
            model.setIsEnabled(true); // 默认模型必须激活
        } else {
            model.setIsDefault(false);
            model.setIsEnabled(req.getIsEnabled() != null ? req.getIsEnabled() : true);
        }

        SysAiModel saved = aiModelRepository.save(model);
        return AiModelResp.fromEntity(saved, true);
    }

    /**
     * 更新大模型配置
     */
    @Transactional
    public AiModelResp updateModel(String id, AiModelUpdateReq req) {
        SysAiModel model = aiModelRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.PARAM_ERROR, "指定的 AI 模型不存在"));

        if (StringUtils.hasText(req.getName())) {
            model.setName(req.getName().trim());
        }
        if (StringUtils.hasText(req.getProvider())) {
            model.setProvider(req.getProvider().trim());
        }
        if (StringUtils.hasText(req.getBaseUrl())) {
            model.setBaseUrl(req.getBaseUrl().trim());
        }
        if (StringUtils.hasText(req.getApiKey())) {
            model.setApiKey(req.getApiKey().trim());
        }
        if (StringUtils.hasText(req.getModelName())) {
            model.setModelName(req.getModelName().trim());
        }
        if (req.getTemperature() != null) {
            model.setTemperature(req.getTemperature());
        }
        if (req.getAgentPrompt() != null) {
            model.setAgentPrompt(req.getAgentPrompt());
        }
        if (req.getDisableThinking() != null) {
            model.setDisableThinking(req.getDisableThinking());
        }

        // 默认模型状态与激活互斥保护
        if (Boolean.TRUE.equals(req.getIsDefault())) {
            aiModelRepository.clearOtherDefaults(id);
            model.setIsDefault(true);
            model.setIsEnabled(true); // 默认模型强制激活
        } else if (Boolean.FALSE.equals(req.getIsDefault()) && Boolean.TRUE.equals(model.getIsDefault())) {
            // 禁止主动取消默认状态（必须通过设置另一模型为默认来替换）
            long totalCount = aiModelRepository.count();
            if (totalCount > 1) {
                throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "系统必须保留一个默认模型，请直接将其他模型设为默认");
            }
        }

        if (req.getIsEnabled() != null) {
            if (!req.getIsEnabled() && Boolean.TRUE.equals(model.getIsDefault())) {
                throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "系统默认模型不能停用，请先将其他模型设为默认");
            }
            model.setIsEnabled(req.getIsEnabled());
        }

        SysAiModel updated = aiModelRepository.save(model);
        return AiModelResp.fromEntity(updated, true);
    }

    /**
     * 删除模型配置
     */
    @Transactional
    public void deleteModel(String id) {
        SysAiModel model = aiModelRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.PARAM_ERROR, "指定的 AI 模型不存在"));

        if (Boolean.TRUE.equals(model.getIsDefault())) {
            long totalCount = aiModelRepository.count();
            if (totalCount > 1) {
                throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "默认模型不可删除，请先将其他模型设为默认");
            }
        }

        aiModelRepository.delete(model);
    }

    /**
     * 一键设为默认模型
     */
    @Transactional
    public void setDefault(String id) {
        SysAiModel model = aiModelRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.PARAM_ERROR, "指定的 AI 模型不存在"));

        aiModelRepository.clearOtherDefaults(id);
        model.setIsDefault(true);
        model.setIsEnabled(true); // 默认模型同时激活
        aiModelRepository.save(model);
    }

    /**
     * 一键切换激活状态
     */
    @Transactional
    public void toggleActive(String id) {
        SysAiModel model = aiModelRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.PARAM_ERROR, "指定的 AI 模型不存在"));

        if (Boolean.TRUE.equals(model.getIsDefault()) && Boolean.TRUE.equals(model.getIsEnabled())) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "系统默认模型不能停用，请先将其他模型设为默认");
        }

        model.setIsEnabled(!Boolean.TRUE.equals(model.getIsEnabled()));
        aiModelRepository.save(model);
    }

    /**
     * 连通性测试
     */
    public Map<String, Object> testConnection(AiModelTestReq req) {
        String baseUrl = req.getBaseUrl();
        String apiKey = req.getApiKey();
        String modelName = req.getModelName();

        boolean disableThinking = true;
        // 若传递了已存在模型的 id，则补全空缺字段
        if (StringUtils.hasText(req.getId())) {
            Optional<SysAiModel> modelOpt = aiModelRepository.findById(req.getId());
            if (modelOpt.isPresent()) {
                SysAiModel m = modelOpt.get();
                if (!StringUtils.hasText(baseUrl)) baseUrl = m.getBaseUrl();
                if (!StringUtils.hasText(apiKey)) apiKey = m.getApiKey();
                if (!StringUtils.hasText(modelName)) modelName = m.getModelName();
                if (m.getDisableThinking() != null) {
                    disableThinking = m.getDisableThinking();
                }
            }
        }
        if (req.getDisableThinking() != null) {
            disableThinking = req.getDisableThinking();
        }

        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(modelName)) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "URL、API Key 与 模型标识不能为空");
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(25000);
        RestTemplate testRest = new RestTemplate(factory);

        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        body.put("max_tokens", 32);

        // 连通性测试时抑制深度思考（Thinking），避免模型产生数百 token 的 reasoning 导致耗时飙升至十几秒
        if (disableThinking) {
            body.putObject("thinking").put("type", "disabled");
            body.put("enable_thinking", false);
            body.putObject("chat_template_args").put("enable_thinking", false);
            body.putObject("extra_body").put("enable_thinking", false);
            body.putObject("extra_body").putObject("thinking").put("type", "disabled");
        }

        ArrayNode messages = body.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", StringUtils.hasText(req.getPrompt()) ? req.getPrompt() : "Hello");

        long startTime = System.currentTimeMillis();
        try {
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
            ResponseEntity<String> response = testRest.postForEntity(url, entity, String.class);
            long latency = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");
                String reply = "";
                if (choices.isArray() && !choices.isEmpty()) {
                    reply = choices.get(0).path("message").path("content").asText();
                }

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("latencyMs", latency);
                result.put("reply", reply.trim());
                result.put("model", modelName);
                return result;
            }
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "模型响应状态异常: " + response.getStatusCode());
        } catch (org.springframework.web.client.RestClientResponseException e) {
            String errorMsg = "HTTP " + e.getStatusCode();
            try {
                JsonNode errNode = objectMapper.readTree(e.getResponseBodyAsString());
                if (errNode.has("error") && errNode.get("error").has("message")) {
                    errorMsg = errNode.get("error").get("message").asText();
                }
            } catch (Exception ignored) {
            }
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "连接失败: " + errorMsg);
        } catch (Exception e) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "连接超时或网络异常: " + e.getMessage());
        }
    }
}
