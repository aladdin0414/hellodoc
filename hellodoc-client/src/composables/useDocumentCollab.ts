import { computed, onUnmounted, ref, watch, type Ref } from 'vue'
import { message } from '../utils/message'
import { i18n } from '../i18n'

type CollabConnState = 'disconnected' | 'connecting' | 'connected' | 'error'

interface CollabUser {
    sessionId: string
    userId: number
    username?: string
    userColor?: string
}

interface CollabLock {
    id?: number
    docId: number
    userId: number
    sessionId: string
    lockType: string
    blockId?: string | null
    expiresAt?: string | null
}

interface CollabDoc {
    id?: number
    type?: string
}

interface UseDocumentCollabOptions {
    currentDoc: Ref<CollabDoc | null>
    isCollabFeatureEnabled: Ref<boolean>
    onSaveBeforeUnlock: () => Promise<boolean>
    onSyncFromServer: () => Promise<unknown>
}

const normalizeNotificationParams = (params: unknown): Record<string, unknown> => {
    if (params && typeof params === 'object' && !Array.isArray(params)) {
        return params as Record<string, unknown>
    }
    return {}
}

const resolveI18nText = (key: unknown, params: Record<string, unknown>, fallback: string = '') => {
    if (typeof key !== 'string' || !key) return fallback
    const i18nGlobal = i18n.global as any
    if (typeof i18nGlobal.te === 'function' && !i18nGlobal.te(key)) {
        return fallback
    }
    const translated = i18n.global.t(key, params as any)
    const content = typeof translated === 'string' ? translated : String(translated || '')
    return content || fallback
}

const formatNotificationText = (data: any) => {
    const params = normalizeNotificationParams(data?.params)
    const title = resolveI18nText(data?.titleKey, params, '')
    const content = resolveI18nText(
        data?.contentKey,
        params,
        typeof data?.content === 'string' ? data.content : ''
    )
    if (title && content) return `${title}: ${content}`
    if (content) return content
    if (title) return title
    if (typeof data?.content === 'string' && data.content.trim()) return data.content.trim()
    return i18n.global.t('notification.common.content')
}

