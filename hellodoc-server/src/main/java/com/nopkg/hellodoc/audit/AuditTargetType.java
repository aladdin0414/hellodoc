package com.nopkg.hellodoc.audit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;

@Getter
public enum AuditTargetType {
    KB("kb"),
    DOCUMENT("document"),
    MEMBER("member"),
    COMMENT("comment"),
    REVISION("revision");

    @JsonValue
    private final String value;

    AuditTargetType(String value) {
        this.value = value;
    }

    @JsonCreator
    public static AuditTargetType fromValue(String value) {
        for (AuditTargetType v : AuditTargetType.values()) {
            if (v.name().equalsIgnoreCase(value) || v.value.equalsIgnoreCase(value)) {
                return v;
            }
        }
        return null;
    }

    @Converter(autoApply = true)
    public static class AuditTargetTypeConverter implements AttributeConverter<AuditTargetType, String> {
        @Override
        public String convertToDatabaseColumn(AuditTargetType attribute) {
            return attribute != null ? attribute.getValue() : null;
        }

        @Override
        public AuditTargetType convertToEntityAttribute(String dbData) {
            return dbData != null ? AuditTargetType.fromValue(dbData) : null;
        }
    }
}
