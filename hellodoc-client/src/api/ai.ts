import request from '../utils/request'
import { buildCommonRequestHeaders } from '../utils/requestHeaders'
import axios from 'axios'

export interface AiChatMessage {
    role: 'user' | 'assistant'
    content: string
}

export interface AiCompletionReq {
    modelId?: string
    context: string
    prompt: string
    history?: AiChatMessage[]
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

const refreshAccessTokenIfNeeded = async (signal?: AbortSignal): Promise<boolean> => {
    const refreshToken = (localStorage.getItem('refreshToken') || '').trim()
    if (!refreshToken) return false

    try {
        const resp = await axios.post<RefreshTokenApiResp>(
            '/api/auth/refresh-token',
            { refreshToken },
            {
                timeout: 10000,
                signal,
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

interface AiStreamOptions {
    signal?: AbortSignal
    timeoutMs?: number
}

export const aiCompletionStream = async (data: AiCompletionReq, handlers: AiCompletionStreamHandlers, options: AiStreamOptions = {}): Promise<void> => {
    const controller = new AbortController()
    const abort = () => controller.abort(options.signal?.reason)
    if (options.signal?.aborted) abort()
    else options.signal?.addEventListener('abort', abort, { once: true })
    const timeout = setTimeout(() => controller.abort(new DOMException('AI 请求超时，请重试', 'TimeoutError')), options.timeoutMs ?? 120000)
    let reader: ReadableStreamDefaultReader<Uint8Array> | undefined
    try {
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
                signal: controller.signal,
            })

        let response = await doStreamRequest(createHeaders())
        if (response.status === 401 || response.status === 403) {
            const refreshed = await refreshAccessTokenIfNeeded(controller.signal)
            if (refreshed) {
                response = await doStreamRequest(createHeaders())
            }
        }

        controller.signal.throwIfAborted()
        if (!response.ok || !response.body) {
            let detail = ''
            try {
                detail = await response.text()
            } catch {
                // ignore parse errors
            }
            throw new Error(`AI stream failed: ${response.status}${detail ? ` ${detail}` : ''}`)
        }

        reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let completed = false

        const processEvent = (block: string) => {
            const lines = block.split('\n')
            let eventName = 'message'
            const dataLines: string[] = []
            for (const line of lines) {
                if (line.startsWith('event:')) {
                    eventName = line.slice(6).trim()
                } else if (line.startsWith('data:')) {
                    let content = line.slice(5)
                    // 若为标准 SSE 格式 (data: <text>)，仅在确定是纯文本且由单个空格前导时剥离该协议空格
                    // 若内容为 JSON 格式（如 "abc"、{"text":...}），则完整保留
                    if (content.startsWith(' ') && !content.startsWith(' "') && !content.startsWith(' {') && !content.startsWith(' [')) {
                        content = content.slice(1)
                    }
                    dataLines.push(content)
                }
            }
            const payload = dataLines.join('\n')
            if (eventName === 'chunk') {
                let chunkText = payload
                try {
                    const parsed = JSON.parse(payload)
                    if (typeof parsed === 'string') {
                        chunkText = parsed
                    }
                } catch {
                    // 兼容非 JSON 传输
                }
                if (chunkText.length > 0) {
                    handlers.onChunk(chunkText)
                }
                return
            }
            if (!payload) return
            if (eventName === 'model') {
                handlers.onModel?.(payload)
                return
            }
            if (eventName === 'error') {
                throw new Error(payload)
            }
            if (eventName === 'done') {
                completed = true
                handlers.onDone?.()
            }
        }

        while (!completed) {
            const { done, value } = await reader.read()
            buffer += done ? decoder.decode() : decoder.decode(value, { stream: true })
            buffer = buffer.replace(/\r\n/g, '\n')
            let idx = buffer.indexOf('\n\n')
            while (idx >= 0 && !completed) {
                const block = buffer.slice(0, idx)
                buffer = buffer.slice(idx + 2)
                if (block.trim()) processEvent(block)
                idx = buffer.indexOf('\n\n')
            }
            if (done) {
                if (!completed && buffer.trim()) processEvent(buffer)
                if (!completed) throw new Error('AI 响应中断，未收到完整结果，请重试')
            }
        }
    } catch (error) {
        if (controller.signal.aborted) throw controller.signal.reason
        throw error
    } finally {
        clearTimeout(timeout)
        options.signal?.removeEventListener('abort', abort)
        if (reader) {
            await reader.cancel().catch(() => {})
            reader.releaseLock()
        }
        controller.abort()
    }
}
