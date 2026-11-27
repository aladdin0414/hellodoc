export type KbEditorTheme = 'default' | 'geist'

export interface KbEditorThemeOption {
    id: KbEditorTheme
    dotColor: string
    nameKey: string
    descKey: string
}

// 知识库编辑风格选项：当前开放默认风格与 Geist 黑白极简风格
export const KB_EDITOR_THEMES: KbEditorThemeOption[] = [
    {
        id: 'default',
        dotColor: '#64748B',
        nameKey: 'kb.editorThemeDefault',
        descKey: 'kb.editorThemeDefaultDesc'
    },
    {
        id: 'geist',
        dotColor: '#111111',
        nameKey: 'kb.editorThemeGeist',
        descKey: 'kb.editorThemeGeistDesc'
    }
]

export function normalizeKbEditorTheme(theme?: string | null): KbEditorTheme {
    if (!theme) return 'default'
    const normalized = theme.trim().toLowerCase()
    const matched = KB_EDITOR_THEMES.find(item => item.id === normalized)
    return matched ? matched.id : 'default'
}

const KB_EDITOR_THEME_CACHE_PREFIX = 'hellodoc_kb_editor_theme:'

// 同步读取知识库编辑风格缓存，消除异步请求完成前的主题闪烁
export function getCachedKbEditorTheme(kbId?: number | null): KbEditorTheme | undefined {
    if (!kbId || typeof window === 'undefined') return undefined
    try {
        const raw = window.localStorage.getItem(`${KB_EDITOR_THEME_CACHE_PREFIX}${kbId}`)
        if (!raw) return undefined
        return normalizeKbEditorTheme(raw)
    } catch {
        return undefined
    }
}

export function setCachedKbEditorTheme(kbId?: number | null, theme?: string | null): void {
    if (!kbId || typeof window === 'undefined') return
    try {
        window.localStorage.setItem(
            `${KB_EDITOR_THEME_CACHE_PREFIX}${kbId}`,
            normalizeKbEditorTheme(theme)
        )
    } catch {
        // 忽略存储空间或隐私模式限制
    }
}
