package com.nopkg.hellodoc.i18n;

import com.nopkg.hellodoc.enums.NotifyType;
import com.nopkg.hellodoc.services.impl.NotificationServiceImpl;
import com.nopkg.hellodoc.utils.MessageUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationLanguageTest {
    @Test void recipientLanguageControlsNotificationTextEvenWhenSenderUsesAnotherLanguage() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("i18n.messages"); source.setDefaultEncoding("UTF-8");
        new MessageUtils(source);
        var service = new NotificationServiceImpl(null, null, null, null);
        try {
            LanguageContext.setLocale("en-US");
            String result = ReflectionTestUtils.invokeMethod(service, "renderFallbackContent", NotifyType.MENTION, "zh-CN", Map.of());
            assertEquals("用户 提及了你，位于《文档》", result);
        } finally { LanguageContext.clear(); }
    }
}
