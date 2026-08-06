<script setup lang="ts">
import { ChevronRight, ChevronDown, Folder } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseDialog from '../../shared/BaseDialog.vue'

interface MoveFolderLike {
    id: number
    name: string
    depth: number
    hasChildren: boolean
    isExpanded: boolean
}

defineProps<{
    visible: boolean
    selectedTargetFolderId: number | null
    availableFolders: MoveFolderLike[]
}>()

const emit = defineEmits<{
    close: []
    updateSelectedTargetFolderId: [value: number | null]
    toggleMoveFolder: [folderId: number]
    confirm: []
}>()

const { t } = useI18n()
</script>

<template>
    <BaseDialog :show="visible" max-width-class="max-w-sm" panel-class="animate-in fade-in zoom-in duration-200 ring-1 ring-slate-900/5 dark:ring-gray-700"
        @close="emit('close')">
            <div class="p-6">
                <h3 class="text-lg font-bold text-slate-900 dark:text-gray-100 mb-6">{{ t('editor.moveTo') }}</h3>
                <div class="max-h-64 overflow-y-auto border border-slate-200 dark:border-gray-600 rounded-xl scrollbar-subtle">
                    <div class="p-2 space-y-1">
                        <button @click="emit('updateSelectedTargetFolderId', null)"
                            class="w-full text-left px-3 py-2.5 rounded-lg text-sm transition-all flex items-center space-x-2.5 font-medium"
                            :class="selectedTargetFolderId === null ? 'bg-indigo-50 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 ring-1 ring-indigo-200 dark:ring-indigo-800' : 'text-slate-600 dark:text-gray-400 hover:bg-slate-50 dark:hover:bg-gray-700'">
                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
                            </svg>
                            <span>{{ t('editor.rootFolder') }}</span>
                        </button>
                        <div v-for="folder in availableFolders" :key="folder.id"
                            class="group flex items-center rounded-lg transition-all"
                            :class="selectedTargetFolderId === folder.id ? 'bg-indigo-50 dark:bg-indigo-900/30 ring-1 ring-indigo-200 dark:ring-indigo-800' : 'hover:bg-slate-50 dark:hover:bg-gray-700/50'">
                            <div class="flex items-center flex-1 h-10 pr-2" :style="{ paddingLeft: (folder.depth * 16 + 8) + 'px' }">
                                <button v-if="folder.hasChildren" @click.stop="emit('toggleMoveFolder', folder.id)"
                                    class="p-1 hover:bg-slate-200 dark:hover:bg-gray-600 rounded-md transition-colors mr-1">
                                    <ChevronDown v-if="folder.isExpanded" class="h-3.5 w-3.5 text-slate-500" />
                                    <ChevronRight v-else class="h-3.5 w-3.5 text-slate-500" />
                                </button>
                                <div v-else class="w-5.5 mr-1"></div>
                                <button @click="emit('updateSelectedTargetFolderId', folder.id)"
                                    class="flex-1 text-left flex items-center space-x-2.5 min-w-0">
                                    <Folder class="h-4 w-4 shrink-0"
                                        :class="selectedTargetFolderId === folder.id ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-400'" />
                                    <span class="truncate text-sm font-medium"
                                        :class="selectedTargetFolderId === folder.id ? 'text-indigo-700 dark:text-indigo-300' : 'text-slate-600 dark:text-gray-300'">
                                        {{ folder.name }}
                                    </span>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div
                class="px-6 py-4 bg-slate-50 dark:bg-[#161b22] flex justify-end space-x-3 border-t border-slate-100 dark:border-gray-700">
                <button @click="emit('close')" class="px-5 py-2 text-sm font-medium text-slate-700 dark:text-gray-300 rounded-xl hover:bg-slate-100 dark:hover:bg-gray-700 transition-all">
                    {{ t('nav.cancel') }}
                </button>
                <button @click="emit('confirm')"
                    class="px-5 py-2 text-sm font-semibold bg-indigo-600 text-white rounded-xl hover:bg-indigo-700 transition-all active:scale-95">
                    {{ t('editor.confirmMove') }}
                </button>
            </div>
    </BaseDialog>
</template>
