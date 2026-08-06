package com.nopkg.hellodoc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum RelationType {
    LINK("link"),
    EMBED("embed"),
    FORK("fork");

    @JsonValue
    private final String value;

    RelationType(String value) {
        this.value = value;
    }

    @JsonCreator
    public static RelationType fromValue(String value) {
        for (RelationType v : RelationType.values()) {
            if (v.value.equalsIgnoreCase(value)) {
                return v;
            }
        }
        return LINK;
    }
}
