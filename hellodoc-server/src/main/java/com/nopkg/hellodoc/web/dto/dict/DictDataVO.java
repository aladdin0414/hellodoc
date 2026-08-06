package com.nopkg.hellodoc.web.dto.dict;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.entities.SysDictDatum;
import lombok.Data;

import java.util.Map;

@Data
public class DictDataVO {
    private Long id;
    private Long dictTypeId;
    private String dictCode;
    private String label;
    private String localizedLabel;
    private Map<String, String> labelI18n;
    private String value;
    private String valueType;
    private String cssClass;
    private String styleAttr;
    private Integer sortOrder;
    private Boolean isDefault;
    private Short status;
    private String remark;

    public static DictDataVO from(SysDictDatum item, String locale, ObjectMapper objectMapper) {
        DictDataVO vo = new DictDataVO();
        vo.setId(item.getId());
        vo.setDictTypeId(item.getDictType() == null ? null : item.getDictType().getId());
        vo.setDictCode(item.getDictCode());
        vo.setLabel(item.getLabel());
        vo.setValue(item.getValue());
        vo.setValueType(item.getValueType());
        vo.setCssClass(item.getCssClass());
        vo.setStyleAttr(item.getStyleAttr());
        vo.setSortOrder(item.getSortOrder());
        vo.setIsDefault(item.getIsDefault());
        vo.setStatus(item.getStatus());
        vo.setRemark(item.getRemark());
        Map<String, String> map = Map.of();
        try {
            if (item.getLabelI18n() != null && !item.getLabelI18n().isBlank()) {
                map = objectMapper.readValue(item.getLabelI18n(), new TypeReference<>() {
                });
            }
        } catch (Exception ignored) {
            map = Map.of();
        }
        vo.setLabelI18n(map);
        String localizedLabel = map.getOrDefault(locale, map.getOrDefault("zh-CN", item.getLabel()));
        vo.setLocalizedLabel(localizedLabel);
        return vo;
    }
}
