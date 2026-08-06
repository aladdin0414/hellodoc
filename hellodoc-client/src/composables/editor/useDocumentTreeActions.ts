import { computed, ref, watch, type Ref } from 'vue'
import { updateDocument } from '../../api/document'
import { message } from '../../utils/message'
import { buildOrderedDocuments, sortDocuments } from '../../utils/documentTree'
import { i18n } from '../../i18n'

export interface TreeDocListItem {
    id: number
    __key?: number
    name: string
    type: string
    parentId: number | null
    orderNum: number
    status?: string
    isCover?: boolean
    extraMeta?: Record<string, any>
}

interface UseDocumentTreeActionsOptions {
    kbId: Ref<number>
    documents: Ref<TreeDocListItem[]>
    sidebarSearchQuery: Ref<string>
    isMobile: Ref<boolean>
    activeDocId: Ref<number | null>
    selectDocument: (doc: TreeDocListItem) => Promise<boolean> | boolean
    fetchDocuments: (silent?: boolean) => Promise<void>
}

const ORDER_SPACING = 10000

export const useDocumentTreeActions = (options: UseDocumentTreeActionsOptions) => {
    const expandedFolders = ref<Set<number>>(new Set())
    const selectedDocIds = ref<Set<number>>(new Set())
    const lastClickedId = ref<number | null>(null)
    const dragState = ref<{
        draggingDoc: TreeDocListItem | null
        overDocId: number | null
        dropPosition: 'before' | 'after' | 'inside' | null
    }>({ draggingDoc: null, overDocId: null, dropPosition: null })

    const saveExpandedState = () => {
        if (options.kbId.value) {
            try {
                sessionStorage.setItem(`kb_expanded_${options.kbId.value}`, JSON.stringify(Array.from(expandedFolders.value)))
            } catch (e) {}
        }
    }

    const loadExpandedState = () => {
        if (options.kbId.value) {
            try {
                const saved = sessionStorage.getItem(`kb_expanded_${options.kbId.value}`)
                if (saved) {
                    const arr = JSON.parse(saved)
                    if (Array.isArray(arr)) {
                        expandedFolders.value = new Set(arr.map(Number))
                    }
                }
            } catch (e) {}
        }
    }

    watch(() => options.kbId.value, () => {
        loadExpandedState()
    }, { immediate: true })

    watch(expandedFolders, () => {
        saveExpandedState()
    }, { deep: true })

    const orderedDocuments = computed(() => {
        return buildOrderedDocuments(options.documents.value, expandedFolders.value, options.sidebarSearchQuery.value)
    })

    const toggleFolder = (folderId: number) => {
        const newSet = new Set(expandedFolders.value)
        if (newSet.has(folderId)) {
            newSet.delete(folderId)
        } else {
            newSet.add(folderId)
        }
        expandedFolders.value = newSet
    }

    const handleItemClick = (doc: TreeDocListItem, event: MouseEvent) => {
        const isMod = event.ctrlKey || event.metaKey
        const isShift = event.shiftKey

        if (doc.type === 'folder' && !isMod && !isShift) {
            toggleFolder(doc.id)
            return
        }

        if (isShift && lastClickedId.value !== null) {
            const ordered = orderedDocuments.value
            const startIdx = ordered.findIndex(d => d.id === lastClickedId.value)
            const endIdx = ordered.findIndex(d => d.id === doc.id)

            if (startIdx !== -1 && endIdx !== -1) {
                const [minIdx, maxIdx] = [Math.min(startIdx, endIdx), Math.max(startIdx, endIdx)]
                const range = ordered.slice(minIdx, maxIdx + 1).map(d => d.id)
                range.forEach(id => selectedDocIds.value.add(id))
                lastClickedId.value = doc.id
                return
            }
        }

        if (isMod) {
            if (selectedDocIds.value.has(doc.id)) {
                selectedDocIds.value.delete(doc.id)
            } else {
                selectedDocIds.value.add(doc.id)
            }
            lastClickedId.value = doc.id
            return
        }

        selectedDocIds.value.clear()
        selectedDocIds.value.add(doc.id)
        lastClickedId.value = doc.id

        if (doc.type !== 'folder') {
            void options.selectDocument(doc)
        }
    }

    const handleMove = async (doc: TreeDocListItem, direction: 'up' | 'down') => {
        const siblings = options.documents.value
            .filter(d => d.parentId === doc.parentId)
            .sort(sortDocuments)

        if (siblings.length <= 1) return

        const isMultiSelected = selectedDocIds.value.has(doc.id) && selectedDocIds.value.size > 1
        const selectedIdSet = isMultiSelected
            ? selectedDocIds.value
            : new Set([doc.id])

        const selectedDocs = siblings.filter(d => selectedIdSet.has(d.id))
        const nonSelectedDocs = siblings.filter(d => !selectedIdSet.has(d.id))

        if (selectedDocs.length === 0) return

        let newSiblings: TreeDocListItem[] = []

        if (direction === 'up') {
            const firstSelectedIndex = siblings.findIndex(d => selectedIdSet.has(d.id))
            if (firstSelectedIndex <= 0) return

            const targetPrev = siblings[firstSelectedIndex - 1]
            if (!targetPrev) return

            const beforeTargetPrev: TreeDocListItem[] = []
            const afterTargetPrev: TreeDocListItem[] = []
            let passedTarget = false

            nonSelectedDocs.forEach(item => {
                if (item.id === targetPrev.id) {
                    passedTarget = true
                } else if (!passedTarget) {
                    beforeTargetPrev.push(item)
                } else {
                    afterTargetPrev.push(item)
                }
            })

            newSiblings = [...beforeTargetPrev, ...selectedDocs, targetPrev, ...afterTargetPrev]
        } else {
            let lastSelectedIndex = -1
            for (let i = siblings.length - 1; i >= 0; i--) {
                const item = siblings[i]
                if (item && selectedIdSet.has(item.id)) {
                    lastSelectedIndex = i
                    break
                }
            }
            if (lastSelectedIndex === -1 || lastSelectedIndex >= siblings.length - 1) return

            const targetNext = siblings[lastSelectedIndex + 1]
            if (!targetNext) return

            const beforeTargetNext: TreeDocListItem[] = []
            const afterTargetNext: TreeDocListItem[] = []
            let passedTarget = false

            nonSelectedDocs.forEach(item => {
                if (item.id === targetNext.id) {
                    passedTarget = true
                } else if (!passedTarget) {
                    beforeTargetNext.push(item)
                } else {
                    afterTargetNext.push(item)
                }
            })

            newSiblings = [...beforeTargetNext, targetNext, ...selectedDocs, ...afterTargetNext]
        }

        const updates: Promise<any>[] = []
        newSiblings.forEach((item, i) => {
            const newOrder = (i + 1) * ORDER_SPACING
            if (item.orderNum !== newOrder) {
                item.orderNum = newOrder
                updates.push(updateDocument(options.kbId.value, item.id, { ...item, orderNum: newOrder }))
            }
        })

        if (updates.length > 0) {
            try {
                await Promise.all(updates)
                await options.fetchDocuments(true)
            } catch (error) {
                console.error('Reorder failed:', error)
                message.error(i18n.global.t('editor.reorderSyncFailed'))
                void options.fetchDocuments()
            }
        }
    }

    const isDescendantOf = (targetId: number, ancestorId: number): boolean => {
        let currentId: number | null = targetId
        const visited = new Set<number>()
        while (currentId !== null) {
            if (currentId === ancestorId) return true
            if (visited.has(currentId)) return false
            visited.add(currentId)
            const doc = options.documents.value.find(d => d.id === currentId)
            currentId = doc?.parentId ?? null
        }
        return false
    }

    const clearDragState = () => {
        dragState.value = { draggingDoc: null, overDocId: null, dropPosition: null }
    }

    const handleDragStart = (e: DragEvent, doc: TreeDocListItem) => {
        if (!e.dataTransfer) return

        if (!selectedDocIds.value.has(doc.id)) {
            selectedDocIds.value.clear()
            selectedDocIds.value.add(doc.id)
            lastClickedId.value = doc.id
        }

        dragState.value.draggingDoc = doc
        e.dataTransfer.effectAllowed = 'move'
        e.dataTransfer.setData('text/plain', String(doc.id))
    }

    const getDropPosition = (e: DragEvent, doc: TreeDocListItem): 'before' | 'after' | 'inside' => {
        const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
        const ratio = (e.clientY - rect.top) / rect.height

        if (doc.type === 'folder') {
            if (ratio < 0.25) return 'before'
            if (ratio > 0.75) return 'after'
            return 'inside'
        }

        return ratio < 0.5 ? 'before' : 'after'
    }

    const handleDragOver = (e: DragEvent, doc: TreeDocListItem) => {
        if (!dragState.value.draggingDoc) return
        e.preventDefault()
        if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'

        const dragging = dragState.value.draggingDoc
        if (dragging.id === doc.id) {
            dragState.value.overDocId = null
            dragState.value.dropPosition = null
            return
        }
        if (dragging.type === 'folder' && isDescendantOf(doc.id, dragging.id)) {
            dragState.value.overDocId = null
            dragState.value.dropPosition = null
            return
        }

        const position = getDropPosition(e, doc)
        dragState.value.overDocId = doc.id
        dragState.value.dropPosition = position
    }

    const handleDragEnd = () => {
        clearDragState()
    }

    const handleDrop = async (e: DragEvent, targetDoc: TreeDocListItem) => {
        e.preventDefault()
        const draggingIdFromData = Number(e.dataTransfer?.getData('text/plain') || NaN)
        const draggingFromData = Number.isFinite(draggingIdFromData)
            ? options.documents.value.find(d => d.id === draggingIdFromData) ?? null
            : null
        const dragging = dragState.value.draggingDoc ?? draggingFromData
        if (!dragging) {
            clearDragState()
            return
        }

        const stateTargetDoc = dragState.value.overDocId != null
            ? options.documents.value.find(d => d.id === dragState.value.overDocId) ?? null
            : null
        const resolvedTargetDoc = stateTargetDoc ?? targetDoc
        if (dragging.id === resolvedTargetDoc.id || (dragging.type === 'folder' && isDescendantOf(resolvedTargetDoc.id, dragging.id))) {
            clearDragState()
            return
        }

        const position = dragState.value.overDocId === resolvedTargetDoc.id && dragState.value.dropPosition
            ? dragState.value.dropPosition
            : getDropPosition(e, resolvedTargetDoc)

        clearDragState()

        const selectedIds = Array.from(selectedDocIds.value)
        let topLevelSelectedIds = selectedIds.filter(id => {
            let current = options.documents.value.find(d => d.id === id)
            while (current && current.parentId !== null) {
                if (selectedDocIds.value.has(current.parentId)) return false
                const parentId = current.parentId
                current = options.documents.value.find(d => d.id === parentId)
            }
            return true
        })

        if (!topLevelSelectedIds.includes(dragging.id)) {
            topLevelSelectedIds = [dragging.id]
        }

        const ordered = orderedDocuments.value
        topLevelSelectedIds.sort((a, b) => {
            return ordered.findIndex(d => d.id === a) - ordered.findIndex(d => d.id === b)
        })

        const movingDocs = topLevelSelectedIds
            .map(id => options.documents.value.find(d => d.id === id))
            .filter((doc): doc is TreeDocListItem => !!doc)
            .filter(doc => doc.id !== resolvedTargetDoc.id && !isDescendantOf(resolvedTargetDoc.id, doc.id))

        if (movingDocs.length === 0) return

        const movingIdSet = new Set(movingDocs.map(doc => doc.id))
        const movePromises: Promise<any>[] = []

        if (position === 'inside') {
            if (resolvedTargetDoc.type !== 'folder') return

            const existingChildren = options.documents.value
                .filter(d => d.parentId === resolvedTargetDoc.id && !movingIdSet.has(d.id))
                .sort(sortDocuments)

            const reordered = [...existingChildren, ...movingDocs]

            reordered.forEach((doc, index) => {
                const desiredParentId = resolvedTargetDoc.id
                const desiredOrderNum = (index + 1) * ORDER_SPACING

                if (doc.parentId === desiredParentId && doc.orderNum === desiredOrderNum) return

                const idx = options.documents.value.findIndex(d => d.id === doc.id)
                if (idx !== -1) {
                    options.documents.value[idx] = { ...doc, parentId: desiredParentId, orderNum: desiredOrderNum }
                }

                movePromises.push(updateDocument(options.kbId.value, doc.id, {
                    ...doc,
                    parentId: desiredParentId,
                    orderNum: desiredOrderNum
                }))
            })

            options.documents.value = [...options.documents.value]
            expandedFolders.value.add(resolvedTargetDoc.id)
        } else {
            const newParentId = resolvedTargetDoc.parentId
            const siblings = options.documents.value
                .filter(d => d.parentId === newParentId && !movingIdSet.has(d.id))
                .sort(sortDocuments)

            const targetIndex = siblings.findIndex(d => d.id === resolvedTargetDoc.id)
            if (targetIndex === -1) return

            const insertIndex = position === 'before' ? targetIndex : targetIndex + 1
            const reordered = [...siblings]
            reordered.splice(insertIndex, 0, ...movingDocs)

            reordered.forEach((doc, index) => {
                const desiredParentId = newParentId
                const desiredOrderNum = (index + 1) * ORDER_SPACING

                if (doc.parentId === desiredParentId && doc.orderNum === desiredOrderNum) return

                const idx = options.documents.value.findIndex(d => d.id === doc.id)
                if (idx !== -1) {
                    options.documents.value[idx] = { ...doc, parentId: desiredParentId, orderNum: desiredOrderNum }
                }

                movePromises.push(updateDocument(options.kbId.value, doc.id, {
                    ...doc,
                    parentId: desiredParentId === null ? -1 : desiredParentId,
                    orderNum: desiredOrderNum
                }))
            })

            options.documents.value = [...options.documents.value]
        }

        if (movePromises.length > 0) {
            try {
                await Promise.all(movePromises)
            } catch (error) {
                console.error('Batch move failed:', error)
                message.error(i18n.global.t('editor.batchMoveFailed'))
                void options.fetchDocuments(true)
            }
        }
    }

    const handleContainerDragOver = (e: DragEvent) => {
        if (!dragState.value.draggingDoc || orderedDocuments.value.length === 0) return

        const dragging = dragState.value.draggingDoc
        const firstDoc = orderedDocuments.value[0]
        const lastDoc = orderedDocuments.value[orderedDocuments.value.length - 1]

        if (!firstDoc || !lastDoc) return

        // 1. 判定最顶部区域（第一个节点顶部及其上方搜索框/标题栏）
        const firstNodeEl = document.querySelector(`[data-doc-id="${firstDoc.id}"]`)
        if (firstNodeEl) {
            const firstRect = firstNodeEl.getBoundingClientRect()
            if (e.clientY <= firstRect.top + 8) {
                if (dragging.id === firstDoc.id || (dragging.type === 'folder' && isDescendantOf(firstDoc.id, dragging.id))) {
                    dragState.value.overDocId = null
                    dragState.value.dropPosition = null
                    return
                }
                e.preventDefault()
                if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
                dragState.value.overDocId = firstDoc.id
                dragState.value.dropPosition = 'before'
                return
            }
        }

        // 2. 判定最底部区域（最后一个节点底部及其下方空白区）
        const lastNodeEl = document.querySelector(`[data-doc-id="${lastDoc.id}"]`)
        if (lastNodeEl) {
            const lastRect = lastNodeEl.getBoundingClientRect()
            if (e.clientY >= lastRect.bottom - 8) {
                if (dragging.id === lastDoc.id || (dragging.type === 'folder' && isDescendantOf(lastDoc.id, dragging.id))) {
                    dragState.value.overDocId = null
                    dragState.value.dropPosition = null
                    return
                }
                e.preventDefault()
                if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
                dragState.value.overDocId = lastDoc.id
                dragState.value.dropPosition = 'after'
                return
            }
        }
    }

    const handleContainerDrop = async (e: DragEvent) => {
        if (!dragState.value.draggingDoc || orderedDocuments.value.length === 0) return

        const firstDoc = orderedDocuments.value[0]
        const lastDoc = orderedDocuments.value[orderedDocuments.value.length - 1]

        if (!firstDoc || !lastDoc) return

        // 顶端释放捕获
        const firstNodeEl = document.querySelector(`[data-doc-id="${firstDoc.id}"]`)
        if (firstNodeEl) {
            const firstRect = firstNodeEl.getBoundingClientRect()
            if (e.clientY <= firstRect.top + firstRect.height / 2) {
                e.preventDefault()
                await handleDrop(e, firstDoc)
                return
            }
        }

        // 底端释放捕获
        const lastNodeEl = document.querySelector(`[data-doc-id="${lastDoc.id}"]`)
        if (lastNodeEl) {
            const lastRect = lastNodeEl.getBoundingClientRect()
            if (e.clientY >= lastRect.top + lastRect.height / 2) {
                e.preventDefault()
                await handleDrop(e, lastDoc)
                return
            }
        }
    }

    const clearSelection = () => {
        selectedDocIds.value.clear()
        lastClickedId.value = null
    }

    return {
        expandedFolders,
        selectedDocIds,
        lastClickedId,
        dragState,
        orderedDocuments,
        toggleFolder,
        handleItemClick,
        handleMove,
        isDescendantOf,
        clearDragState,
        clearSelection,
        handleDragStart,
        handleDragOver,
        handleDragEnd,
        handleDrop,
        handleContainerDragOver,
        handleContainerDrop
    }
}
