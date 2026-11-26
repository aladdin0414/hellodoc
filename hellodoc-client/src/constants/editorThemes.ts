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
