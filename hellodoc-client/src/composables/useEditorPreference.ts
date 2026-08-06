import { ref } from 'vue'

export type EditorType = 'markdown' | 'visual'

const EDITOR_PREFERENCE_KEY = 'app.editor.preference'
const DEFAULT_EDITOR_TYPE: EditorType = 'visual'

const normalizeEditorType = (value: string | null): EditorType => {
    if (value === 'markdown' || value === 'visual') return value
    return DEFAULT_EDITOR_TYPE
}

// 全局单例状态
const editorType = ref<EditorType>(normalizeEditorType(localStorage.getItem(EDITOR_PREFERENCE_KEY)))

export function useEditorPreference() {
    const setEditorType = (type: EditorType) => {
        editorType.value = type
        localStorage.setItem(EDITOR_PREFERENCE_KEY, type)
    }

    const toggleEditorType = () => {
        setEditorType(editorType.value === 'markdown' ? 'visual' : 'markdown')
    }

    return {
        editorType,
        setEditorType,
        toggleEditorType
    }
}
