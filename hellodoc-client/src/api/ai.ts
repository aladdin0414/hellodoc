import request from '../utils/request'
import { buildCommonRequestHeaders } from '../utils/requestHeaders'
import axios from 'axios'

export interface AiCompletionReq {
    context: string
    prompt: string
}

export interface AiCompletionResp {
    result: string
    model: string
}

interface RefreshTokenApiResp {
    code: number
    data?: {
        accessToken: string
        refreshToken?: string
    }
    message?: string
}

export const aiCompletion = async (data: AiCompletionReq): Promise<AiCompletionResp> => {
    return request.post('/api/ai/completion', data, { timeout: 60000 })
}

interface AiCompletionStreamHandlers {
    onModel?: (model: string) => void
    onChunk: (chunk: string) => void
    onDone?: () => void
}

const refreshAccessTokenIfNeeded = async (): Promise<boolean> => {
    const refreshToken = (localStorage.getItem('refreshToken') || '').trim()
    if (!refreshToken) return false

    try {
        const resp = await axios.post<RefreshTokenApiResp>(
            '/api/auth/refresh-token',
            { refreshToken },
            {
                headers: buildCommonRequestHeaders({
                    includeAuth: false,
                    includeContentTypeJson: true,
                }),
            }
        )

        const payload = resp.data
        if (payload?.code !== 200 && payload?.code !== 0) {
            return false
        }

        const newAccessToken = payload.data?.accessToken
        const newRefreshToken = payload.data?.refreshToken
        if (!newAccessToken) return false

        localStorage.setItem('accessToken', newAccessToken)
        if (newRefreshToken) {
            localStorage.setItem('refreshToken', newRefreshToken)
        }
        return true
    } catch {
        return false
    }
}

export const aiCompletionStream = async (data: AiCompletionReq, handlers: AiCompletionStreamHandlers): Promise<void> => {
    const createHeaders = () =>
        buildCommonRequestHeaders({
            requireAuth: true,
            includeContentTypeJson: true,
        })

    const doStreamRequest = (headers: Record<string, string>) =>
        fetch('/api/ai/completion/stream', {
            method: 'POST',
            headers,
            body: JSON.stringify(data),
        })

    let response = await doStreamRequest(createHeaders())
    if (response.status === 401 || response.status === 403) {
        const refreshed = await refreshAccessTokenIfNeeded()
        if (refreshed) {
            response = await doStreamRequest(createHeaders())
        }
    }

    if (!response.ok || !response.body) {
        let detail = ''
        try {
            detail = await response.text()
        } catch {
            // ignore parse errors
        }
        throw new Error(`AI stream failed: ${response.status}${detail ? ` ${detail}` : ''}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    const processEvent = (block: string) => {
        const lines = block.split('\n')
        let eventName = 'message'
        const dataLines: string[] = []
        for (const line of lines) {
            if (line.startsWith('event:')) {
                eventName = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
                dataLines.push(line.slice(5).trimStart())
            }
        }
        const payload = dataLines.join('\n')
        if (!payload) return
        if (eventName === 'model') {
            handlers.onModel?.(payload)
            return
        }
        if (eventName === 'chunk') {
            handlers.onChunk(payload)
            return
        }
        if (eventName === 'error') {
            throw new Error(payload)
        }
        if (eventName === 'done') {
            handlers.onDone?.()
        }
    }

    while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        buffer = buffer.replace(/\r\n/g, '\n')
        let idx = buffer.indexOf('\n\n')
        while (idx >= 0) {
            const block = buffer.slice(0, idx)
            buffer = buffer.slice(idx + 2)
            if (block.trim()) {
                processEvent(block)
            }
            idx = buffer.indexOf('\n\n')
        }
    }
}
