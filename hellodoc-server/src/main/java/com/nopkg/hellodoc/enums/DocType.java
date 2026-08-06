package com.nopkg.hellodoc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;

@Getter
public enum DocType {
    FILE("file"),
    FOLDER("folder"),
    LINK("link"),
    EMBED("embed"),
    TEMPLATE("template");

    @JsonValue
    private final String value;

    DocType(String value) {
        this.value = value;
    }

    @JsonCreator
    public static DocType fromValue(String value) {
        for (DocType v : DocType.values()) {
            if (v.value.equalsIgnoreCase(value)) {
                return v;
            }
        }
        return FILE;
    }

    @Converter(autoApply = true)
    public static class DocTypeConverter implements AttributeConverter<DocType, String> {
        @Override
        public String convertToDatabaseColumn(DocType attribute) {
            return attribute != null ? attribute.getValue() : null;
        }

        @Override
        public DocType convertToEntityAttribute(String dbData) {
            return dbData != null ? DocType.fromValue(dbData) : null;
        }
    }
}
