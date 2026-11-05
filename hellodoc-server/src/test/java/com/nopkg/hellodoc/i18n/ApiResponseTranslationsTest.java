package com.nopkg.hellodoc.i18n;

import com.nopkg.hellodoc.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiResponseTranslationsTest {
    @Test void messageUtilsKeysHaveBothTranslations() throws Exception {
        var source = new ResourceBundleMessageSource();
        source.setBasename("i18n.messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        var pattern = Pattern.compile("MessageUtils\\.get\\(\\s*(?:[^,\"\\n]+,\\s*)?\"([\\w.-]+)\"");
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                var matcher = pattern.matcher(Files.readString(path));
                while (matcher.find()) for (var locale : new Locale[]{Locale.US, Locale.SIMPLIFIED_CHINESE}) {
                    assertNotNull(source.getMessage(matcher.group(1), null, null, locale), path + ": " + matcher.group(1) + " " + locale);
                }
            }
        }
    }
    @Test void everyResponseCodeHasBothTranslations() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("i18n.messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        for (var code : ApiResponse.Code.values()) {
            for (var locale : new Locale[]{Locale.US, Locale.SIMPLIFIED_CHINESE}) {
                assertNotNull(source.getMessage("api.code." + code.name(), null, null, locale), code.name() + " " + locale);
            }
        }
    }
}
