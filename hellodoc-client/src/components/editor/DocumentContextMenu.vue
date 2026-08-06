<script setup lang="ts">
import { computed } from 'vue'
import { Copy, FileCheck, FileX, Plus, FileText, Ban } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

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

const getDocIconColor = (doc: DocLike | null) => {
    if (!doc) return ''
    return doc.extraMeta?.iconColor || ''
}

const TAG_COLORS = [
    { name: 'Red', value: '#ff5f57' },
    { name: 'Orange', value: '#ff9f0a' },
    { name: 'Yellow', value: '#ffd60a' },
    { name: 'Green', value: '#30d158' },
    { name: 'Blue', value: '#0a84ff' },
    { name: 'Purple', value: '#bf5af2' },
    { name: 'Gray', value: '#98989d' }
]

const props = defineProps<{
    visible: boolean
    menuPosition: { x: number; y: number }
    contextTargetDoc: DocLike | null
    selectedDocIds?: Set<number>
}>()

const isMultiSelected = computed(() => {
    if (!props.selectedDocIds || !props.contextTargetDoc) return false
    return props.selectedDocIds.has(props.contextTargetDoc.id) && props.selectedDocIds.size > 1
})

const emit = defineEmits<{
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
}>()

const { t } = useI18n()

const safeDocAction = (handler: (doc: DocLike) => void, doc: DocLike | null) => {
    if (!doc) return
    handler(doc)
}
</script>

