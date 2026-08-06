<script setup lang="ts">
import DocumentContextMenu from './DocumentContextMenu.vue'
import CreateItemDialog from './dialogs/CreateItemDialog.vue'
import MoveItemDialog from './dialogs/MoveItemDialog.vue'
import DeleteConfirmDialog from './dialogs/DeleteConfirmDialog.vue'
import CopyToKbDialog from './dialogs/CopyToKbDialog.vue'
import TrashDialog from './dialogs/TrashDialog.vue'

interface DocLike {
    id: number
    name: string
    type: string
    status?: string
    isCover?: boolean
    extraMeta?: Record<string, any>
    parentId: number | null
    orderNum: number
}

interface MoveFolderLike extends DocLike {
    depth: number
    hasChildren: boolean
    isExpanded: boolean
}

defineProps<{
    showContextMenu: boolean
    menuPosition: { x: number; y: number }
    contextTargetDoc: DocLike | null
    showCreateModal: boolean
    createType: 'file' | 'folder'
    newName: string
    creating: boolean
    showMoveModal: boolean
    selectedTargetFolderId: number | null
    availableFolders: MoveFolderLike[]
    showDeleteModal: boolean
    deletingDoc: DocLike | null
    isDeleting: boolean
    subItemCount?: number
    selectedItemCount?: number
    showKbCopyDialog: boolean
    kbSearchQuery: string
    filteredCopyableKbs: any[]
    selectedKbForCopy: any
    copyLoading: boolean
    setCreateInputRef: (el: any) => void
    showTrashModal: boolean
    kbId: number
    selectedDocIds?: Set<number>
}>()

const emit = defineEmits<{
    updateShowContextMenu: [value: boolean]
    updateShowCreateModal: [value: boolean]
    updateNewName: [value: string]
    confirmCreate: []
    startInlineCreate: [type: 'file' | 'folder', parentId?: number]
    folderToggleStatus: [doc: DocLike, status: 'published' | 'draft']
    moveDirection: [doc: DocLike, direction: 'up' | 'down']
    copyToKb: []
    openMoveModal: [doc: DocLike]
    duplicate: [doc: DocLike]
    startRename: [doc: DocLike]
    toggleStatus: [doc: DocLike]
    setCover: [doc: DocLike]
    setIconColor: [doc: DocLike, color: string]
    deleteDoc: [doc: DocLike]
    updateShowMoveModal: [value: boolean]
    updateSelectedTargetFolderId: [value: number | null]
    toggleMoveFolder: [folderId: number]
    confirmMove: []
    updateShowDeleteModal: [value: boolean]
    confirmDelete: []
    updateShowKbCopyDialog: [value: boolean]
    updateKbSearchQuery: [value: string]
    updateSelectedKbForCopy: [value: any]
    confirmCopyToKb: []
    updateShowTrashModal: [value: boolean]
    trashRestored: []
}>()
</script>

<template>
    <!-- Context Menu Overlay for clicking outside to close -->
    <div v-if="showContextMenu" class="fixed inset-0 z-[90]" @click="emit('updateShowContextMenu', false)"></div>

    <DocumentContextMenu :visible="showContextMenu" :menu-position="menuPosition" :context-target-doc="contextTargetDoc"
        :selected-doc-ids="selectedDocIds"
        @start-inline-create="(type: any, parentId?: any) => emit('startInlineCreate', type, parentId)"
        @folder-toggle-status="(doc: any, status: any) => emit('folderToggleStatus', doc, status)"
        @move-direction="(doc: any, direction: any) => emit('moveDirection', doc, direction)"
        @copy-to-kb="emit('copyToKb')"
        @open-move-modal="(doc: any) => emit('openMoveModal', doc)"
        @duplicate="(doc: any) => emit('duplicate', doc)"
        @start-rename="(doc: any) => emit('startRename', doc)"
        @toggle-status="(doc: any) => emit('toggleStatus', doc)"
        @set-cover="(doc: any) => emit('setCover', doc)"
        @set-icon-color="(doc: any, color: any) => emit('setIconColor', doc, color)"
        @delete-doc="(doc: any) => emit('deleteDoc', doc)" />

    <CreateItemDialog :visible="showCreateModal" :create-type="createType" :new-name="newName" :creating="creating"
        :set-create-input-ref="setCreateInputRef" @close="emit('updateShowCreateModal', false)"
        @update-name="(value) => emit('updateNewName', value)" @confirm="emit('confirmCreate')" />

    <MoveItemDialog :visible="showMoveModal" :selected-target-folder-id="selectedTargetFolderId"
        :available-folders="availableFolders" @close="emit('updateShowMoveModal', false)"
        @update-selected-target-folder-id="(value) => emit('updateSelectedTargetFolderId', value)"
        @toggle-move-folder="(folderId) => emit('toggleMoveFolder', folderId)" @confirm="emit('confirmMove')" />

    <DeleteConfirmDialog :visible="showDeleteModal" :deleting-doc="deletingDoc" :is-deleting="isDeleting"
        :sub-item-count="subItemCount" :selected-item-count="selectedItemCount" @close="emit('updateShowDeleteModal', false)" @confirm="emit('confirmDelete')" />

    <CopyToKbDialog :visible="showKbCopyDialog" :kb-search-query="kbSearchQuery"
        :filtered-copyable-kbs="filteredCopyableKbs" :selected-kb-for-copy="selectedKbForCopy" :copy-loading="copyLoading"
        @close="emit('updateShowKbCopyDialog', false)" @update-kb-search-query="(value) => emit('updateKbSearchQuery', value)"
        @update-selected-kb-for-copy="(value) => emit('updateSelectedKbForCopy', value)"
        @confirm="emit('confirmCopyToKb')" />

    <TrashDialog :visible="showTrashModal" :kb-id="kbId"
        @close="emit('updateShowTrashModal', false)"
        @restored="emit('trashRestored')" />
</template>
