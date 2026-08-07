package com.nopkg.hellodoc.utils;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 后端多语言国际化消息工具类
 */
@Component
public class MessageUtils {

    private static MessageSource messageSource;

    public MessageUtils(MessageSource messageSource) {
        MessageUtils.messageSource = messageSource;
    }

    /**
     * 根据当前请求的 Locale 获取国际化消息
     *
     * @param code 消息 key
     * @param args 参数
     * @return 转换后的文本，若无对应 key 则返回 code 本身
     */
    public static String get(String code, Object... args) {
        if (messageSource == null) {
            return code;
        }
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(code, args, code, locale);
    }

    /**
     * 获取指定 Locale 的国际化消息
     */
    public static String get(Locale locale, String code, Object... args) {
        if (messageSource == null) {
            return code;
        }
        return messageSource.getMessage(code, args, code, locale != null ? locale : Locale.SIMPLIFIED_CHINESE);
    }
}
