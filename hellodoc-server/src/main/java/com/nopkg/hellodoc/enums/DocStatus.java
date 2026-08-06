package com.nopkg.hellodoc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;

@Getter
public enum DocStatus {
    DRAFT("draft"),
    PUBLISHED("published");

    @JsonValue
    private final String value;

    DocStatus(String value) {
        this.value = value;
    }

    @JsonCreator
    public static DocStatus fromValue(String value) {
        for (DocStatus v : DocStatus.values()) {
            if (v.value.equalsIgnoreCase(value)) {
                return v;
            }
        }
        return DRAFT;
    }

    @Converter(autoApply = true)
    public static class DocStatusConverter implements AttributeConverter<DocStatus, String> {
        @Override
        public String convertToDatabaseColumn(DocStatus attribute) {
            return attribute != null ? attribute.getValue() : null;
        }

        @Override
        public DocStatus convertToEntityAttribute(String dbData) {
            return dbData != null ? DocStatus.fromValue(dbData) : null;
        }
    }
}
