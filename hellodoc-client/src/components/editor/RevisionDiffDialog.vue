<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Diff } from 'vue-diff'
import 'vue-diff/dist/index.css'
import BaseDialog from '../shared/BaseDialog.vue'

defineProps<{
    visible: boolean
    selectedRevision: any
    diffLoading: boolean
    diffCompareData: { prev: string; current: string }
    isDark: boolean
}>()

const emit = defineEmits<{
    close: []
    restore: []
}>()

const { t } = useI18n()
</script>

<template>
    <BaseDialog :show="visible" z-index-class="z-[300]" max-width-class="max-w-none"
        panel-class="w-11/12 h-5/6 flex flex-col animate-in fade-in zoom-in duration-200 dark:bg-[#161b22]" @close="emit('close')">
            <div
                class="px-6 py-4 border-b border-slate-200/50 dark:border-slate-800 flex items-center justify-between shrink-0 bg-white dark:bg-[#161b22]">
                <div class="flex items-center space-x-4">
                    <h3 class="text-lg font-bold text-slate-900 dark:text-gray-100">
                        {{ t('editor.diffTitle', { version: selectedRevision?.version }) }}
                    </h3>
                    <span v-if="diffLoading" class="inline-flex items-center text-xs text-indigo-500 font-medium">
                        <svg class="animate-spin -ml-1 mr-2 h-4 w-4" fill="none" viewBox="0 0 24 24">
                            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                            <path class="opacity-75" fill="currentColor"
                                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z">
                            </path>
                        </svg>
                        {{ t('common.loading') }}
                    </span>
                </div>

                <div class="flex items-center space-x-3">
                    <button @click="emit('restore')"
                        class="px-4 py-2 bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 font-bold text-sm rounded-xl hover:bg-indigo-100 dark:hover:bg-indigo-900/50 transition-colors">
                        {{ t('editor.restoreToRevision') }}
                    </button>
                    <button @click="emit('close')"
                        class="p-2 text-slate-400 hover:bg-slate-100 dark:hover:bg-gray-800 rounded-xl transition-colors">
                        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    </button>
                </div>
            </div>

            <div class="flex-1 overflow-auto bg-slate-50 dark:bg-[#161b22]/50 p-4 scrollbar-subtle">
                <template v-if="!diffLoading">
                    <Diff mode="split" :theme="isDark ? 'dark' : 'light'" :prev="diffCompareData.prev"
                        :current="diffCompareData.current" language="markdown"
                        class="rounded-xl overflow-hidden border border-slate-200 dark:border-gray-700 shadow-sm" />
                </template>
            </div>
    </BaseDialog>
</template>
