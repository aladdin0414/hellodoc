import { ref, type Ref } from 'vue'
import { getDocuments, updateDocument, deleteDocument, duplicateDocument } from '../../api/document'
import { message } from '../../utils/message'
import { expandAncestorFolders } from '../../utils/documentTree'
import { i18n } from '../../i18n'

export interface DocumentActionItem {
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

interface UseDocumentActionsOptions {
    kbId: Ref<number>
    routeDocId: Ref<number | undefined>
    documents: Ref<DocumentActionItem[]>
    currentDoc: Ref<DocumentActionItem | null>
    activeDocId: Ref<number | null>
    expandedFolders: Ref<Set<number>>
    selectedDocIds?: Ref<Set<number>>
    selectDocument: (doc: DocumentActionItem) => Promise<boolean> | boolean
    navigateToEditorBase: () => void
}

const ORDER_SPACING = 10000

export const useDocumentActions = (options: UseDocumentActionsOptions) => {
    const loading = ref(false)
    const isDeleting = ref(false)
    const latestFetchRequestId = ref(0)
    const pendingNonSilentFetchCount = ref(0)

    const fetchDocuments = async (silent = false) => {
        const requestId = ++latestFetchRequestId.value
        if (!silent) {
            pendingNonSilentFetchCount.value += 1
            loading.value = true
        }
        try {
            const data: any = await getDocuments(options.kbId.value)
            if (requestId !== latestFetchRequestId.value) return
            options.documents.value = data

            if (!silent && options.routeDocId.value) {
                const target = options.documents.value.find(d => d.id === options.routeDocId.value)
                if (target) {
                    expandAncestorFolders(options.expandedFolders.value, options.documents.value, target.id)
                    options.selectDocument(target)
                }
            }
        } catch (error) {
            console.error('Fetch documents failed:', error)
        } finally {
            if (!silent) {
                pendingNonSilentFetchCount.value = Math.max(0, pendingNonSilentFetchCount.value - 1)
                loading.value = pendingNonSilentFetchCount.value > 0
            }
        }
    }

    const handleFolderToggleStatus = async (folder: DocumentActionItem, targetStatus: 'published' | 'draft') => {
        const affectedDocs: DocumentActionItem[] = []
        const collectFiles = (parentId: number) => {
            const children = options.documents.value.filter(d => d.parentId === parentId)
            children.forEach(child => {
                if (child.type === 'file') {
                    affectedDocs.push(child)
                } else if (child.type === 'folder') {
                    collectFiles(child.id)
                }
            })
        }
        collectFiles(folder.id)

        if (affectedDocs.length === 0) {
            message.info(i18n.global.t('editor.folderNoOperableDocs'))
            return false
        }

        const actionText = targetStatus === 'published' ? i18n.global.t('editor.publishAll') : i18n.global.t('editor.unpublishAll')

        affectedDocs.forEach(doc => {
            doc.status = targetStatus
            if (options.currentDoc.value && options.currentDoc.value.id === doc.id) {
                options.currentDoc.value.status = targetStatus
            }
        })

        try {
            await Promise.all(affectedDocs.map(doc =>
                updateDocument(options.kbId.value, doc.id, {
                    id: doc.id,
                    name: doc.name,
                    status: targetStatus,
                    type: doc.type,
                    parentId: doc.parentId,
                    orderNum: doc.orderNum
                })
            ))
            message.success(i18n.global.t('editor.actionSuccess', { action: actionText }))
            return true
        } catch (error) {
            console.error(`${actionText} failed:`, error)
            message.error(i18n.global.t('editor.actionFailed', { action: actionText }))
            fetchDocuments(true)
            return false
        }
    }

    const handleToggleStatus = async (targetDoc?: DocumentActionItem) => {
        const doc = targetDoc || options.currentDoc.value
        if (!doc) return false
        const originalStatus = doc.status
        const newStatus = originalStatus === 'published' ? 'draft' : 'published'

        doc.status = newStatus
        if (options.currentDoc.value && options.currentDoc.value.id === doc.id) {
            options.currentDoc.value.status = newStatus
        }

        const index = options.documents.value.findIndex(d => d.id === doc.id)
        if (index !== -1 && options.documents.value[index]) {
            options.documents.value[index].status = newStatus
        }

        try {
            await updateDocument(options.kbId.value, doc.id, {
                id: doc.id,
                name: doc.name,
                status: newStatus,
                type: doc.type
            })
            return true
        } catch (error) {
            console.error('Toggle status failed:', error)
            doc.status = originalStatus
            if (options.currentDoc.value && options.currentDoc.value.id === doc.id) {
                options.currentDoc.value.status = originalStatus
            }
            if (index !== -1 && options.documents.value[index]) {
                options.documents.value[index].status = originalStatus
            }
            message.error(i18n.global.t('editor.updateStatusFailed'))
            return false
        }
    }

    const handleConfirmDelete = async (doc: DocumentActionItem | null) => {
        if (!doc) return false
        isDeleting.value = true

        let targetDocs: DocumentActionItem[] = []
        if (options.selectedDocIds?.value && options.selectedDocIds.value.has(doc.id) && options.selectedDocIds.value.size > 1) {
            targetDocs = options.documents.value.filter(d => options.selectedDocIds!.value.has(d.id))
        } else {
            targetDocs = [doc]
        }

        const originalDocs = [...options.documents.value]
        const removeIds = new Set<number>()
        const collectIds = (id: number) => {
            removeIds.add(id)
            options.documents.value.filter(d => d.parentId === id).forEach(child => collectIds(child.id))
        }

        targetDocs.forEach(d => collectIds(d.id))
        options.documents.value = options.documents.value.filter(d => !removeIds.has(d.id))

        if (options.currentDoc.value && removeIds.has(options.currentDoc.value.id)) {
            options.currentDoc.value = null
            options.activeDocId.value = null
            options.navigateToEditorBase()
        }

        try {
            await Promise.all(targetDocs.map(d => deleteDocument(options.kbId.value, d.id)))
            if (options.selectedDocIds?.value) {
                options.selectedDocIds.value.clear()
            }
            await fetchDocuments(true)
            return true
        } catch (error) {
            options.documents.value = originalDocs
            console.error('Delete failed:', error)
            message.error(i18n.global.t('editor.deleteFailed'))
            fetchDocuments(true)
            return false
        } finally {
            isDeleting.value = false
        }
    }

    const handleDuplicate = async (doc: DocumentActionItem) => {
        let targetDocs: DocumentActionItem[] = []
        if (options.selectedDocIds?.value && options.selectedDocIds.value.has(doc.id) && options.selectedDocIds.value.size > 1) {
            targetDocs = options.documents.value.filter(d => options.selectedDocIds!.value.has(d.id))
        } else {
            targetDocs = [doc]
        }

        const originalDocs = [...options.documents.value]
        const tempCopies: { targetDoc: DocumentActionItem; tempDoc: DocumentActionItem }[] = []

        for (const targetDoc of targetDocs) {
            const siblings = options.documents.value.filter(d => d.parentId === targetDoc.parentId)
            const maxOrderNum = siblings.length > 0 ? Math.max(...siblings.map(d => d.orderNum || 0)) : -1
            const nextOrderNum = maxOrderNum + ORDER_SPACING

            const tempCopy: DocumentActionItem = {
                ...targetDoc,
                id: -Date.now() - Math.floor(Math.random() * 1000),
                name: `${targetDoc.name} (${i18n.global.t('editor.copy')})`,
                orderNum: nextOrderNum,
                status: 'draft'
            }

            options.documents.value.push(tempCopy)
            tempCopies.push({ targetDoc, tempDoc: tempCopy })
        }

        try {
            await Promise.all(
                tempCopies.map(({ targetDoc }) => duplicateDocument(options.kbId.value, targetDoc.id))
            )

            const newSet = new Set(options.expandedFolders.value)
            let needExpand = false
            targetDocs.forEach(targetDoc => {
                if (targetDoc.parentId !== null) {
                    newSet.add(targetDoc.parentId)
                    needExpand = true
                }
            })
            if (needExpand) {
                options.expandedFolders.value = newSet
            }

            await fetchDocuments(true)
            message.success(i18n.global.t('editor.createCopySuccess'))
            return true
        } catch (error) {
            options.documents.value = originalDocs
            console.error('Duplicate failed:', error)
            message.error(i18n.global.t('editor.createCopyFailed'))
            return false
        }
    }

    const handleSetCover = async (doc: DocumentActionItem) => {
        const originalDocs = JSON.parse(JSON.stringify(options.documents.value))
        const isCurrentlyCover = doc.isCover

        options.documents.value = options.documents.value.map(d => ({
            ...d,
            isCover: isCurrentlyCover ? false : d.id === doc.id
        }))

        try {
            await updateDocument(options.kbId.value, doc.id, { ...doc, isCover: !isCurrentlyCover })
            await fetchDocuments(true)
            return true
        } catch (error) {
            options.documents.value = originalDocs
            console.error('Set cover failed:', error)
            message.error(isCurrentlyCover ? i18n.global.t('editor.unsetCoverFailed') : i18n.global.t('editor.setCoverFailed'))
            fetchDocuments(true)
            return false
        }
    }

    const handleConfirmMove = async (doc: DocumentActionItem | null, targetParentId: number | null) => {
        if (!doc) return false
        if (doc.parentId === targetParentId) {
            return true
        }

        const originalParentId = doc.parentId
        const originalOrderNum = doc.orderNum
        const targetSiblings = options.documents.value.filter(d => d.parentId === targetParentId && d.id !== doc.id)
        const maxOrderNum = targetSiblings.length > 0 ? Math.max(...targetSiblings.map(d => d.orderNum || 0)) : -1
        const newOrderNum = maxOrderNum + ORDER_SPACING

        doc.parentId = targetParentId
        doc.orderNum = newOrderNum

        try {
            await updateDocument(options.kbId.value, doc.id, {
                ...doc,
                parentId: targetParentId === null ? -1 : targetParentId,
                orderNum: newOrderNum
            })
            await fetchDocuments(true)
            return true
        } catch (error) {
            doc.parentId = originalParentId
            doc.orderNum = originalOrderNum
            console.error('Move failed:', error)
            message.error(i18n.global.t('editor.moveFailed'))
            fetchDocuments(true)
            return false
        }
    }

    const handleSetExtraMeta = async (doc: DocumentActionItem, metaUpdate: Record<string, any>) => {
        const originalDocs = JSON.parse(JSON.stringify(options.documents.value))
        const newExtraMeta = { ...(doc.extraMeta || {}), ...metaUpdate }

        options.documents.value = options.documents.value.map(d => {
            if (d.id === doc.id) {
                return { ...d, ...metaUpdate, extraMeta: newExtraMeta }
            }
            return d
        })

        try {
            await updateDocument(options.kbId.value, doc.id, {
                ...doc,
                ...metaUpdate,
                extraMeta: newExtraMeta
            })
            void fetchDocuments(true)
            return true
        } catch (error) {
            options.documents.value = originalDocs
            console.error('Set extra meta failed:', error)
            void fetchDocuments(true)
            return false
        }
    }

    const handleSetIconColor = async (doc: DocumentActionItem, color: string) => {
        return handleSetExtraMeta(doc, { iconColor: color })
    }

    return {
        loading,
        isDeleting,
        fetchDocuments,
        handleFolderToggleStatus,
        handleToggleStatus,
        handleConfirmDelete,
        handleDuplicate,
        handleSetCover,
        handleSetIconColor,
        handleSetExtraMeta,
        handleConfirmMove
    }
}