export const useDocumentCollab = (options: UseDocumentCollabOptions) => {
    const collabState = ref<CollabConnState>('disconnected')
    const collabError = ref<string | null>(null)
    const collabWs = ref<WebSocket | null>(null)
    const collabDocId = ref<number | null>(null)
    const collabWsSeq = ref(0)
    const collabReconnectAttempts = ref(0)
    let collabHeartbeatTimer: ReturnType<typeof setInterval> | null = null
    let collabReconnectTimer: ReturnType<typeof setTimeout> | null = null

    const selfSessionId = ref<string | null>(null)
    const activeEditors = ref<CollabUser[]>([])
    const locks = ref<CollabLock[]>([])
    const lockSyncing = ref(false)

    const docLock = computed(() => locks.value.find(l => l.lockType === 'DOCUMENT') || null)
    const isLockedByMe = computed(() => !!docLock.value && !!selfSessionId.value && docLock.value.sessionId === selfSessionId.value)
    const isCollabConnected = computed(() => collabState.value === 'connected')
    const isReadOnlyByCollab = computed(() => {
        if (!options.isCollabFeatureEnabled.value) return false
        if (options.currentDoc.value?.type !== 'file') return false
        if (!isCollabConnected.value) return true
        if (lockSyncing.value) return true
        if (!docLock.value) return true
        return !isLockedByMe.value
    })

    const lockOwnerLabel = computed(() => {
        if (!docLock.value) return ''
        const bySession = activeEditors.value.find(u => u.sessionId === docLock.value?.sessionId)
        const byUserId = activeEditors.value.find(u => u.userId === docLock.value?.userId)
        const user = bySession || byUserId
        if (user?.username) return user.username
        return i18n.global.t('editor.userWithId', { id: user?.userId || docLock.value.userId })
    })

    const collabStateLabel = computed(() => {
        if (collabState.value === 'connected') return i18n.global.t('editor.collabOnline', { count: activeEditors.value.length })
        if (collabState.value === 'connecting') return i18n.global.t('editor.collabConnecting')
        if (collabState.value === 'error') {
            return collabError.value
                ? i18n.global.t('editor.collabErrorWithCode', { code: collabError.value })
                : i18n.global.t('editor.collabError')
        }
        return i18n.global.t('editor.collabDisconnected')
    })

    const collabStateDotClass = computed(() => {
        if (collabState.value === 'connected') return 'bg-emerald-500'
        if (collabState.value === 'connecting') return 'bg-amber-500'
        if (collabState.value === 'error') return 'bg-rose-500'
        return 'bg-slate-300'
    })

    const sendCollab = (payload: any) => {
        const ws = collabWs.value
        if (!ws || ws.readyState !== WebSocket.OPEN) return
        ws.send(JSON.stringify(payload))
    }

    const stopCollabTimers = () => {
        if (collabHeartbeatTimer) {
            clearInterval(collabHeartbeatTimer)
            collabHeartbeatTimer = null
        }
        if (collabReconnectTimer) {
            clearTimeout(collabReconnectTimer)
            collabReconnectTimer = null
        }
    }

    const closeCollab = () => {
        stopCollabTimers()
        collabWsSeq.value += 1
        const ws = collabWs.value
        collabWs.value = null
        collabDocId.value = null
        if (ws && ws.readyState === WebSocket.OPEN) {
            try {
                const lock = locks.value.find(l => l.lockType === 'DOCUMENT') || null
                if (lock && selfSessionId.value && lock.sessionId === selfSessionId.value) {
                    ws.send(JSON.stringify({ type: 'unlock', data: { lockType: 'DOCUMENT' } }))
                }
            } catch { }
        }
        if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
            try {
                ws.close()
            } catch { }
        }
        collabState.value = 'disconnected'
        collabError.value = null
        selfSessionId.value = null
        activeEditors.value = []
        locks.value = []
        collabReconnectAttempts.value = 0
    }

    const buildCollabWsUrl = (docId: number) => {
        const token = localStorage.getItem('accessToken') || ''
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
        return `${protocol}//${window.location.host}/ws/doc/${docId}?token=${encodeURIComponent(token)}`
    }

    const connectCollab = (docId: number) => {
        if (!options.isCollabFeatureEnabled.value) return
        if (collabDocId.value === docId) {
            const ws = collabWs.value
            if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
                return
            }
        }

        closeCollab()
        const token = localStorage.getItem('accessToken')
        if (!token) {
            collabState.value = 'error'
            collabError.value = 'NO_TOKEN'
            return
        }

        collabState.value = 'connecting'
        collabDocId.value = docId
        const seq = ++collabWsSeq.value
        const ws = new WebSocket(buildCollabWsUrl(docId))
        collabWs.value = ws

        ws.onopen = () => {
            if (seq !== collabWsSeq.value) return
            collabState.value = 'connected'
            collabError.value = null
            collabReconnectAttempts.value = 0
            sendCollab({ type: 'join', data: {} })
            collabHeartbeatTimer = setInterval(() => {
                sendCollab({ type: 'heartbeat', data: {} })
            }, 10000)
        }

        ws.onmessage = (evt) => {
            if (seq !== collabWsSeq.value) return
            try {
                const msg = JSON.parse(evt.data || '{}')
                const type = msg.type
                const data = msg.data || {}
                if (type === 'notification') {
                    message.info(formatNotificationText(data))
                    return
                }
                if (type === 'sync' || type === 'reconnect_ack') {
                    selfSessionId.value = data.selfSessionId || null
                    activeEditors.value = Array.isArray(data.activeEditors) ? data.activeEditors : []
                    locks.value = Array.isArray(data.locks) ? data.locks : []
                    return
                }
                if (type === 'presence') {
                    if (data.type === 'join' && data.sessionId && data.user) {
                        const u: CollabUser = {
                            sessionId: String(data.sessionId),
                            userId: Number(data.user.id),
                            username: data.user.name ? String(data.user.name) : undefined,
                            userColor: data.user.color ? String(data.user.color) : undefined
                        }
                        const idx = activeEditors.value.findIndex(x => x.sessionId === u.sessionId)
                        if (idx >= 0) activeEditors.value[idx] = u
                        else activeEditors.value = [...activeEditors.value, u]
                    }
                    if (data.type === 'leave' && data.sessionId) {
                        const sid = String(data.sessionId)
                        activeEditors.value = activeEditors.value.filter(u => u.sessionId !== sid)
                    }
                    return
                }
                if (type === 'lock_state') {
                    locks.value = Array.isArray(data.locks) ? data.locks : []
                    return
                }
                if (type === 'error') {
                    const code = data.code ? String(data.code) : 'ERROR'
                    if (code === 'LOCKED') {
                        message.warning(i18n.global.t('editor.readOnlyByOther'))
                    }
                    return
                }
            } catch (e) {
                console.error('Collab message parse failed:', e)
            }
        }

        ws.onclose = (evt) => {
            if (seq !== collabWsSeq.value) return
            stopCollabTimers()
            if (collabWs.value === ws) {
                collabWs.value = null
            }
            try {
                const code = (evt as CloseEvent)?.code
                if (code && code !== 1000) {
                    collabError.value = `WS_CLOSE_${code}`
                }
            } catch { }
            if (options.currentDoc.value?.id !== docId || options.currentDoc.value?.type !== 'file') {
                collabState.value = 'disconnected'
                return
            }
            const closeCode = (evt as CloseEvent)?.code
            if (closeCode === 1000 || closeCode === 1001 || closeCode === 1003 || closeCode === 1007 || closeCode === 1008) {
                collabState.value = 'error'
                return
            }
            collabState.value = 'connecting'
            const attempt = collabReconnectAttempts.value + 1
            collabReconnectAttempts.value = attempt
            if (attempt >= 5) {
                collabState.value = 'error'
                collabError.value = collabError.value || 'RETRY_LIMIT'
                return
            }
            const delay = Math.min(1000 * Math.pow(2, Math.min(attempt, 4)), 10000)
            collabReconnectTimer = setTimeout(() => {
                connectCollab(docId)
            }, delay)
        }

        ws.onerror = () => {
            if (seq !== collabWsSeq.value) return
            collabState.value = 'error'
            collabError.value = 'WS_ERROR'
        }
    }

    const requestEditLock = () => {
        if (!options.currentDoc.value?.id || options.currentDoc.value.type !== 'file') return
        if (!isCollabConnected.value) {
            message.warning(i18n.global.t('editor.collabNotReady'))
            return
        }
        sendCollab({ type: 'lock', data: { lockType: 'DOCUMENT' } })
    }

    const releaseEditLock = async () => {
        if (!options.currentDoc.value?.id || options.currentDoc.value.type !== 'file') return
        if (!isCollabConnected.value) return
        if (lockSyncing.value) return
        if (isReadOnlyByCollab.value) {
            message.warning(i18n.global.t('editor.readOnlyCannotRelease'))
            return
        }

        const ok = await options.onSaveBeforeUnlock()
        if (!ok) return
        sendCollab({ type: 'unlock', data: { lockType: 'DOCUMENT' } })
    }

    const reconnectCollabNow = () => {
        if (!options.currentDoc.value?.id || options.currentDoc.value.type !== 'file') return
        connectCollab(options.currentDoc.value.id)
    }

    watch(isReadOnlyByCollab, (val) => {
        if (val) {
            const active = document.activeElement as HTMLElement | null
            active?.blur?.()
        }
    })

    watch(isLockedByMe, async (val, oldVal) => {
        if (val && !oldVal) {
            lockSyncing.value = true
            try {
                await options.onSyncFromServer()
            } finally {
                lockSyncing.value = false
            }
        }
    })

    watch(
        () => docLock.value?.sessionId ?? null,
        async (newSid, oldSid) => {
            if (newSid === oldSid) return
            if (!options.currentDoc.value?.id || options.currentDoc.value.type !== 'file') return
            if (isLockedByMe.value) return
            await options.onSyncFromServer()
        }
    )

    watch(
        () => [options.isCollabFeatureEnabled.value, options.currentDoc.value?.id, options.currentDoc.value?.type] as const,
        ([enabled, docId, docType]) => {
            if (!enabled) {
                closeCollab()
                return
            }
            if (docType === 'file' && typeof docId === 'number') {
                connectCollab(docId)
                return
            }
            closeCollab()
        },
        { immediate: true }
    )

    onUnmounted(() => {
        closeCollab()
    })

    return {
        collabState,
        activeEditors,
        docLock,
        isLockedByMe,
        isReadOnlyByCollab,
        lockOwnerLabel,
        collabStateLabel,
        collabStateDotClass,
        requestEditLock,
        releaseEditLock,
        reconnectCollabNow,
        closeCollab
    }
}
