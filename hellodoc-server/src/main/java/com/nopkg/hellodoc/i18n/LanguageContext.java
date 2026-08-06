package com.nopkg.hellodoc.i18n;

public final class LanguageContext {
    private static final ThreadLocal<String> LOCALE_HOLDER = new ThreadLocal<>();
    public static final String DEFAULT_LOCALE = "zh-CN";

    private LanguageContext() {
    }

    public static void setLocale(String locale) {
        LOCALE_HOLDER.set(normalize(locale));
    }

    public static String getLocale() {
        String locale = LOCALE_HOLDER.get();
        if (locale == null || locale.isBlank()) {
            return DEFAULT_LOCALE;
        }
        return locale;
    }

    public static String normalize(String locale) {
        if (locale == null || locale.isBlank()) {
            return DEFAULT_LOCALE;
        }
        String lower = locale.toLowerCase();
        if (lower.startsWith("en")) {
            return "en-US";
        }
        return "zh-CN";
    }

    public static void clear() {
        LOCALE_HOLDER.remove();
    }
}
