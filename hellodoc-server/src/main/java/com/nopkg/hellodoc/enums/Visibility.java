package com.nopkg.hellodoc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;

@Getter
public enum Visibility {
    PRIVATE("private"),
    PUBLIC("public"),
    TEAM("team");

    @JsonValue
    private final String value;

    Visibility(String value) {
        this.value = value;
    }

    @JsonCreator
    public static Visibility fromValue(String value) {
        for (Visibility v : Visibility.values()) {
            if (v.value.equalsIgnoreCase(value)) {
                return v;
            }
        }
        return PRIVATE;
    }

    @Converter(autoApply = true)
    public static class VisibilityConverter implements AttributeConverter<Visibility, String> {
        @Override
        public String convertToDatabaseColumn(Visibility attribute) {
            return attribute != null ? attribute.getValue() : null;
        }

        @Override
        public Visibility convertToEntityAttribute(String dbData) {
            return dbData != null ? Visibility.fromValue(dbData) : null;
        }
    }
}
