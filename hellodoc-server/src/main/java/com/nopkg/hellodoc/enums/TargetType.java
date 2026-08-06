package com.nopkg.hellodoc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum TargetType {
    USER("user"),
    GROUP("group"),
    LINK("link");

    @JsonValue
    private final String value;

    TargetType(String value) {
        this.value = value;
    }

    @JsonCreator
    public static TargetType fromValue(String value) {
        for (TargetType v : TargetType.values()) {
            if (v.value.equalsIgnoreCase(value)) {
                return v;
            }
        }
        return USER;
    }
}
