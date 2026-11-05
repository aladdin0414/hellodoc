package com.nopkg.hellodoc.i18n;

import com.nopkg.hellodoc.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AiModelMessagesTest {
    @Test
    void modelErrorsFollowRequestLanguage() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("i18n.messages");
        source.setDefaultEncoding("UTF-8");
        var service = new ApiI18nService(source);
        try {
            LanguageContext.setLocale("en-US");
            assertEquals("The default model cannot be disabled; set another model as default first", service.resolveMessage(ApiResponse.Code.PARAM_ERROR, "系统默认模型不能停用，请先将其他模型设为默认"));
            assertEquals("Connection failed: timeout", service.resolveMessage(ApiResponse.Code.SYSTEM_ERROR, "连接失败: timeout"));
            LanguageContext.setLocale("zh-CN");
            assertEquals("系统默认模型不能停用，请先将其他模型设为默认", service.resolveMessage(ApiResponse.Code.PARAM_ERROR, "系统默认模型不能停用，请先将其他模型设为默认"));
        } finally { LanguageContext.clear(); }
    }
}
