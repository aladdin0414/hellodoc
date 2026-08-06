import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN/common'
import enUS from './locales/en-US/common'

export type SupportedLocale = 'zh-CN' | 'en-US'
export type LocaleMode = 'AUTO' | SupportedLocale

const SUPPORTED_LOCALES: SupportedLocale[] = ['zh-CN', 'en-US']
const DEFAULT_LOCALE: SupportedLocale = 'zh-CN'
const FALLBACK_LOCALE: SupportedLocale = 'en-US'

export const resolveLocale = (mode: LocaleMode, browserLang: string): SupportedLocale => {
    if (mode === 'zh-CN' || mode === 'en-US') {
        return mode
    }
    const normalized = String(browserLang || '').toLowerCase()
    if (normalized.startsWith('zh')) {
        return 'zh-CN'
    }
    return 'en-US'
}

export const normalizeLocaleMode = (value: unknown): LocaleMode => {
    if (value === 'AUTO' || value === 'zh-CN' || value === 'en-US') {
        return value
    }
    return 'AUTO'
}

const initialMode = normalizeLocaleMode(localStorage.getItem('localeMode'))
const initialLocale = resolveLocale(initialMode, navigator.language)

export const i18n = createI18n({
    legacy: false,
    locale: initialLocale,
    fallbackLocale: FALLBACK_LOCALE,
    messages: {
        'zh-CN': zhCN,
        'en-US': enUS
    }
})

export const i18nConfig = {
    SUPPORTED_LOCALES,
    DEFAULT_LOCALE,
    FALLBACK_LOCALE
}
