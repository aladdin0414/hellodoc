package com.nopkg.hellodoc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum DocRole {
    EDITOR("editor"),
    VIEWER("viewer");

    @JsonValue
    private final String value;

    DocRole(String value) {
        this.value = value;
    }

    @JsonCreator
    public static DocRole fromValue(String value) {
        for (DocRole v : DocRole.values()) {
            if (v.value.equalsIgnoreCase(value)) {
                return v;
            }
        }
        return VIEWER;
    }
    @jakarta.persistence.Converter(autoApply = true)
    public static class DocRoleConverter implements jakarta.persistence.AttributeConverter<DocRole, String> {
        @Override public String convertToDatabaseColumn(DocRole value) { return value == null ? null : value.getValue(); }
        @Override public DocRole convertToEntityAttribute(String value) { return value == null ? null : DocRole.fromValue(value); }
    }
}
