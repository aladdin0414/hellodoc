<script lang="ts">
export default {
    name: 'DocumentEditor'
}
</script>

<script setup lang="ts">
import { ref, onMounted, computed, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { updateDocument, copyDocumentToKb } from '../api/document'
import { getKbDetail, searchEditorInKb, getKbList, getFrontendConfigs } from '../api/kb'
import DocumentSidebar from './editor/DocumentSidebar.vue'
import DocumentEditorHeader from './editor/DocumentEditorHeader.vue'
import DocumentEditorContentPane from './editor/DocumentEditorContentPane.vue'
import DocumentEditorOverlays from './editor/DocumentEditorOverlays.vue'
import RevisionDrawer from './editor/RevisionDrawer.vue'
import RevisionDiffDialog from './editor/RevisionDiffDialog.vue'
import ImagePreview from './ImagePreview.vue'
import type { SearchResult } from './SearchResultList.vue'
import { message } from '../utils/message'
import { expandAncestorFolders } from '../utils/documentTree'
import { useDocumentCollab } from '../composables/useDocumentCollab'
import { useDocumentEditorMedia } from '../composables/useDocumentEditorMedia'
import { useDocumentEditorCore } from '../composables/editor/useDocumentEditorCore'
import { useDocumentActions } from '../composables/editor/useDocumentActions'
import { useDocumentCreateFlow } from '../composables/editor/useDocumentCreateFlow'
import { useDocumentEditorLifecycle } from '../composables/editor/useDocumentEditorLifecycle'
import { useDocumentRevisions } from '../composables/editor/useDocumentRevisions'
import { useDocumentTreeActions } from '../composables/editor/useDocumentTreeActions'
import { useDocumentUiState } from '../composables/editor/useDocumentUiState'
import type { ExposeParam } from 'md-editor-v3'
import { useTheme } from '../composables/useTheme'
import { useI18n } from 'vue-i18n'

const { isDark } = useTheme()
const { t } = useI18n()

const props = defineProps<{
    kbId: number
    docId?: number
}>()

const router = useRouter()
const emit = defineEmits([])

interface DocListItem {
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
    extraMeta?: Record<string, any>
}

interface DocDetail extends DocListItem {
    content: string
}

const documents = ref<DocListItem[]>([])
const currentDoc = ref<DocDetail | null>(null)
const kbDetail = ref<any>(null)
const activeDocId = ref<number | null>(null)
const editingDocId = ref<number | null>(null)
const editingName = ref('')
const inlineCreateInputRef = ref<HTMLInputElement | null>(null)
const allKbs = ref<any[]>([])
const mdEditorRef = ref<ExposeParam>()
const setMdEditorRef = (instance: any) => {
    mdEditorRef.value = instance as ExposeParam
}

const contentPaneRef = ref<any>(null)
const handleOpenAiAssistant = () => {
    contentPaneRef.value?.triggerAiAssistant()
}
const focusEditorAfterDocCreate = async () => {
    for (let i = 0; i < 6; i += 1) {
        await nextTick()
        await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
        mdEditorRef.value?.focus?.()
        const activeEl = document.activeElement as HTMLElement | null
        if (activeEl?.closest('.md-editor')) {
            return
        }
    }
}
const showImagePreview = ref(false)
const previewImageSrc = ref('')
const isVisualPreviewMode = ref(false)
const PREVIEW_ENABLED_STORAGE_KEY = 'hellodoc:document-editor:preview-enabled'
const previewEnabled = ref(true)
const aiGenerating = ref(false)
const showPaperColorButton = ref(true)

const loadPreviewEnabledFromLocal = () => {
    try {
        const saved = localStorage.getItem(PREVIEW_ENABLED_STORAGE_KEY)
        if (saved === 'true' || saved === 'false') {
            previewEnabled.value = saved === 'true'
        }
    } catch (error) {
        console.warn('Load preview mode from localStorage failed:', error)
    }
}

watch(previewEnabled, (enabled) => {
    try {
        localStorage.setItem(PREVIEW_ENABLED_STORAGE_KEY, String(enabled))
    } catch (error) {
        console.warn('Persist preview mode to localStorage failed:', error)
    }
})

const {
    isMobile,
    updateIsMobile,
    showKbDropdown,
    showCreateMenu,
    showContextMenu,
    sidebarSearchQuery,
    searchResults,
    searchLoading,
    sidebarWidth,
    isResizingSidebar,
    isSidebarVisible,
    closeAllMenus,
    toggleSidebar,
    startResize,
    cleanupUiState
} = useDocumentUiState<SearchResult>({
    routeDocId: computed(() => props.docId),
    searchInKb: async (query: string) => {
        const res: any = await searchEditorInKb(props.kbId, query)
        return res || []
    }
})

// 拷贝到知识库相关状态
const showKbCopyDialog = ref(false)
const copyableKbs = ref<any[]>([])
const selectedKbForCopy = ref<any>(null)
const copyLoading = ref(false)
const kbSearchQuery = ref('')

// 回收站弹窗状态
const showTrashModal = ref(false)
const handleTrashRestored = () => {
    fetchDocuments(true)
}
const filteredCopyableKbs = computed(() => {
    if (!kbSearchQuery.value.trim()) return copyableKbs.value
    const query = kbSearchQuery.value.toLowerCase()
    return copyableKbs.value.filter(kb => 
        kb.title?.toLowerCase().includes(query) || 
        kb.description?.toLowerCase().includes(query)
    )
})

// Sync activeDocId with currentDoc
watch(() => currentDoc.value?.id, (id) => {
    if (id !== undefined) {
        activeDocId.value = id
        if (selectedDocIds.value.size <= 1) {
            selectedDocIds.value.clear()
            selectedDocIds.value.add(id)
        }
    }
}, { immediate: true })
const createInput = ref<HTMLInputElement | null>(null)
const renameInput = ref<HTMLInputElement | null>(null)
const setCreateInputRef = (el: any) => {
    if (el) createInput.value = el
}
const setRenameInputRef = (el: any) => {
    if (el) renameInput.value = el
}
const setInlineCreateInputRef = (el: any) => {
    if (el) inlineCreateInputRef.value = el
}

// Context menu state
const menuPosition = ref({ x: 0, y: 0 })
const contextTargetDoc = ref<DocListItem | null>(null)




// Move modal state
const showMoveModal = ref(false)
const movingDoc = ref<DocListItem | null>(null)
const selectedTargetFolderId = ref<number | null>(null)
const moveModalExpandedFolders = ref<Set<number>>(new Set())

const toggleMoveFolder = (folderId: number) => {
    if (moveModalExpandedFolders.value.has(folderId)) {
        moveModalExpandedFolders.value.delete(folderId)
    } else {
        moveModalExpandedFolders.value.add(folderId)
    }
}

// Delete modal state
const showDeleteModal = ref(false)
const deletingDoc = ref<DocListItem | null>(null)

// 计算选中的顶层删除项目数量
const selectedItemCount = computed(() => {
    if (!deletingDoc.value) return 1
    if (selectedDocIds.value.has(deletingDoc.value.id) && selectedDocIds.value.size > 1) {
        return selectedDocIds.value.size
    }
    return 1
})

// 计算待删除关联的所有子级节点数量（递归计算所有层级）
const deletingSubItemCount = computed(() => {
    if (!deletingDoc.value) return 0
    let targetDocs: DocListItem[] = []
    if (selectedDocIds.value.has(deletingDoc.value.id) && selectedDocIds.value.size > 1) {
        targetDocs = documents.value.filter(d => selectedDocIds.value.has(d.id))
    } else {
        targetDocs = [deletingDoc.value]
    }

    const removeIds = new Set<number>()
    const collectDescendants = (parentId: number) => {
        documents.value.filter(d => d.parentId === parentId).forEach(child => {
            if (!removeIds.has(child.id)) {
                removeIds.add(child.id)
                collectDescendants(child.id)
            }
        })
    }

    targetDocs.forEach(doc => {
        removeIds.add(doc.id)
        if (doc.type === 'folder') {
            collectDescendants(doc.id)
        }
    })

    return removeIds.size - targetDocs.length
})
// Revision state
const showRevisions = ref(false)

// Diff Modal state
const showDiffModal = ref(false)

let selectDocumentForTree: ((doc: DocListItem) => Promise<boolean> | boolean) | null = null

const {
    expandedFolders,
    selectedDocIds,
    lastClickedId,
    dragState,
    orderedDocuments,
    handleItemClick,
    handleMove: treeHandleMove,
    handleDragStart,
    handleDragOver,
    handleDragEnd,
    handleDrop,
    handleContainerDragOver,
    handleContainerDrop
} = useDocumentTreeActions({
    kbId: computed(() => props.kbId),
    documents,
    sidebarSearchQuery,
    isMobile,
    activeDocId,
    selectDocument: (doc) => {
        if (!selectDocumentForTree) return false
        return selectDocumentForTree(doc as DocListItem)
    },
    fetchDocuments: async (silent?: boolean) => {
        await fetchDocuments(silent)
    }
})

const {
    docLoading,
    saving,
    originalDocContent,
    originalDocName,
    hasUnsavedChanges,
    lastSavedAt,
    lastSaveReason,
    saveDraftToLocal,
    saveDocument,
    loadDocumentById,
    selectDocument,
    syncCurrentDocFromServer,
    scheduleAutoSave,
    scheduleLocalDraftSave,
    flushSaveAndDraft,
    clearSaveTimers
} = useDocumentEditorCore({
    kbId: computed(() => props.kbId),
    routeDocId: computed(() => props.docId),
    documents,
    currentDoc,
    activeDocId,
    selectedDocIds,
    lastClickedId,
    kbTitle: computed(() => kbDetail.value?.title),
    isSidebarVisible,
    isMobile,
    showDiffModal,
    showRevisions,
    navigateToDoc: (docId: number) => {
        if (props.docId === docId) return
        router.push({
            name: 'Editor',
            params: { kbId: props.kbId, docId }
        })
    }
})

selectDocumentForTree = selectDocument

const {
    loading,
    isDeleting,
    fetchDocuments: fetchDocumentsFromActions,
    handleFolderToggleStatus: handleFolderToggleStatusAction,
    handleToggleStatus: handleToggleStatusAction,
    handleConfirmDelete: handleConfirmDeleteAction,
    handleDuplicate: handleDuplicateAction,
    handleSetCover: handleSetCoverAction,
    handleSetIconColor: handleSetIconColorAction,
    handleConfirmMove: handleConfirmMoveAction
} = useDocumentActions({
    kbId: computed(() => props.kbId),
    routeDocId: computed(() => props.docId),
    documents,
    currentDoc,
    activeDocId,
    expandedFolders,
    selectedDocIds,
    selectDocument: (doc) => selectDocument(doc as any),
    navigateToEditorBase: () => {
        router.push({
            name: 'Editor',
            params: { kbId: props.kbId }
        })
    }
})

const fetchDocuments = async (silent = false) => {
    await fetchDocumentsFromActions(silent)
}

const {
    revisions,
    revisionsLoading,
    revisionsPage,
    hasMoreRevisions,
    diffLoading,
    diffCompareData,
    selectedRevision,
    openRevisions,
    loadMoreRevisions,
    closeRevisions,
    handleViewDiff,
    closeDiffModal,
    handleRestoreRevision
} = useDocumentRevisions({
    currentDoc,
    loadDocumentById,
    showRevisions,
    showDiffModal
})

watch(
    () => [currentDoc.value?.name, currentDoc.value?.content, currentDoc.value?.paperBgColor, currentDoc.value?.paperBgImage],
    () => {
        if (!currentDoc.value || !hasUnsavedChanges.value) return
        if (isReadOnlyByCollab.value) return
        scheduleLocalDraftSave()
        scheduleAutoSave()
    },
    { deep: false }
)

const handleMove = async (doc: DocListItem, direction: 'up' | 'down') => {
    showContextMenu.value = false
    await treeHandleMove(doc, direction)
}

const handleSave = () => {
    if (isReadOnlyByCollab.value) {
        message.warning(t('editor.readOnlyByOther'))
        return
    }
    void saveDocument({ reason: 'manual' })
}

const isCollabFeatureEnabled = ref(false)
const {
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
} = useDocumentCollab({
    currentDoc,
    isCollabFeatureEnabled,
    onSaveBeforeUnlock: () => saveDocument({ reason: 'manual' }),
    onSyncFromServer: () => syncCurrentDocFromServer({ force: true, silent: true })
})

const {
    isUploadingAssets,
    onUploadImg,
    onUploadImgFromUrl,
    bindEditorPasteHandler
} = useDocumentEditorMedia({
    getKbId: () => props.kbId,
    currentDoc,
    isMobile,
    mdEditorRef
})

const fetchKbDetail = async () => {
    try {
        const res: any = await getKbDetail(props.kbId)
        kbDetail.value = res
    } catch (error) {
        console.error('Fetch kb detail failed:', error)
    }
}

const fetchKbList = async () => {
    try {
        const res: any = await getKbList()
        allKbs.value = res
    } catch (error) {
        console.error('Fetch kb list failed:', error)
    }
}

const editableKbs = computed(() => {
    return allKbs.value.filter(kb => {
        const role = (kb.role || '').toUpperCase()
        return role === 'OWNER' || role === 'ADMIN' || role === 'EDITOR'
    })
})

const switchKb = (kb: any) => {
    if (kb.id === props.kbId) {
        showKbDropdown.value = false
        return
    }
    showKbDropdown.value = false
    router.push({ name: 'Editor', params: { kbId: kb.id } })
}

const initEditor = async () => {
    if (hasUnsavedChanges.value) {
        saveDraftToLocal(currentDoc.value)
        await saveDocument({ silent: true, reason: 'lifecycle' })
    }

    // Reset states
    currentDoc.value = null
    documents.value = []
    kbDetail.value = null
    activeDocId.value = null
    originalDocContent.value = ''
    originalDocName.value = ''
    lastSavedAt.value = null
    lastSaveReason.value = null
    sidebarSearchQuery.value = ''
    searchResults.value = []
    selectedDocIds.value.clear()
    lastClickedId.value = null

    // Fetch data
    await Promise.all([
        fetchKbDetail(),
        fetchDocuments()
    ])
}

// Watch for kbId changes to re-initialize everything
watch(() => props.kbId, (newId) => {
    if (newId) {
        initEditor()
    }
})

const {
    creating,
    inlineCreating,
    inlineCreateName,
    inlineCreateInsertAfterId,
    showCreateModal,
    createType,
    newName,
    startInlineCreate,
    handleConfirmInlineCreate,
    cancelInlineCreate,
    handleConfirmCreate
} = useDocumentCreateFlow({
    kbId: computed(() => props.kbId),
    documents,
    expandedFolders,
    sidebarSearchQuery,
    showContextMenu,
    showCreateMenu,
    fetchDocuments: async (silent?: boolean) => {
        await fetchDocuments(silent)
    },
    navigateToDoc: (docId: number) => {
        router.push({
            name: 'Editor',
            params: { kbId: props.kbId, docId }
        })
    },
    onCreated: (data: any) => {
        currentDoc.value = data
        activeDocId.value = data.id
        selectedDocIds.value.clear()
        selectedDocIds.value.add(data.id)
        lastClickedId.value = data.id
        originalDocContent.value = data.content || ''
        originalDocName.value = data.name || ''
        if (data?.type === 'file') {
            void focusEditorAfterDocCreate()
        }
    }
})

watch(() => props.docId, (newId) => {
    isVisualPreviewMode.value = false
    if (newId && documents.value.length > 0) {
        const target = documents.value.find(d => d.id === newId)
        if (target) {
            expandAncestorFolders(expandedFolders.value, documents.value, target.id)
            selectDocument(target)
        }
    }
})

watch(showCreateModal, async (newVal) => {
    if (newVal) {
        await nextTick()
        createInput.value?.focus()
        createInput.value?.select()
    }
})

watch(editingDocId, async (newVal) => {
    if (newVal !== null) {
        await nextTick()
        renameInput.value?.focus()
        renameInput.value?.select()
    }
})

watch(inlineCreating, async (newVal) => {
    if (newVal) {
        await nextTick()
        await nextTick()
        for (let i = 0; i < 10; i++) {
            const el = inlineCreateInputRef.value
            if (el) {
                el.focus()
                el.select()
                return
            }
            await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
        }
    }
})

const handleFolderToggleStatus = async (folder: DocListItem, targetStatus: 'published' | 'draft') => {
    showContextMenu.value = false
    await handleFolderToggleStatusAction(folder, targetStatus)
}

const handleToggleStatus = async (targetDoc?: DocListItem) => {
    await handleToggleStatusAction(targetDoc)
    showContextMenu.value = false
}

// 计算内联新建行的缩进深度
const inlineCreateDepth = computed(() => {
    if (!inlineCreating.value) return 0
    const parentId = inlineCreating.value.parentId
    if (parentId === null) return 0
    // 找到父级在 orderedDocuments 中的深度
    const parent = orderedDocuments.value.find(d => d.id === parentId)
    return parent ? parent.depth + 1 : 0
})

const startRename = (doc: DocListItem) => {
    editingDocId.value = doc.id
    editingName.value = doc.name
    showContextMenu.value = false
}

const handleConfirmRename = async (doc: DocListItem) => {
    const newNameVal = editingName.value.trim()
    if (!newNameVal || newNameVal === doc.name) {
        editingDocId.value = null
        return
    }

    // Optimistic update
    const originalName = doc.name
    doc.name = newNameVal
    editingDocId.value = null

    try {
        await updateDocument(props.kbId, doc.id, {
            ...doc,
            name: newNameVal
        })
        await fetchDocuments(true)
    } catch (error) {
        // Revert on error
        doc.name = originalName
        console.error('Rename failed:', error)
        message.error(t('editor.renameFailed'))
        fetchDocuments(true)
    }
}

const handleDelete = (doc: DocListItem) => {
    showContextMenu.value = false
    deletingDoc.value = doc
    showDeleteModal.value = true
}

const handleConfirmDelete = async () => {
    const ok = await handleConfirmDeleteAction(deletingDoc.value as any)
    if (ok) showDeleteModal.value = false
    deletingDoc.value = null
}

const handleDuplicate = async (doc: DocListItem) => {
    showContextMenu.value = false
    await handleDuplicateAction(doc as any)
}

const handleCopyToKb = async () => {
    showContextMenu.value = false
    kbSearchQuery.value = ''
    selectedKbForCopy.value = null
    copyLoading.value = true
    try {
        const res: any = await getKbList()
        // 过滤出有编辑权限的知识库 (OWNER, ADMIN, EDITOR)，且排除当前知识库
        copyableKbs.value = (res || []).filter((kb: any) => 
            ['OWNER', 'ADMIN', 'EDITOR'].includes(kb.role) && kb.id !== props.kbId
        )
        showKbCopyDialog.value = true
    } catch (error) {
        console.error('Fetch KB list failed:', error)
        message.error(t('editor.fetchKbListFailed'))
    } finally {
        copyLoading.value = false
    }
}

const confirmCopyToKb = async () => {
    if (!contextTargetDoc.value || !selectedKbForCopy.value) return

    let targetDocs: DocListItem[] = []
    if (selectedDocIds.value.has(contextTargetDoc.value.id) && selectedDocIds.value.size > 1) {
        targetDocs = documents.value.filter(d => selectedDocIds.value.has(d.id))
    } else {
        targetDocs = [contextTargetDoc.value]
    }

    copyLoading.value = true
    try {
        await Promise.all(
            targetDocs.map(doc => copyDocumentToKb(props.kbId, doc.id, selectedKbForCopy.value.id))
        )
        message.success(t('editor.copySuccess'))
        showKbCopyDialog.value = false
    } catch (error) {
        console.error('Copy to KB failed:', error)
        message.error(t('editor.copyFailed'))
    } finally {
        copyLoading.value = false
    }
}



const handleSetCover = async (doc: DocListItem) => {
    showContextMenu.value = false
    await handleSetCoverAction(doc as any)
}

const handleSetIconColor = async (doc: DocListItem, color: string) => {
    showContextMenu.value = false
    await handleSetIconColorAction(doc as any, color)
}

const openMoveModal = (doc: DocListItem) => {
    movingDoc.value = doc
    selectedTargetFolderId.value = doc.parentId
    showMoveModal.value = true
    showContextMenu.value = false

    // Reset and initialize expanded folders for the move modal
    moveModalExpandedFolders.value = new Set()
    if (doc.parentId !== null) {
        // Expand all parents of the current document
        const expandParents = (parentId: number) => {
            moveModalExpandedFolders.value.add(parentId)
            const parent = documents.value.find(d => d.id === parentId)
            if (parent && parent.parentId !== null) {
                expandParents(parent.parentId)
            }
        }
        expandParents(doc.parentId)
    }
}

const availableFolders = computed(() => {
    if (!movingDoc.value) return []

    // 1. Get all descendants of the moving item (if it's a folder)
    const descendants = new Set<number>()
    if (movingDoc.value.type === 'folder') {
        const collect = (id: number) => {
            descendants.add(id)
            documents.value.filter(d => d.parentId === id).forEach(child => collect(child.id))
        }
        collect(movingDoc.value.id)
    }

    // 2. Build tree of folders, only including those whose parents are expanded
    const result: (DocListItem & { depth: number; hasChildren: boolean; isExpanded: boolean })[] = []
    const build = (parentId: number | null, depth: number) => {
        const children = documents.value.filter(d =>
            d.parentId === parentId &&
            d.type === 'folder' &&
            d.id !== movingDoc.value?.id &&
            !descendants.has(d.id)
        )
        // Sort by orderNum, then by name
        children.sort((a, b) => {
            if (a.orderNum !== b.orderNum) return a.orderNum - b.orderNum
            return a.name.localeCompare(b.name)
        })

        children.forEach(child => {
            const hasChildren = documents.value.some(d =>
                d.parentId === child.id &&
                d.type === 'folder' &&
                d.id !== movingDoc.value?.id &&
                !descendants.has(d.id)
            )
            const isExpanded = moveModalExpandedFolders.value.has(child.id)

            result.push({
                ...child,
                depth,
                hasChildren,
                isExpanded
            })

            // Only recurse if this folder is expanded in the move modal
            if (isExpanded) {
                build(child.id, depth + 1)
            }
        })
    }

    build(null, 0)
    return result
})

const handleConfirmMove = async () => {
    if (!movingDoc.value) return

    await handleConfirmMoveAction(movingDoc.value as any, selectedTargetFolderId.value)
    showMoveModal.value = false
}

const handleContextMenu = (e: MouseEvent, doc: DocListItem) => {
    showContextMenu.value = true
    contextTargetDoc.value = doc
    
    // 动态调整菜单位置以防止被遮挡
    const x = e.clientX
    const y = e.clientY
    
    // 估算菜单宽高 (min-w 160px + padding/border)
    const menuWidth = 180
    const menuHeight = doc.type === 'folder' ? 320 : 260 // 包含子项的菜单更高
    
    let finalX = x
    let finalY = y
    
    // 如果右边超出，则向左偏移
    if (x + menuWidth > window.innerWidth) {
        finalX = Math.max(8, x - menuWidth)
    }
    // 如果底部超出，则向上偏移
    if (y + menuHeight > window.innerHeight) {
        finalY = Math.max(8, y - menuHeight)
    }
    
    menuPosition.value = { x: finalX, y: finalY }
}




watch([() => currentDoc.value?.name, () => kbDetail.value?.title], ([docName, kbTitle]) => {
    if (docName && kbTitle) {
        document.title = t('editor.editDocTitleWithDoc', { docName, kbTitle })
    } else if (kbTitle) {
        document.title = t('editor.editDocTitleWithKb', { kbTitle })
    } else {
        document.title = t('editor.editDocTitle')
    }
}, { immediate: true })

useDocumentEditorLifecycle({
    kbId: computed(() => props.kbId),
    currentDoc: currentDoc as any,
    hasUnsavedChanges,
    saveDraftToLocal: (doc) => saveDraftToLocal(doc as any),
    flushSaveAndDraft,
    closeCollab,
    updateIsMobile,
    closeAllMenus,
    handleSave,
    cleanupUiState,
    clearSaveTimers
})

onMounted(() => {
    loadPreviewEnabledFromLocal()
    initEditor()
    fetchKbList()
})

onMounted(async () => {
    try {
        const configs: any = await getFrontendConfigs()
        const v = configs?.['collab.enabled']
        isCollabFeatureEnabled.value = String(v).toLowerCase() === 'true'
    } catch {
        isCollabFeatureEnabled.value = false
    }
})

// 搜索结果选中处理
const handleSearchSelect = (docId: number) => {
    const doc = documents.value.find(d => d.id === docId)
    if (doc) {
        activeDocId.value = doc.id
        selectedDocIds.value.clear()
        selectedDocIds.value.add(doc.id)
        lastClickedId.value = doc.id
        void selectDocument(doc)
    } else {
        void loadDocumentById(docId, { navigate: true, optimisticActive: true })
    }
}

const handleClearMultiSelection = () => {
    if (selectedDocIds.value.size > 1) {
        selectedDocIds.value.clear()
        if (activeDocId.value) {
            selectedDocIds.value.add(activeDocId.value)
            lastClickedId.value = activeDocId.value
        } else {
            lastClickedId.value = null
        }
    }
}

const handleSidebarItemClick = (doc: DocListItem & { depth?: number }, event: MouseEvent) => {
    const isMod = event.ctrlKey || event.metaKey
    const isShift = event.shiftKey
    if (doc.type === 'file' && !isMod && !isShift) {
        activeDocId.value = doc.id
        selectedDocIds.value.clear()
        selectedDocIds.value.add(doc.id)
        lastClickedId.value = doc.id
    }
    handleItemClick(doc as any, event)
}
</script>

<template>
    <div
        :class="{ 'ow-mobile-no-hover': isMobile }"
        class="flex h-screen bg-[#f5f5f4] dark:bg-[#161b22] overflow-hidden font-sans text-slate-900 dark:text-slate-200 selection:bg-indigo-100 selection:text-indigo-900 dark:selection:text-slate-100">
        <div v-if="!isVisualPreviewMode && isMobile && isSidebarVisible" class="fixed inset-0 z-40 bg-slate-900/40 backdrop-blur-sm"
            @click="isSidebarVisible = false"></div>
        <DocumentSidebar v-if="!isVisualPreviewMode" :visible="isSidebarVisible" :is-mobile="isMobile" :sidebar-width="sidebarWidth"
            :is-resizing-sidebar="isResizingSidebar" :kb-detail="kbDetail" :show-kb-dropdown="showKbDropdown"
            :editable-kbs="editableKbs" :kb-id="props.kbId" :sidebar-search-query="sidebarSearchQuery"
            :loading="loading" :documents="documents" :inline-creating="inlineCreating" :search-results="searchResults"
            :search-loading="searchLoading" :ordered-documents="orderedDocuments" :expanded-folders="expandedFolders"
            :drag-state="dragState" :selected-doc-ids="selectedDocIds" :active-doc-id="activeDocId"
            :editing-doc-id="editingDocId" :editing-name="editingName"
            :inline-create-insert-after-id="inlineCreateInsertAfterId" :inline-create-depth="inlineCreateDepth"
            :inline-create-name="inlineCreateName" :show-create-menu="showCreateMenu" @start-resize="startResize"
            @update-show-kb-dropdown="showKbDropdown = $event" @switch-kb="switchKb"
            @update-sidebar-search-query="sidebarSearchQuery = $event" @search-select="handleSearchSelect"
            @item-click="(doc, event) => handleSidebarItemClick(doc, event)" @context-menu="handleContextMenu"
            @drag-start="(event, doc) => handleDragStart(event, doc)" @drag-over="(event, doc) => handleDragOver(event, doc)"
            @drop="(event, doc) => handleDrop(event, doc)" @drag-end="handleDragEnd"
            @container-drag-over="(event) => handleContainerDragOver(event)" @container-drop="(event) => handleContainerDrop(event)"
            @start-inline-create="startInlineCreate" @delete-doc="handleDelete" @set-rename-input-ref="setRenameInputRef"
            @update-editing-name="editingName = $event" @confirm-rename="handleConfirmRename"
            @set-inline-create-input-ref="setInlineCreateInputRef" @update-inline-create-name="inlineCreateName = $event"
            @confirm-inline-create="handleConfirmInlineCreate" @cancel-inline-create="cancelInlineCreate"
            @go-home="router.push('/')" @update-show-create-menu="showCreateMenu = $event"
            @open-trash-modal="showTrashModal = true" @clear-selection="handleClearMultiSelection" />

        <!-- Main Content -->
        <div class="flex-1 flex flex-col min-w-0 bg-white dark:bg-[#161b22] min-h-0" @click="handleClearMultiSelection">
            <template v-if="docLoading && currentDoc == null">
                <div class="flex-1 flex items-center justify-center bg-slate-50 dark:bg-[#161b22]">
                    <div class="animate-spin rounded-full h-8 w-8 border-2 border-indigo-600 border-t-transparent">
                    </div>
                </div>
            </template>
            <template v-else-if="currentDoc != null">
                <DocumentEditorHeader v-if="!isVisualPreviewMode" :current-doc="currentDoc" :kb-id="kbId" :is-mobile="isMobile"
                    :is-sidebar-visible="isSidebarVisible" :has-unsaved-changes="hasUnsavedChanges"
                    :saving="saving" :is-read-only-by-collab="isReadOnlyByCollab"
                    :is-collab-feature-enabled="isCollabFeatureEnabled" :collab-state="collabState"
                    :collab-state-label="collabStateLabel" :collab-state-dot-class="collabStateDotClass"
                    :active-editors="activeEditors" :is-locked-by-me="isLockedByMe" :doc-lock="docLock"
                    :lock-owner-label="lockOwnerLabel" :ai-generating="aiGenerating" @toggle-sidebar="toggleSidebar" @save="handleSave"
                    @open-revisions="openRevisions" @toggle-status="handleToggleStatus()"
                    @reconnect-collab="reconnectCollabNow" @release-edit-lock="releaseEditLock"
                    @request-edit-lock="requestEditLock" @open-ai-assistant="handleOpenAiAssistant" />

                <DocumentEditorContentPane ref="contentPaneRef" :current-doc="currentDoc" :doc-loading="docLoading" :is-mobile="isMobile"
                    :is-uploading-assets="isUploadingAssets" :is-dark="isDark"
                    :is-read-only-by-collab="isReadOnlyByCollab" :set-editor-ref="setMdEditorRef"
                    :show-paper-color-button="showPaperColorButton"
                    :code-foldable="false" :preview-enabled="previewEnabled"
                    :placeholder="t('editor.inputContent')"
                    @upload-img="onUploadImg" @upload-img-from-url="onUploadImgFromUrl" @save="handleSave" @editor-remount="bindEditorPasteHandler"
                    @update-preview-enabled="previewEnabled = $event" @ai-generating-change="aiGenerating = $event"
                    @visual-preview-mode-change="isVisualPreviewMode = $event"
                    @preview-image="(src) => { previewImageSrc = src; showImagePreview = true }" />

            </template>
            <div v-else class="flex-1 flex items-center justify-center text-slate-400 bg-slate-50 dark:bg-[#161b22]">
                <div class="text-center">
                    <svg class="h-16 w-16 mx-auto mb-4 text-slate-300" fill="none" viewBox="0 0 24 24"
                        stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                            d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18 18.247 18.477 16.5 18.477a10.47 10.47 0 00-4.5 1.253" />
                    </svg>
                    <p class="text-sm font-medium">{{ t('editor.selectOrCreateDoc') }}</p>
                </div>
            </div>
        </div>

        <DocumentEditorOverlays v-if="!isVisualPreviewMode" :show-context-menu="showContextMenu" :menu-position="menuPosition"
            :context-target-doc="contextTargetDoc" :show-create-modal="showCreateModal" :create-type="createType"
            :new-name="newName" :creating="creating" :show-move-modal="showMoveModal"
            :selected-target-folder-id="selectedTargetFolderId" :available-folders="availableFolders"
            :show-delete-modal="showDeleteModal" :deleting-doc="deletingDoc" :is-deleting="isDeleting"
            :sub-item-count="deletingSubItemCount" :selected-item-count="selectedItemCount"
            :selected-doc-ids="selectedDocIds"
            :show-kb-copy-dialog="showKbCopyDialog" :kb-search-query="kbSearchQuery"
            :filtered-copyable-kbs="filteredCopyableKbs" :selected-kb-for-copy="selectedKbForCopy"
            :copy-loading="copyLoading" :set-create-input-ref="setCreateInputRef"
            :show-trash-modal="showTrashModal" :kb-id="kbId"
        @update-show-context-menu="showContextMenu = $event"
            @update-show-create-modal="showCreateModal = $event" @update-new-name="newName = $event"
            @confirm-create="handleConfirmCreate" @start-inline-create="startInlineCreate"
            @folder-toggle-status="handleFolderToggleStatus" @move-direction="handleMove"
            @copy-to-kb="handleCopyToKb" @open-move-modal="openMoveModal" @duplicate="handleDuplicate"
            @start-rename="startRename" @toggle-status="handleToggleStatus" @set-cover="handleSetCover"
            @set-icon-color="handleSetIconColor"
            @delete-doc="handleDelete" @update-show-move-modal="showMoveModal = $event"
            @update-selected-target-folder-id="selectedTargetFolderId = $event" @toggle-move-folder="toggleMoveFolder"
            @confirm-move="handleConfirmMove" @update-show-delete-modal="showDeleteModal = $event"
            @confirm-delete="handleConfirmDelete" @update-show-kb-copy-dialog="showKbCopyDialog = $event"
            @update-kb-search-query="kbSearchQuery = $event" @update-selected-kb-for-copy="selectedKbForCopy = $event"
            @confirm-copy-to-kb="confirmCopyToKb"
            @update-show-trash-modal="showTrashModal = $event"
            @trash-restored="handleTrashRestored" />
    </div>

    <RevisionDrawer :visible="showRevisions" :revisions="revisions" :revisions-loading="revisionsLoading"
        :revisions-page="revisionsPage" :has-more-revisions="hasMoreRevisions" @close="closeRevisions"
        @view-diff="handleViewDiff" @load-more="loadMoreRevisions" />

    <RevisionDiffDialog :visible="showDiffModal" :selected-revision="selectedRevision" :diff-loading="diffLoading"
        :diff-compare-data="diffCompareData" :is-dark="isDark" @close="closeDiffModal"
        @restore="handleRestoreRevision" />
    <ImagePreview :show="showImagePreview" :src="previewImageSrc" @close="showImagePreview = false" />
</template>


<style>
/* 非 scoped 样式，解决第三方组件多主题背景覆盖的持久性 Bug */
:where(.md-editor) {
    background-color: #ffffff !important;
    --md-bk-color: #ffffff !important;
    --md-bk-color-outstand: #fafafa !important;
    border: none !important;
}

:where(.dark) :where(.md-editor) {
    background-color: #1e1e1e !important;
    --md-bk-color: #1e1e1e !important;
    --md-bk-color-outstand: #181818 !important;
    border: none !important;
}

:where(.md-editor-toolbar-wrapper),
:where(.md-editor-content),
:where(.md-editor-footer) {
    background-color: #ffffff !important;
}

:where(.dark) :where(.md-editor-toolbar-wrapper),
:where(.dark) :where(.md-editor-content),
:where(.dark) :where(.md-editor-footer) {
    background-color: #1e1e1e !important;
}

:where(.md-editor-toolbar-wrapper) {
    border-bottom: 1px solid #f1f5f9 !important;
}

:where(.dark) :where(.md-editor-toolbar-wrapper) {
    border-bottom: 1px solid #334155 !important;
}

:where(.md-editor-footer) {
    border-top: 1px solid #f1f5f9 !important;
}

:where(.dark) :where(.md-editor-footer) {
    border-top: 1px solid #334155 !important;
}

@media (max-width: 767px) {
    /* 移除强制的 overflow-x: auto，尝试依赖组件自身的移动端优化 */
}

@supports (-webkit-touch-callout: none) {
    @media (max-width: 767px) {
    }
}
</style>

<style scoped>
@import '../styles/markdown-table.css';

/* 拖拽放入文件夹时的高亮效果 */
.drag-drop-inside {
    background: rgba(99, 102, 241, 0.08) !important;
    box-shadow: inset 0 0 0 2px rgba(99, 102, 241, 0.4) !important;
    border-radius: 0.75rem;
}

.dark .drag-drop-inside {
    background: rgba(99, 102, 241, 0.15) !important;
    box-shadow: inset 0 0 0 2px rgba(129, 140, 248, 0.4) !important;
}





:deep(.md-editor.ow-uploading .md-editor-toolbar-wrapper) {
    position: relative;
}

:deep(.md-editor.ow-uploading .md-editor-toolbar-wrapper::after) {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    bottom: -1px;
    height: 3px;
    border-radius: 9999px;
    z-index: 1;
    pointer-events: none;
    background:
        linear-gradient(90deg, rgba(79, 70, 229, 0.25), rgba(79, 70, 229, 0.9), rgba(79, 70, 229, 0.25)) no-repeat,
        rgba(79, 70, 229, 0.12);
    background-size: 35% 100%, 100% 100%;
    background-position: -35% 0, 0 0;
    animation: upload-slide-bg 1.1s linear infinite;
}

:deep(.dark .md-editor.ow-uploading .md-editor-toolbar-wrapper::after) {
    background:
        linear-gradient(90deg, rgba(129, 140, 248, 0.2), rgba(129, 140, 248, 0.85), rgba(129, 140, 248, 0.2)) no-repeat,
        rgba(129, 140, 248, 0.12);
    background-size: 35% 100%, 100% 100%;
    background-position: -35% 0, 0 0;
}

.upload-progress-track {
    contain: paint;
}

.upload-progress-slider {
    position: absolute;
    top: 0;
    left: -35%;
    height: 100%;
    width: 35%;
    background: linear-gradient(90deg, rgba(79, 70, 229, 0.25), rgba(79, 70, 229, 0.9), rgba(79, 70, 229, 0.25));
    border-radius: 9999px;
    animation: upload-slide 1.1s linear infinite;
}

.dark .upload-progress-slider {
    background: linear-gradient(90deg, rgba(129, 140, 248, 0.2), rgba(129, 140, 248, 0.85), rgba(129, 140, 248, 0.2));
}

@keyframes upload-slide {
    0% {
        left: -35%;
    }

    100% {
        left: 100%;
    }
}

@keyframes upload-slide-bg {
    0% {
        background-position: -35% 0, 0 0;
    }

    100% {
        background-position: 100% 0, 0 0;
    }
}

.ow-mobile-no-hover :deep(.group-hover\:opacity-100),
.ow-mobile-no-hover :deep(.sm\:group-hover\:opacity-100),
.ow-mobile-no-hover :deep(.md\:group-hover\:opacity-100),
.ow-mobile-no-hover :deep(.group-hover\/sidebar\:opacity-100) {
    opacity: 1 !important;
}

@media (hover: none), (pointer: coarse) {
    .ow-mobile-no-hover :deep([class*='hover:']:hover),
    .ow-mobile-no-hover :deep([class*='sm:hover:']:hover),
    .ow-mobile-no-hover :deep([class*='md:hover:']:hover) {
        background-color: inherit !important;
        color: inherit !important;
        border-color: inherit !important;
        box-shadow: none !important;
        transform: none !important;
    }
}

</style>
