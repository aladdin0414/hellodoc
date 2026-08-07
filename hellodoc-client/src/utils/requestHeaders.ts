import { i18n } from '../i18n'

interface BuildCommonRequestHeadersOptions {
    requireAuth?: boolean
    includeContentTypeJson?: boolean
    includeAuth?: boolean
}

export const buildCommonRequestHeaders = (
    options: BuildCommonRequestHeadersOptions = {}
): Record<string, string> => {
    const headers: Record<string, string> = {}
    const token = (localStorage.getItem('accessToken') || '').trim()
    const includeAuth = options.includeAuth !== false

    if (options.requireAuth && !token) {
        throw new Error(i18n.global.t('auth.loginExpired') || 'Not logged in or session expired')
    }
    if (includeAuth && token) {
        headers.Authorization = `Bearer ${token}`
    }

    const customBackend = (localStorage.getItem('customBackendUrl') || '').trim()
    if (customBackend) {
        headers['X-Backend-Url'] = customBackend
    }

    const locale = i18n.global.locale.value || localStorage.getItem('locale') || 'zh-CN'
    headers['X-Language'] = locale
    headers['Accept-Language'] = locale

    if (options.includeContentTypeJson) {
        headers['Content-Type'] = 'application/json'
    }

    return headers
}
