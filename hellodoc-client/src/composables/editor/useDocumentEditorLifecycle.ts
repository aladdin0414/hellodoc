import { onMounted, onBeforeUnmount, type Ref } from 'vue'
import { buildCommonRequestHeaders } from '../../utils/requestHeaders'

interface LifecycleDoc {
    id: number
    name: string
    content: string
    type: string
    paperBgColor?: string
    paperBgImage?: string
}

interface UseDocumentEditorLifecycleOptions {
    kbId: Ref<number>
    currentDoc: Ref<LifecycleDoc | null>
    hasUnsavedChanges: Ref<boolean>
    saveDraftToLocal: (doc?: LifecycleDoc | null) => void
    flushSaveAndDraft: () => void
    closeCollab: () => void
    updateIsMobile: () => void
    closeAllMenus: () => void
    handleSave: () => void
    cleanupUiState: () => void
    clearSaveTimers: () => void
}

export const useDocumentEditorLifecycle = (options: UseDocumentEditorLifecycleOptions) => {
    const handleKeyDown = (e: KeyboardEvent) => {
        const isMod = e.metaKey || e.ctrlKey
        if (isMod && e.key === 's') {
            e.preventDefault()
            options.handleSave()
        }
    }

    const handleVisibilityChange = () => {
        if (document.visibilityState === 'hidden') {
            options.flushSaveAndDraft()
        }
    }

    const handlePageHide = () => {
        options.flushSaveAndDraft()
    }

    const handleBeforeUnload = () => {
        if (options.hasUnsavedChanges.value && options.currentDoc.value) {
            options.saveDraftToLocal(options.currentDoc.value)

            try {
                const snapshot = {
                    name: options.currentDoc.value.name,
                    content: options.currentDoc.value.content,
                    type: options.currentDoc.value.type,
                    paperBgColor: options.currentDoc.value.paperBgColor,
                    paperBgImage: options.currentDoc.value.paperBgImage
                }
                const url = `/api/kb/${options.kbId.value}/documents/${options.currentDoc.value.id}`
                if (typeof fetch === 'function') {
                    fetch(url, {
                        method: 'PUT',
                        headers: buildCommonRequestHeaders({ includeContentTypeJson: true }),
                        body: JSON.stringify(snapshot),
                        keepalive: true
                    }).catch(() => { })
                }
            } catch (e) {
                console.error('Failed to sync on unload', e)
            }
        }
        options.closeCollab()
    }

    onMounted(() => {
        options.updateIsMobile()
        window.addEventListener('resize', options.updateIsMobile)
        window.addEventListener('click', options.closeAllMenus)
        window.addEventListener('keydown', handleKeyDown)
        document.addEventListener('visibilitychange', handleVisibilityChange)
        window.addEventListener('pagehide', handlePageHide)
        window.addEventListener('beforeunload', handleBeforeUnload)
    })

    onBeforeUnmount(() => {
        options.flushSaveAndDraft()
        window.removeEventListener('resize', options.updateIsMobile)
        window.removeEventListener('click', options.closeAllMenus)
        window.removeEventListener('keydown', handleKeyDown)
        document.removeEventListener('visibilitychange', handleVisibilityChange)
        window.removeEventListener('pagehide', handlePageHide)
        window.removeEventListener('beforeunload', handleBeforeUnload)
        options.cleanupUiState()
        options.clearSaveTimers()
        options.closeCollab()
    })
}
