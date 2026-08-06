import { computed, ref, type Ref } from 'vue'
import { getDocumentDetail, updateDocument } from '../../api/document'
import { message } from '../../utils/message'
import { recordRecentDoc } from '../../utils/recentDocs'
import { i18n } from '../../i18n'

export interface EditorDocListItem {
    id: number
    __key?: number
    name: string
    type: string
    parentId: number | null
    orderNum: number
    status?: string
    isCover?: boolean
    paperBgColor?: string
    paperBgImage?: string
}

export interface EditorDocDetail extends EditorDocListItem {
    content: string
}

export type SaveReason = 'manual' | 'auto' | 'lifecycle'

interface LocalDraftPayload {
    name: string
    content: string
    paperBgColor?: string
    paperBgImage?: string
    savedAt: number
}

interface UseDocumentEditorCoreOptions {
    kbId: Ref<number>
    routeDocId: Ref<number | undefined>
    documents: Ref<EditorDocListItem[]>
    currentDoc: Ref<EditorDocDetail | null>
    activeDocId: Ref<number | null>
    selectedDocIds: Ref<Set<number>>
    lastClickedId: Ref<number | null>
    kbTitle: Ref<string | undefined>
    isSidebarVisible: Ref<boolean>
    isMobile: Ref<boolean>
    showDiffModal: Ref<boolean>
    showRevisions: Ref<boolean>
    navigateToDoc: (docId: number) => void
}

