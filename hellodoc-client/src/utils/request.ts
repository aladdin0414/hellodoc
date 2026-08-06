import axios from 'axios'
import { message } from './message'
import { i18n } from '../i18n'
import { buildCommonRequestHeaders } from './requestHeaders'

const request = axios.create({
    baseURL: '', // Using proxy
    timeout: 10000
})

let isRefreshing = false
let requestsQueue: ((token: string) => void)[] = []

const ERROR_I18N_MAP: Record<string, string> = {
    '401': 'error.unauthorized',
    '403': 'error.noPermission',
    '1001': 'error.usernameOrPasswordError',
    '1002': 'error.accountDisabled',
    '1003': 'error.usernameConflict',
    '1004': 'error.resourceNotFound',
    '1005': 'error.tokenInvalid',
    '1006': 'error.tokenTypeError',
    '1007': 'error.paramError',
    '1008': 'error.oldPasswordWrong',
    '1009': 'error.invalidRequest',
    '1010': 'error.uploadFileRequired',
    '1011': 'error.uploadImageOnly',
    '1012': 'error.uploadAvatarTooLarge',
    '1013': 'error.uploadAvatarFailed',
    '9999': 'error.systemError'
}

const resolveErrorMessage = (code: unknown, fallbackMessage?: string) => {
    if (String(code) === '9999' && fallbackMessage && fallbackMessage !== '系统异常') {
        return fallbackMessage
    }
    const key = ERROR_I18N_MAP[String(code)]
    if (key) {
        return i18n.global.t(key)
    }
    if (fallbackMessage) {
        return fallbackMessage
    }
    return i18n.global.t('error.unknown')
}

function subscribeTokenRefresh(cb: (token: string) => void) {
    requestsQueue.push(cb)
}

function onRefreshed(token: string) {
    requestsQueue.map(cb => cb(token))
    requestsQueue = []
}

const clearAuthTokens = () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userRole')
    localStorage.removeItem('nickname')
    localStorage.removeItem('username')
    localStorage.removeItem('avatar')
    localStorage.removeItem('email')
}

// Request interceptor
request.interceptors.request.use(
    config => {
        const commonHeaders = buildCommonRequestHeaders()
        for (const [key, value] of Object.entries(commonHeaders)) {
            config.headers[key] = value
        }
        if (config.data instanceof FormData) {
            config.timeout = 0
        }
        return config
    },
    error => {
        return Promise.reject(error)
    }
)

// Response interceptor
request.interceptors.response.use(
    response => {
        const res = response.data
        // Handle Blob/File response
        if (response.config.responseType === 'blob' || res instanceof Blob) {
            return res
        }

        // Assuming backend returns ApiResponse { code, data, message: msg }
        if (res.code !== 200 && res.code !== 0) {
            const msg = resolveErrorMessage(res.code, res.message)
            message.error(msg)

            // 如果后端返回 Token 失效/错误码，自动清除 Token 并重定向至登录页
            if (res.code === 1005 || res.code === 1006 || res.code === 401 || res.code === 403) {
                clearAuthTokens()
                const loginPath = window.location.pathname.startsWith('/m') ? '/m/login' : '/login'
                if (!window.location.pathname.includes('/login')) {
                    window.location.href = loginPath
                }
            }

            return Promise.reject(new Error(msg))
        } else {
            return res.data
        }
    },
    async error => {
        const { response, config } = error
        
        // 当返回 401 或 403 拒绝访问时处理 Token 失效
        if ((response?.status === 401 || response?.status === 403) && !config._retry) {
            if (response?.status === 401) {
                if (isRefreshing) {
                    return new Promise(resolve => {
                        subscribeTokenRefresh(token => {
                            config.headers['Authorization'] = `Bearer ${token}`
                            resolve(request(config))
                        })
                    })
                }

                config._retry = true
                const refreshToken = localStorage.getItem('refreshToken')
                if (refreshToken) {
                    isRefreshing = true
                    try {
                        const res = await axios.post(
                            '/api/auth/refresh-token',
                            { refreshToken },
                            { headers: buildCommonRequestHeaders({ includeAuth: false, includeContentTypeJson: true }) }
                        )
                        if (res.data.code === 200 || res.data.code === 0) {
                            const { accessToken, refreshToken: newRefreshToken } = res.data.data
                            localStorage.setItem('accessToken', accessToken)
                            if (newRefreshToken) localStorage.setItem('refreshToken', newRefreshToken)

                            isRefreshing = false
                            onRefreshed(accessToken)

                            config.headers['Authorization'] = `Bearer ${accessToken}`
                            return request(config)
                        }
                    } catch (refreshError) {
                        isRefreshing = false
                        requestsQueue = []
                        console.error('Refresh token failed:', refreshError)
                    }
                }
            }

            // 清理本地所有凭据，当未在登录页时才重定向
            clearAuthTokens()
            const isAlreadyOnLogin = window.location.pathname.includes('/login')
            if (!isAlreadyOnLogin) {
                const currentPath = window.location.pathname + window.location.search
                const loginPath = window.location.pathname.startsWith('/m') ? '/m/login' : '/login'
                window.location.href = `${loginPath}?redirect=${encodeURIComponent(currentPath)}`
            }
            return Promise.reject(error)
        }

        if (axios.isCancel(error)) {
            return Promise.reject({ _isCancel: true })
        }

        if (!response) {
            message.error(i18n.global.t('error.networkError') || '网络连接异常，请检查网络设置')
        } else if (response.status >= 500) {
            message.error(i18n.global.t('error.systemError') || '服务器异常，请稍后再试')
        }

        console.error('API Error:', error)
        return Promise.reject(error)
    }
)

export default request
