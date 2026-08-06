export type RecentDocMode = 'view' | 'edit'

export interface RecentDocInput {
    kbId: number
    docId: number
    docName: string
    kbTitle?: string
    mode: RecentDocMode
}

const RECENT_DOCS_KEY = 'recentDocs'

export const recordRecentDoc = (input: RecentDocInput) => {
    try {
        const raw = localStorage.getItem(RECENT_DOCS_KEY)
        const list = raw ? JSON.parse(raw) : []
        const normalized = Array.isArray(list) ? list.filter((x: any) => x && typeof x === 'object') : []

        const item = {
            kbId: input.kbId,
            docId: input.docId,
            docName: input.docName,
            kbTitle: input.kbTitle || '',
            lastAccessed: Date.now(),
            mode: input.mode
        }

        const idx = normalized.findIndex((x: any) => Number(x.kbId) === input.kbId && Number(x.docId) === input.docId)
        if (idx >= 0) {
            normalized.splice(idx, 1)
        }
        normalized.unshift(item)
        localStorage.setItem(RECENT_DOCS_KEY, JSON.stringify(normalized.slice(0, 30)))
    } catch {
    }
}
