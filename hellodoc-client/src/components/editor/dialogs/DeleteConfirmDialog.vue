<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import BaseDialog from '../../shared/BaseDialog.vue'

interface DocLike {
    id: number
    name: string
    type?: string
}

defineProps<{
    visible: boolean
    deletingDoc: DocLike | null
    isDeleting: boolean
    subItemCount?: number
    selectedItemCount?: number
}>()

const emit = defineEmits<{
    close: []
    confirm: []
}>()

const { t } = useI18n()
</script>

<template>
    <BaseDialog :show="visible" z-index-class="z-[200]" max-width-class="max-w-lg"
        panel-class="ring-1 ring-slate-900/5 dark:ring-gray-700" @close="emit('close')">
                <div class="bg-white dark:bg-[#161b22] px-4 pt-5 pb-4 sm:p-6 sm:pb-4">
                    <div class="sm:flex sm:items-start">
                        <div
                            class="mx-auto flex-shrink-0 flex items-center justify-center h-12 w-12 rounded-full bg-rose-100 dark:bg-rose-950/50 sm:mx-0 sm:h-10 sm:w-10">
                            <svg class="h-6 w-6 text-rose-600 dark:text-rose-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                            </svg>
                        </div>
                        <div class="mt-3 text-center sm:mt-0 sm:ml-4 sm:text-left">
                            <h3 class="text-lg leading-6 font-bold text-slate-900 dark:text-gray-100" id="modal-title">{{ t('editor.confirmDeleteTitle') }}</h3>
                            <div class="mt-2">
                                <template v-if="selectedItemCount && selectedItemCount > 1">
                                    <p v-if="subItemCount && subItemCount > 0" class="text-sm text-slate-600 dark:text-gray-300 leading-relaxed">
                                        确定要将选中的 <span class="font-bold text-slate-900 dark:text-gray-100">{{ selectedItemCount }}</span> 个项目移入回收站吗？该操作将同时移入其下的 <span class="font-bold text-rose-600 dark:text-rose-400">{{ subItemCount }}</span> 个子文档及目录。
                                    </p>
                                    <p v-else class="text-sm text-slate-600 dark:text-gray-300 leading-relaxed">
                                        确定要将选中的 <span class="font-bold text-slate-900 dark:text-gray-100">{{ selectedItemCount }}</span> 个项目移入回收站吗？
                                    </p>
                                </template>
                                <template v-else-if="deletingDoc?.type === 'folder'">
                                    <p v-if="subItemCount && subItemCount > 0" class="text-sm text-slate-600 dark:text-gray-300 leading-relaxed">
                                        确定要将文件夹 <span class="font-bold text-slate-900 dark:text-gray-100">"{{ deletingDoc?.name }}"</span> 移入回收站吗？该操作将同时移入其下的 <span class="font-bold text-rose-600 dark:text-rose-400">{{ subItemCount }}</span> 个子文档及目录。
                                    </p>
                                    <p v-else class="text-sm text-slate-600 dark:text-gray-300 leading-relaxed">
                                        确定要将文件夹 <span class="font-bold text-slate-900 dark:text-gray-100">"{{ deletingDoc?.name }}"</span> 移入回收站吗？
                                    </p>
                                </template>
                                <template v-else>
                                    <p class="text-sm text-slate-600 dark:text-gray-300 leading-relaxed">
                                        确定要将文档 <span class="font-bold text-slate-900 dark:text-gray-100">"{{ deletingDoc?.name }}"</span> 移入回收站吗？
                                    </p>
                                </template>
                                <p class="mt-2.5 text-xs text-rose-500 dark:text-rose-400 font-bold flex items-center">
                                    <span>{{ t('editor.deleteWarning') }}</span>
                                </p>
                            </div>
                        </div>
                    </div>
                </div>
                <div
                    class="bg-slate-50 dark:bg-[#161b22] px-4 py-3 sm:px-6 sm:flex sm:flex-row-reverse space-x-reverse space-x-3 border-t border-slate-100 dark:border-gray-700">
                    <button type="button" @click="emit('confirm')" :disabled="isDeleting"
                        class="w-full inline-flex justify-center rounded-xl border border-transparent px-5 py-2 text-sm font-semibold bg-rose-600 text-white hover:bg-rose-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-rose-500 sm:ml-3 sm:w-auto transition-all active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed">
                        <svg v-if="isDeleting" class="animate-spin -ml-1 mr-2 h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                            <path class="opacity-75" fill="currentColor"
                                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z">
                            </path>
                        </svg>
                        {{ isDeleting ? t('editor.deleting') : t('editor.confirmDeleteTitle') }}
                    </button>
                    <button type="button" @click="emit('close')"
                        class="mt-3 w-full inline-flex justify-center rounded-xl border border-slate-300 dark:border-gray-600 px-5 py-2 text-sm font-medium bg-white dark:bg-[#161b22] text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 sm:mt-0 sm:w-auto transition-all">
                        {{ t('nav.cancel') }}
                    </button>
                </div>
    </BaseDialog>
</template>
