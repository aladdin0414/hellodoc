import { computed, ref, type Ref } from 'vue'
import { createDocument } from '../../api/document'
import { message } from '../../utils/message'
import { i18n } from '../../i18n'

export interface CreateFlowDocListItem {
    id: number
    __key?: number
    name: string
    type: string
    parentId: number | null
    orderNum: number
    status?: string
    isCover?: boolean
    content?: string
}

interface UseDocumentCreateFlowOptions {
    kbId: Ref<number>
    documents: Ref<CreateFlowDocListItem[]>
    expandedFolders: Ref<Set<number>>
    sidebarSearchQuery: Ref<string>
    showContextMenu: Ref<boolean>
    showCreateMenu: Ref<boolean>
    fetchDocuments: (silent?: boolean) => Promise<void>
    navigateToDoc: (docId: number) => void
    onCreated?: (docData: any) => void
}

const ORDER_SPACING = 10000

export const useDocumentCreateFlow = (options: UseDocumentCreateFlowOptions) => {
    const creating = ref(false)
    const inlineCreating = ref<{ type: 'file' | 'folder'; parentId: number | null } | null>(null)
    const inlineCreateName = ref('')
    const showCreateModal = ref(false)
    const createType = ref<'file' | 'folder'>('file')
    const newName = ref('')
    const targetParentId = ref<number | null>(null)

    const inlineCreateInsertAfterId = computed(() => {
        if (!inlineCreating.value) return null
        const parentId = inlineCreating.value.parentId
        if (parentId !== null) return parentId
        return null
    })

    const getNextOrderNum = (parentId: number | null) => {
        const siblings = options.documents.value.filter(d => d.parentId === parentId)
        if (siblings.length === 0) {
            return ORDER_SPACING
        }
        const minOrderNum = Math.min(...siblings.map(d => d.orderNum || 0))
        return minOrderNum - ORDER_SPACING
    }

    const applyCreatedDocument = async (docData: any, parentId: number | null, tempKey: number) => {
        const tempIndex = options.documents.value.findIndex(d => d.__key === tempKey)
        if (tempIndex !== -1) {
            options.documents.value[tempIndex] = { ...docData, __key: tempKey }
        } else {
            options.documents.value.push({ ...docData, __key: tempKey })
        }

        await options.fetchDocuments(true)
        options.onCreated?.(docData)
        options.navigateToDoc(docData.id)

        if (parentId !== null) {
            const newSet = new Set(options.expandedFolders.value)
            newSet.add(parentId)
            options.expandedFolders.value = newSet
        }
    }

    const executeCreate = async (nameRaw: string, type: 'file' | 'folder', parentId: number | null) => {
        const name = nameRaw.trim()
        if (!name) return false

        const nextOrderNum = getNextOrderNum(parentId)
        const tempKey = -Date.now()
        const tempDoc: CreateFlowDocListItem = {
            id: tempKey,
            __key: tempKey,
            name,
            type,
            parentId,
            orderNum: nextOrderNum,
            status: type === 'file' ? 'draft' : undefined
        }
        options.documents.value.push(tempDoc)

        creating.value = true
        try {
            const data: any = await createDocument(options.kbId.value, {
                name,
                type,
                parentId,
                orderNum: nextOrderNum,
                content: ''
            })
            await applyCreatedDocument(data, parentId, tempKey)
            return true
        } catch (error) {
            options.documents.value = options.documents.value.filter(d => d.__key !== tempKey)
            console.error('Create failed:', error)
            message.error(i18n.global.t('editor.createFailed'))
            return false
        } finally {
            creating.value = false
        }
    }

    const startInlineCreate = (type: 'file' | 'folder', parentId: number | null = null) => {
        if (options.sidebarSearchQuery.value.trim()) {
            options.sidebarSearchQuery.value = ''
        }
        if (parentId !== null) {
            const newSet = new Set(options.expandedFolders.value)
            newSet.add(parentId)
            options.expandedFolders.value = newSet
        }
        inlineCreating.value = { type, parentId }
        inlineCreateName.value = type === 'folder' ? i18n.global.t('editor.newFolder') : i18n.global.t('editor.newDoc')
        options.showContextMenu.value = false
        options.showCreateMenu.value = false
    }

    const handleConfirmInlineCreate = async () => {
        if (!inlineCreating.value) return
        const payload = inlineCreating.value
        const inputName = inlineCreateName.value
        inlineCreating.value = null
        const success = await executeCreate(inputName, payload.type, payload.parentId)
        if (!success) {
            inlineCreateName.value = ''
        }
    }

    const cancelInlineCreate = () => {
        inlineCreating.value = null
        inlineCreateName.value = ''
    }

    const handleConfirmCreate = async () => {
        const success = await executeCreate(newName.value, createType.value, targetParentId.value)
        if (!success) return
        showCreateModal.value = false
        targetParentId.value = null
        newName.value = ''
    }

    return {
        creating,
        inlineCreating,
        inlineCreateName,
        inlineCreateInsertAfterId,
        showCreateModal,
        createType,
        newName,
        targetParentId,
        startInlineCreate,
        handleConfirmInlineCreate,
        cancelInlineCreate,
        handleConfirmCreate
    }
}
