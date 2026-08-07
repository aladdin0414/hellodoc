package com.nopkg.hellodoc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.entities.SysConfig;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.repositories.SysConfigRepository;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final SysConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    public String getConfigValue(String key) {
        return configRepository.findByConfigKey(key)
                .map(SysConfig::getConfigValue)
                .orElse(null);
    }

    public <T> T getConfigValue(String key, Class<T> clazz) {
        String value = getConfigValue(key);
        if (value == null)
            return null;

        if (clazz == String.class)
            return (T) value;
        if (clazz == Integer.class)
            return (T) Integer.valueOf(value);
        if (clazz == Boolean.class)
            return (T) Boolean.valueOf(value);

        try {
            return objectMapper.readValue(value, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse config value for key {}: {}", key, value, e);
            return null;
        }
    }

    public List<SysConfig> listConfigs() {
        return configRepository.findAll();
    }

    public Map<String, String> getFrontendConfigs() {
        return configRepository.findByIsFrontendTrue().stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue));
    }

    @Transactional
    public SysConfig updateConfig(SysConfig config) {
        SysConfig existing = configRepository.findByConfigKey(config.getConfigKey())
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("config.item_not_found", "Configuration item not found")));

        existing.setConfigValue(config.getConfigValue());
        if (config.getConfigName() != null) {
            existing.setConfigName(config.getConfigName());
        }
        if (config.getDescription() != null) {
            existing.setDescription(config.getDescription());
        }
        if (config.getConfigNameI18n() != null) {
            existing.setConfigNameI18n(config.getConfigNameI18n());
        }
        if (config.getDescriptionI18n() != null) {
            existing.setDescriptionI18n(config.getDescriptionI18n());
        }
        existing.setUpdateTime(Instant.now());
        return configRepository.save(existing);
    }

    @Transactional
    public SysConfig createConfig(SysConfig config) {
        if (configRepository.existsByConfigKey(config.getConfigKey())) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("config.key_exists", "Configuration key already exists"));
        }
        config.setCreateTime(Instant.now());
        config.setUpdateTime(Instant.now());
        return configRepository.save(config);
    }

    public void refreshCache() {
        log.info("Config cache is disabled, skip refresh");
    }
}
