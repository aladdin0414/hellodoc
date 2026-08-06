package com.nopkg.hellodoc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum LockType {
    BLOCK("block"),
    RANGE("range"),
    DOCUMENT("document");

    @JsonValue
    private final String value;

    LockType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static LockType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (LockType v : LockType.values()) {
            if (v.value.equalsIgnoreCase(value) || v.name().equalsIgnoreCase(value)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Unknown LockType: " + value);
    }

    @Converter(autoApply = true)
    public static class LockTypeConverter implements AttributeConverter<LockType, String> {
        @Override
        public String convertToDatabaseColumn(LockType attribute) {
            return attribute != null ? attribute.getValue() : null;
        }

        @Override
        public LockType convertToEntityAttribute(String dbData) {
            return dbData != null ? LockType.fromValue(dbData) : null;
        }
    }
}
