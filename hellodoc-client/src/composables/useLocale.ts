import { computed, ref } from 'vue'
import { i18n, resolveLocale, type LocaleMode, type SupportedLocale, normalizeLocaleMode } from '../i18n'
import { updateLanguageMode } from '../api/user'
import { message } from '../utils/message'

const LOCALE_MODE_KEY = 'localeMode'
const localeMode = ref<LocaleMode>(normalizeLocaleMode(localStorage.getItem(LOCALE_MODE_KEY)))

const applyLocaleFromMode = (mode: LocaleMode) => {
    const locale = resolveLocale(mode, navigator.language)
    i18n.global.locale.value = locale
}

applyLocaleFromMode(localeMode.value)

export const setLocaleModeLocal = (mode: LocaleMode) => {
    localeMode.value = mode
    localStorage.setItem(LOCALE_MODE_KEY, mode)
    applyLocaleFromMode(mode)
}

export function useLocale() {
    const effectiveLocale = computed<SupportedLocale>(() => resolveLocale(localeMode.value, navigator.language))

    const setLocaleMode = async (mode: LocaleMode) => {
        if (localeMode.value === mode) {
            return
        }
        const previous = localeMode.value
        setLocaleModeLocal(mode)
        const token = localStorage.getItem('accessToken')
        if (!token) {
            return
        }
        try {
            await updateLanguageMode({ languageMode: mode })
        } catch {
            setLocaleModeLocal(previous)
            message.error(i18n.global.t('locale.saveError'))
            throw new Error('save language mode failed')
        }
    }

    return {
        localeMode,
        effectiveLocale,
        setLocaleMode
    }
}
