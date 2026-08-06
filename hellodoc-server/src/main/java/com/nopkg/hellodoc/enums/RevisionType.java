package com.nopkg.hellodoc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;

@Getter
public enum RevisionType {
    MANUAL("manual"),
    AUTO("auto"),
    MILESTONE("milestone"),
    RESTORE("restore");

    @JsonValue
    private final String value;

    RevisionType(String value) {
        this.value = value;
    }

    @JsonCreator
    public static RevisionType fromValue(String value) {
        for (RevisionType v : RevisionType.values()) {
            if (v.name().equalsIgnoreCase(value) || v.value.equalsIgnoreCase(value)) {
                return v;
            }
        }
        return null;
    }

    @Converter(autoApply = true)
    public static class RevisionTypeConverter implements AttributeConverter<RevisionType, String> {
        @Override
        public String convertToDatabaseColumn(RevisionType attribute) {
            return attribute != null ? attribute.getValue() : null;
        }

        @Override
        public RevisionType convertToEntityAttribute(String dbData) {
            return dbData != null ? RevisionType.fromValue(dbData) : null;
        }
    }
}