<template>
    <div v-if="visible"
        class="fixed z-[100] bg-white dark:bg-[#161b22] rounded-xl shadow-xl border border-slate-100 dark:border-gray-700 py-1.5 min-w-[165px]"
        :style="{ left: menuPosition.x + 'px', top: menuPosition.y + 'px' }" @click.stop>
        <!-- macOS 风格颜色标记面板 -->
        <div v-if="!isMultiSelected && contextTargetDoc" class="px-3 py-1.5 border-b border-slate-100 dark:border-gray-700/80 mb-1 flex items-center justify-between">
            <div class="flex items-center space-x-1.5">
                <button v-for="color in TAG_COLORS" :key="color.value"
                    @click="safeDocAction((doc) => emit('setIconColor', doc, getDocIconColor(doc) === color.value ? '' : color.value), contextTargetDoc)"
                    class="w-4 h-4 rounded-full transition-transform hover:scale-125 focus:outline-none flex items-center justify-center shadow-sm"
                    :style="{ backgroundColor: color.value }">
                    <span v-if="getDocIconColor(contextTargetDoc) === color.value" class="w-1.5 h-1.5 rounded-full bg-white/90"></span>
                </button>
            </div>
            <button v-if="getDocIconColor(contextTargetDoc)"
                @click="safeDocAction((doc) => emit('setIconColor', doc, ''), contextTargetDoc)"
                class="ml-2 text-slate-400 hover:text-rose-500 dark:text-slate-500 dark:hover:text-rose-400 p-0.5 rounded transition-colors flex items-center justify-center"
                :title="t('editorMenu.resetColor')">
                <Ban :size="13" />
            </button>
        </div>
        <template v-if="!isMultiSelected && contextTargetDoc?.type === 'folder'">
            <button @click="safeDocAction((doc) => emit('startInlineCreate', 'folder', doc.id), contextTargetDoc)"
                class="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 hover:text-indigo-600 flex items-center space-x-2.5 transition-colors font-medium">
                <div class="relative flex-shrink-0 flex items-center justify-center">
                    <svg class="h-3.5 w-3.5 text-blue-500" viewBox="0 0 24 24" fill="currentColor">
                        <path d="M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.93a2 2 0 0 1-1.66-.9l-.82-1.2A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z" />
                    </svg>
                    <Plus :size="8" class="absolute text-white stroke-[3.5px]" />
                </div>
                <span>{{ t('editorMenu.newSubFolder') }}</span>
            </button>
            <button @click="safeDocAction((doc) => emit('startInlineCreate', 'file', doc.id), contextTargetDoc)"
                class="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 hover:text-indigo-600 flex items-center space-x-2.5 transition-colors font-medium">
                <FileText :size="14" class="text-slate-400 dark:text-slate-500 flex-shrink-0" />
                <span>{{ t('editorMenu.newSubDoc') }}</span>
            </button>

            <div class="h-px bg-slate-100 dark:bg-gray-700 my-1"></div>
            <button @click="safeDocAction((doc) => emit('folderToggleStatus', doc, 'published'), contextTargetDoc)"
                class="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 hover:text-indigo-600 flex items-center space-x-2.5 transition-colors font-medium">
                <FileCheck :size="14" class="text-emerald-500" />
                <span>{{ t('editorMenu.publishAll') }}</span>
            </button>
            <button @click="safeDocAction((doc) => emit('folderToggleStatus', doc, 'draft'), contextTargetDoc)"
                class="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 hover:text-indigo-600 flex items-center space-x-2.5 transition-colors font-medium">
                <FileX :size="14" class="text-rose-500" />
                <span>{{ t('editorMenu.unpublishAll') }}</span>
            </button>
            <div class="h-px bg-slate-100 dark:bg-gray-700 my-1"></div>
        </template>

        <button @click="safeDocAction((doc) => emit('moveDirection', doc, 'up'), contextTargetDoc)"
            class="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 hover:text-indigo-600 flex items-center space-x-2.5 transition-colors font-medium">
            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 15l7-7 7 7" />
            </svg>
            <span>{{ t('editorMenu.moveUp') }}</span>
        </button>
        <button @click="safeDocAction((doc) => emit('moveDirection', doc, 'down'), contextTargetDoc)"
            class="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 hover:text-indigo-600 flex items-center space-x-2.5 transition-colors font-medium">
            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
            </svg>
            <span>{{ t('editorMenu.moveDown') }}</span>
        </button>
        <div class="h-px bg-slate-100 dark:bg-gray-700 my-1"></div>
        <button @click="emit('copyToKb')"
            class="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 hover:text-indigo-600 flex items-center space-x-2.5 transition-colors font-medium">
            <Copy :size="14" class="text-indigo-500" />
            <span>{{ t('editorMenu.copyToKb') }}</span>
        </button>
        <button @click="safeDocAction((doc) => emit('openMoveModal', doc), contextTargetDoc)"
            class="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 hover:text-indigo-600 flex items-center space-x-2.5 transition-colors font-medium">
            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
            </svg>
            <span>{{ t('editorMenu.moveTo') }}</span>
        </button>
        <button @click="safeDocAction((doc) => emit('duplicate', doc), contextTargetDoc)"
            class="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 hover:text-indigo-600 flex items-center space-x-2.5 transition-colors font-medium">
            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M8 7v8a2 2 0 002 2h6M8 7V5a2 2 0 012-2h4.586a1 1 0 01.707.293l4.414 4.414a1 1 0 01.293.707V15a2 2 0 01-2 2h-2M8 7H6a2 2 0 00-2 2v10a2 2 0 002 2h8a2 2 0 002-2v-2" />
            </svg>
            <span>{{ t('editorMenu.duplicate') }}</span>
        </button>
        <template v-if="!isMultiSelected">
            <div class="h-px bg-slate-100 dark:bg-gray-700 my-1"></div>
            <button @click="safeDocAction((doc) => emit('startRename', doc), contextTargetDoc)"
                class="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 hover:text-indigo-600 flex items-center space-x-2.5 transition-colors font-medium">
                <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
                <span>{{ t('editorMenu.rename') }}</span>
            </button>
        </template>
        <template v-if="!isMultiSelected && contextTargetDoc?.type === 'file'">
            <div class="h-px bg-slate-100 dark:bg-gray-700 my-1"></div>
            <button @click="safeDocAction((doc) => emit('toggleStatus', doc), contextTargetDoc)"
                class="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 hover:text-indigo-600 flex items-center space-x-2.5 transition-colors font-medium">
                <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
                <span>{{ contextTargetDoc?.status === 'published' ? t('editorMenu.toDraft') : t('editorMenu.publishDoc') }}</span>
            </button>
        </template>
        <template v-if="!isMultiSelected && contextTargetDoc?.type !== 'folder'">
            <div class="h-px bg-slate-100 dark:bg-gray-700 my-1"></div>
            <button @click="safeDocAction((doc) => emit('setCover', doc), contextTargetDoc)"
                class="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 hover:text-indigo-600 flex items-center space-x-2.5 transition-colors font-medium">
                <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path v-if="contextTargetDoc?.isCover" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
                    <path v-else stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
                </svg>
                <span>{{ contextTargetDoc?.isCover ? t('editorMenu.unsetCover') : t('editorMenu.setCover') }}</span>
            </button>
        </template>
        <button @click="safeDocAction((doc) => emit('deleteDoc', doc), contextTargetDoc)"
            class="w-full text-left px-4 py-2 text-sm text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-900/30 flex items-center space-x-2.5 transition-colors font-medium">
            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
            <span>{{ t('editorMenu.delete') }}</span>
        </button>
    </div>
</template>
