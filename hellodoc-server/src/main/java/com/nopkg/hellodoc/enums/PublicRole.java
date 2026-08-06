package com.nopkg.hellodoc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;

@Getter
public enum PublicRole {
    VIEWER("viewer"),
    NONE("none");

    @JsonValue
    private final String value;

    PublicRole(String value) {
        this.value = value;
    }

    @JsonCreator
    public static PublicRole fromValue(String value) {
        for (PublicRole v : PublicRole.values()) {
            if (v.value.equalsIgnoreCase(value)) {
                return v;
            }
        }
        return NONE;
    }

    @Converter(autoApply = true)
    public static class PublicRoleConverter implements AttributeConverter<PublicRole, String> {
        @Override
        public String convertToDatabaseColumn(PublicRole attribute) {
            return attribute != null ? attribute.getValue() : null;
        }

        @Override
        public PublicRole convertToEntityAttribute(String dbData) {
            return dbData != null ? PublicRole.fromValue(dbData) : null;
        }
    }
}
