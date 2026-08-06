package com.nopkg.hellodoc.enums;

public enum StorageProvider {
    LOCAL("local"),
    S3("s3"),
    OSS("oss"),
    COS("cos"),
    MINIO("minio"),
    QINIU("qiniu"),
    R2("r2");

    private final String code;

    StorageProvider(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static StorageProvider fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (StorageProvider p : values()) {
            if (p.code.equalsIgnoreCase(code)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown StorageProvider code: " + code);
    }
}
