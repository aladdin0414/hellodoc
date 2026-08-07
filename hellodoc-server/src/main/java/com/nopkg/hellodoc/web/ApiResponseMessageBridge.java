package com.nopkg.hellodoc.web;

import com.nopkg.hellodoc.i18n.ApiI18nService;
import org.springframework.stereotype.Component;

@Component
public class ApiResponseMessageBridge {

    private static ApiI18nService apiI18nService;

    public ApiResponseMessageBridge(ApiI18nService apiI18nService) {
        ApiResponseMessageBridge.apiI18nService = apiI18nService;
    }

    public static String resolveCodeMessage(ApiResponse.Code code) {
        if (apiI18nService == null) {
            return code.message();
        }
        return apiI18nService.resolveCodeMessage(code);
    }

    public static String resolveMessage(ApiResponse.Code code, String rawMessage) {
        if (apiI18nService == null) {
            return rawMessage;
        }
        return apiI18nService.resolveMessage(code, rawMessage);
    }
}
