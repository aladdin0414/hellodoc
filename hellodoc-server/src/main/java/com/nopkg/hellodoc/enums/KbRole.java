package com.nopkg.hellodoc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;

@Getter
public enum KbRole {
    OWNER("owner"), // 所有者，完全控制
    ADMIN("admin"), // 管理员，可管理成员和设置
    EDITOR("editor"), // 编辑者，可编辑文档
    VIEWER("viewer"); // 查看者，只读

    @JsonValue
    private final String value;

    KbRole(String value) {
        this.value = value;
    }

    @JsonCreator
    public static KbRole fromValue(String value) {
        for (KbRole v : KbRole.values()) {
            if (v.value.equalsIgnoreCase(value)) {
                return v;
            }
        }
        return VIEWER;
    }

    @Converter(autoApply = true)
    public static class KbRoleConverter implements AttributeConverter<KbRole, String> {
        @Override
        public String convertToDatabaseColumn(KbRole attribute) {
            return attribute != null ? attribute.getValue() : null;
        }

        @Override
        public KbRole convertToEntityAttribute(String dbData) {
            return dbData != null ? KbRole.fromValue(dbData) : null;
        }
    }
}
