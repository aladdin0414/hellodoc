package com.nopkg.hellodoc.utils;

import org.springframework.util.StringUtils;

public class LtreeUtils {

    public static String generatePath(Long id) {
        return id.toString().replace("-", "_");
    }

    public static String concat(String parentPath, String currentId) {
        if (!StringUtils.hasText(parentPath)) {
            return currentId;
        }
        return parentPath + "." + currentId;
    }

    public static String getParentPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        int lastDot = path.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return path.substring(0, lastDot);
    }
}
