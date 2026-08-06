package com.nopkg.hellodoc.web.dto.config;

import com.nopkg.hellodoc.entities.SysConfig;
import lombok.Data;

import java.util.Map;

@Data
public class SysConfigVO {
    private Long id;
    private String configKey;
    private String configName;
    private String configValue;
    private String valueType;
    private String configGroup;
    private Boolean isSystem;
    private Boolean isFrontend;
    private String description;
    private Map<String, String> configNameI18n;
    private Map<String, String> descriptionI18n;

    public static SysConfigVO from(SysConfig config) {
        if (config == null) {
            return null;
        }
        SysConfigVO vo = new SysConfigVO();
        vo.setId(config.getId());
        vo.setConfigKey(config.getConfigKey());
        vo.setConfigName(config.getConfigName());
        vo.setConfigValue(config.getConfigValue());
        vo.setValueType(config.getValueType());
        vo.setConfigGroup(config.getConfigGroup());
        vo.setIsSystem(config.getIsSystem());
        vo.setIsFrontend(config.getIsFrontend());
        vo.setDescription(config.getDescription());
        vo.setConfigNameI18n(config.getConfigNameI18n());
        vo.setDescriptionI18n(config.getDescriptionI18n());
        return vo;
    }
}