export const useDocumentEditorCore = (options: UseDocumentEditorCoreOptions) => {
    const docLoading = ref(false)
    const saving = ref(false)
    const originalDocContent = ref('')
    const originalDocName = ref('')
    const originalDocPaperBgColor = ref('')
    const originalDocPaperBgImage = ref('')
    const latestDocLoadRequestId = ref(0)
    const latestDocSyncRequestId = ref(0)
    const lastSavedAt = ref<number | null>(null)
    const lastSaveReason = ref<SaveReason | null>(null)
    const AUTO_SAVE_DELAY_MS = 1500
    const LOCAL_DRAFT_DELAY_MS = 500
    let autoSaveTimer: ReturnType<typeof setTimeout> | null = null
    let localDraftTimer: ReturnType<typeof setTimeout> | null = null
    let pendingSaveReason: 'manual' | 'auto' | null = null

    const hasUnsavedChanges = computed(() => {
        if (!options.currentDoc.value) return false
        return options.currentDoc.value.content !== originalDocContent.value ||
            options.currentDoc.value.name !== originalDocName.value ||
            (options.currentDoc.value.paperBgColor || '') !== originalDocPaperBgColor.value ||
            (options.currentDoc.value.paperBgImage || '') !== originalDocPaperBgImage.value
    })

    const lastSavedLabel = computed(() => {
        if (!lastSavedAt.value || !lastSaveReason.value) return ''
        const time = new Date(lastSavedAt.value).toLocaleTimeString([], { hour12: false })
        const reasonText = lastSaveReason.value === 'manual' ? i18n.global.t('editor.manualSave') : i18n.global.t('editor.autoSave')
        return i18n.global.t('editor.savedAt', { reason: reasonText, time })
    })

    const setOriginalFromDoc = (doc: EditorDocDetail | null) => {
        originalDocContent.value = doc?.content || ''
        originalDocName.value = doc?.name || ''
        originalDocPaperBgColor.value = doc?.paperBgColor || ''
        originalDocPaperBgImage.value = doc?.paperBgImage || ''
    }

    const getLocalDraftKey = (docId: number) => `hellodoc:editor-draft:${options.kbId.value}:${docId}`

    const saveDraftToLocal = (doc: Pick<EditorDocDetail, 'id' | 'name' | 'content' | 'paperBgColor' | 'paperBgImage'> | null = options.currentDoc.value) => {
        if (!doc || !doc.id) return
        try {
            const payload: LocalDraftPayload = {
                name: doc.name || '',
                content: doc.content || '',
                paperBgColor: doc.paperBgColor || '',
                paperBgImage: doc.paperBgImage || '',
                savedAt: Date.now()
            }
            localStorage.setItem(getLocalDraftKey(doc.id), JSON.stringify(payload))
        } catch (error) {
            console.error('Save local draft failed:', error)
        }
    }

    const clearLocalDraft = (docId?: number) => {
        const targetId = docId ?? options.currentDoc.value?.id
        if (!targetId) return
        try {
            localStorage.removeItem(getLocalDraftKey(targetId))
        } catch (error) {
            console.error('Clear local draft failed:', error)
        }
    }

    const restoreLocalDraftIfNeeded = (doc: EditorDocDetail) => {
        try {
            const raw = localStorage.getItem(getLocalDraftKey(doc.id))
            if (!raw) return
            const payload = JSON.parse(raw) as LocalDraftPayload
            const serverUpdatedAt = Date.parse(String((doc as any)?.updatedAt || ''))
            if (Number.isFinite(serverUpdatedAt) && (payload?.savedAt ?? 0) <= serverUpdatedAt) {
                clearLocalDraft(doc.id)
                return
            }
            const localName = payload?.name ?? ''
            const localContent = payload?.content ?? ''
            const hasPaperBgColor = Object.prototype.hasOwnProperty.call(payload, 'paperBgColor')
            const hasPaperBgImage = Object.prototype.hasOwnProperty.call(payload, 'paperBgImage')
            const localPaperBgColor = hasPaperBgColor ? (payload?.paperBgColor ?? '') : (doc.paperBgColor || '')
            const localPaperBgImage = hasPaperBgImage ? (payload?.paperBgImage ?? '') : (doc.paperBgImage || '')
            const hasDiff = localName !== (doc.name || '') ||
                localContent !== (doc.content || '') ||
                localPaperBgColor !== (doc.paperBgColor || '') ||
                localPaperBgImage !== (doc.paperBgImage || '')
            if (!hasDiff) {
                clearLocalDraft(doc.id)
                return
            }
            doc.name = localName
            doc.content = localContent
            doc.paperBgColor = localPaperBgColor
            doc.paperBgImage = localPaperBgImage
            message.warning(i18n.global.t('editor.localDraftRestored'))
        } catch (error) {
            console.error('Restore local draft failed:', error)
        }
    }

    const saveDocument = async (optionsArg?: { silent?: boolean; reason?: SaveReason }) => {
        const silent = optionsArg?.silent ?? false
        const reason = optionsArg?.reason ?? 'manual'
        const doc = options.currentDoc.value
        if (!doc) return false
        if (docLoading.value) return false
        if (!hasUnsavedChanges.value) {
            clearLocalDraft(doc.id)
            return true
        }
        if (saving.value) {
            if (reason === 'manual' || !pendingSaveReason) {
                pendingSaveReason = reason === 'lifecycle' ? 'auto' : reason
            }
            return false
        }

        const snapshot = {
            id: doc.id,
            name: doc.name,
            content: doc.content,
            type: doc.type,
            paperBgColor: doc.paperBgColor,
            paperBgImage: doc.paperBgImage
        }

        saving.value = true
        try {
            await updateDocument(options.kbId.value, snapshot.id, {
                name: snapshot.name,
                content: snapshot.content,
                type: snapshot.type,
                paperBgColor: snapshot.paperBgColor,
                paperBgImage: snapshot.paperBgImage
            })

            const index = options.documents.value.findIndex(d => d.id === snapshot.id)
            if (index !== -1 && options.documents.value[index]) {
                options.documents.value[index].name = snapshot.name
            }

            if (options.currentDoc.value && options.currentDoc.value.id === snapshot.id) {
                originalDocContent.value = snapshot.content || ''
                originalDocName.value = snapshot.name
                originalDocPaperBgColor.value = snapshot.paperBgColor || ''
                originalDocPaperBgImage.value = snapshot.paperBgImage || ''
            }
            lastSavedAt.value = Date.now()
            lastSaveReason.value = reason
            clearLocalDraft(snapshot.id)
            return true
        } catch (error) {
            console.error('Save failed:', error)
            saveDraftToLocal({
                id: snapshot.id,
                name: snapshot.name,
                content: snapshot.content,
                paperBgColor: snapshot.paperBgColor,
                paperBgImage: snapshot.paperBgImage
            })
            if (!silent) {
                message.error(i18n.global.t('editor.saveFailed'))
            }
            return false
        } finally {
            saving.value = false
            if (pendingSaveReason) {
                const nextReason = pendingSaveReason
                pendingSaveReason = null
                void saveDocument({ silent: true, reason: nextReason })
            }
        }
    }

    const loadDocumentById = async (docId: number, loadOptions?: { navigate?: boolean; optimisticActive?: boolean }) => {
        const navigate = loadOptions?.navigate ?? true
        const optimisticActive = loadOptions?.optimisticActive ?? true

        if (optimisticActive) {
            options.activeDocId.value = docId
        }
        if (navigate) {
            options.navigateToDoc(docId)
        }

        const requestId = ++latestDocLoadRequestId.value
        docLoading.value = true
        try {
            const data: any = await getDocumentDetail(options.kbId.value, docId)
            if (requestId !== latestDocLoadRequestId.value) return null
            options.currentDoc.value = data
            setOriginalFromDoc(data)
            lastSavedAt.value = null
            lastSaveReason.value = null
            recordRecentDoc({
                kbId: options.kbId.value,
                docId: Number(data.id),
                docName: String(data.name || ''),
                kbTitle: options.kbTitle.value,
                mode: 'edit'
            })
            if (options.currentDoc.value) {
                restoreLocalDraftIfNeeded(options.currentDoc.value)
            }
            return data as EditorDocDetail
        } catch (error) {
            console.error('Fetch document detail failed:', error)
            return null
        } finally {
            if (requestId === latestDocLoadRequestId.value) {
                docLoading.value = false
            }
        }
    }

    const selectDocument = async (doc: EditorDocListItem) => {
        if (doc.type === 'folder') {
            return false
        }

        if (options.activeDocId.value === doc.id && options.currentDoc.value?.id === doc.id) return true

        options.showDiffModal.value = false
        options.showRevisions.value = false

        if (options.currentDoc.value && options.currentDoc.value.id !== doc.id && hasUnsavedChanges.value) {
            saveDraftToLocal(options.currentDoc.value)
            await saveDocument({ silent: true, reason: 'lifecycle' })
        }

        options.activeDocId.value = doc.id
        if (!options.selectedDocIds.value.has(doc.id)) {
            options.selectedDocIds.value.clear()
            options.selectedDocIds.value.add(doc.id)
            options.lastClickedId.value = doc.id
        }
        if (options.isMobile.value) {
            options.isSidebarVisible.value = false
        }

        const shouldNavigate = options.routeDocId.value !== doc.id
        await loadDocumentById(doc.id, { navigate: shouldNavigate, optimisticActive: false })
        return true
    }

    const syncCurrentDocFromServer = async (syncOptions?: { silent?: boolean; force?: boolean }) => {
        const silent = syncOptions?.silent ?? true
        const force = syncOptions?.force ?? false
        const doc = options.currentDoc.value
        if (!doc || doc.type !== 'file') return false
        if (docLoading.value) return false
        if (!force && hasUnsavedChanges.value) return false

        const requestId = ++latestDocSyncRequestId.value
        try {
            const data: any = await getDocumentDetail(options.kbId.value, doc.id)
            if (requestId !== latestDocSyncRequestId.value) return false
            if (!options.currentDoc.value || options.currentDoc.value.id !== doc.id) return false
            options.currentDoc.value = data
            setOriginalFromDoc(data)
            return true
        } catch (error) {
            console.error('Sync document failed:', error)
            if (!silent) {
                message.error(i18n.global.t('editor.syncFailed'))
            }
            return false
        }
    }

    const scheduleAutoSave = () => {
        if (autoSaveTimer) clearTimeout(autoSaveTimer)
        if (!options.currentDoc.value || !hasUnsavedChanges.value) return
        autoSaveTimer = setTimeout(() => {
            void saveDocument({ silent: true, reason: 'auto' })
        }, AUTO_SAVE_DELAY_MS)
    }

    const scheduleLocalDraftSave = () => {
        if (localDraftTimer) clearTimeout(localDraftTimer)
        if (!options.currentDoc.value || !hasUnsavedChanges.value) return
        localDraftTimer = setTimeout(() => {
            saveDraftToLocal(options.currentDoc.value)
        }, LOCAL_DRAFT_DELAY_MS)
    }

    const flushSaveAndDraft = () => {
        if (localDraftTimer) {
            clearTimeout(localDraftTimer)
            localDraftTimer = null
        }
        saveDraftToLocal(options.currentDoc.value)
        if (autoSaveTimer) {
            clearTimeout(autoSaveTimer)
            autoSaveTimer = null
        }
        void saveDocument({ silent: true, reason: 'lifecycle' })
    }

    const clearSaveTimers = () => {
        if (localDraftTimer) {
            clearTimeout(localDraftTimer)
            localDraftTimer = null
        }
        if (autoSaveTimer) {
            clearTimeout(autoSaveTimer)
            autoSaveTimer = null
        }
    }

    return {
        docLoading,
        saving,
        originalDocContent,
        originalDocName,
        hasUnsavedChanges,
        lastSavedAt,
        lastSaveReason,
        lastSavedLabel,
        setOriginalFromDoc,
        saveDraftToLocal,
        clearLocalDraft,
        restoreLocalDraftIfNeeded,
        saveDocument,
        loadDocumentById,
        selectDocument,
        syncCurrentDocFromServer,
        scheduleAutoSave,
        scheduleLocalDraftSave,
        flushSaveAndDraft,
        clearSaveTimers
    }
}
