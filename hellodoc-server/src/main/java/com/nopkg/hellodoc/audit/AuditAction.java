package com.nopkg.hellodoc.audit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;

@Getter
public enum AuditAction {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    RESTORE("restore"),
    SHARE("share"),
    UNSHARE("unshare"),
    EXPORT("export"),
    IMPORT("import");

    @JsonValue
    private final String value;

    AuditAction(String value) {
        this.value = value;
    }

    @JsonCreator
    public static AuditAction fromValue(String value) {
        for (AuditAction v : AuditAction.values()) {
            if (v.name().equalsIgnoreCase(value) || v.value.equalsIgnoreCase(value)) {
                return v;
            }
        }
        return null;
    }

    @Converter(autoApply = true)
    public static class AuditActionConverter implements AttributeConverter<AuditAction, String> {
        @Override
        public String convertToDatabaseColumn(AuditAction attribute) {
            return attribute != null ? attribute.getValue() : null;
        }

        @Override
        public AuditAction convertToEntityAttribute(String dbData) {
            return dbData != null ? AuditAction.fromValue(dbData) : null;
        }
    }
}
