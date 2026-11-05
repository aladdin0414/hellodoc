package com.nopkg.hellodoc.i18n;

import com.nopkg.hellodoc.utils.MessageUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageUtilsLocaleTest {
    @Test void applicationMessagesFollowResolvedAccountLanguage() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("i18n.messages");
        source.setDefaultEncoding("UTF-8");
        new MessageUtils(source);
        try {
            LanguageContext.setLocale("en-US");
            LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
            assertEquals("KB not found", MessageUtils.get("legacy.kb.not_found"));
        } finally { LanguageContext.clear(); LocaleContextHolder.resetLocaleContext(); }
    }
}
