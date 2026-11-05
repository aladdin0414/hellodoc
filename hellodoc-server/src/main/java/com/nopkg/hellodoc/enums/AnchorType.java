package com.nopkg.hellodoc.enums;

public enum AnchorType {
    RANGE, // 选区
    POINT, // 点
    BLOCK; // 块

    @jakarta.persistence.Converter
    public static class AnchorTypeConverter implements jakarta.persistence.AttributeConverter<AnchorType, String> {
        @Override public String convertToDatabaseColumn(AnchorType value) {
            return value == null ? null : value.name().toLowerCase(java.util.Locale.ROOT);
        }
        @Override public AnchorType convertToEntityAttribute(String value) {
            return value == null ? null : AnchorType.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        }
    }
}
