<script setup lang="ts">
import { Plus, Search, Folder, FileText, Copy } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseDialog from '../../shared/BaseDialog.vue'

defineProps<{
    visible: boolean
    kbSearchQuery: string
    filteredCopyableKbs: any[]
    selectedKbForCopy: any
    copyLoading: boolean
}>()

const emit = defineEmits<{
    close: []
    updateKbSearchQuery: [value: string]
    updateSelectedKbForCopy: [value: any]
    confirm: []
}>()

const { t } = useI18n()
</script>

<template>
    <BaseDialog :show="visible" z-index-class="z-[110]" max-width-class="max-w-lg"
        panel-class="flex flex-col max-h-[85vh] animate-in fade-in zoom-in duration-200" @close="emit('close')">
            <div class="p-5 border-b border-slate-100 dark:border-gray-800 flex items-center justify-between">
                <div>
                    <h3 class="text-lg font-bold text-slate-800 dark:text-gray-100">{{ t('editor.copyToKb') }}</h3>
                    <p class="text-xs text-slate-500 dark:text-gray-400 mt-0.5">{{ t('editor.copyToKbDesc') }}</p>
                </div>
                <button @click="emit('close')" class="p-2 hover:bg-slate-100 dark:hover:bg-gray-800 rounded-lg transition-colors">
                    <Plus class="w-5 h-5 rotate-45 text-slate-400" />
                </button>
            </div>
            <div class="px-5 py-3 bg-slate-50/50 dark:bg-gray-900/30">
                <div class="relative group">
                    <Search
                        class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 group-focus-within:text-indigo-500 transition-colors" />
                    <input :value="kbSearchQuery" type="text" :placeholder="t('editor.searchKb')"
                        @input="emit('updateKbSearchQuery', ($event.target as HTMLInputElement).value)"
                        class="w-full pl-10 pr-4 py-2 bg-white dark:bg-gray-800 border border-slate-200 dark:border-gray-700 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all" />
                </div>
            </div>
            <div class="flex-1 overflow-y-auto p-2 custom-scrollbar">
                <div v-if="filteredCopyableKbs.length > 0" class="space-y-2 px-3 py-2">
                    <button v-for="kb in filteredCopyableKbs" :key="kb.id" @click="emit('updateSelectedKbForCopy', kb)"
                        :disabled="copyLoading"
                        :class="[
                            'w-full flex items-center p-3 rounded-xl transition-all text-left border-2 group relative',
                            selectedKbForCopy?.id === kb.id
                                ? 'bg-indigo-50/80 dark:bg-indigo-500/10 border-indigo-500 ring-1 ring-indigo-500/20'
                                : 'bg-white dark:bg-gray-800/40 border-transparent hover:border-slate-200 dark:hover:border-gray-700 hover:bg-slate-50 dark:hover:bg-gray-800'
                        ]">
                        <div
                            :class="[
                                'w-10 h-10 rounded-lg flex items-center justify-center transition-all group-hover:scale-110',
                                selectedKbForCopy?.id === kb.id
                                    ? 'bg-indigo-500 text-white'
                                    : 'bg-indigo-100 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400'
                            ]">
                            <Folder v-if="kb.type === 'FOLDER' || !kb.type" class="w-5 h-5" />
                            <FileText v-else class="w-5 h-5" />
                        </div>
                        <div class="ml-3 flex-1 min-w-0">
                            <div class="text-sm font-semibold text-slate-700 dark:text-gray-200 truncate">{{ kb.title }}</div>
                            <div class="text-xs text-slate-500 dark:text-gray-400 truncate mt-0.5">
                                {{ kb.description || t('editor.noDescription') }}
                            </div>
                        </div>
                        <div v-if="selectedKbForCopy?.id === kb.id"
                            class="absolute -right-1 -top-1 bg-indigo-500 text-white rounded-full p-0.5 border-2 border-white dark:border-[#1d2129]">
                            <svg class="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7" />
                            </svg>
                        </div>
                    </button>
                </div>
                <div v-else-if="copyLoading" class="p-8 flex flex-col items-center justify-center space-y-3">
                    <div class="w-8 h-8 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin"></div>
                    <span class="text-sm text-slate-500">{{ t('editor.copying') }}</span>
                </div>
                <div v-else class="p-12 text-center">
                    <div class="inline-flex p-3 rounded-full bg-slate-100 dark:bg-gray-800 mb-3 text-slate-400">
                        <Search class="w-6 h-6" />
                    </div>
                    <p class="text-sm text-slate-500">{{ t('editor.noAvailableKb') }}</p>
                </div>
            </div>
            <div
                class="p-4 bg-slate-50 dark:bg-gray-900/50 border-t border-slate-100 dark:border-gray-800 flex items-center justify-between">
                <button @click="emit('close')"
                    class="px-5 py-2 text-sm font-medium text-slate-700 dark:text-gray-300 rounded-xl hover:bg-slate-100 dark:hover:bg-gray-700 transition-all">
                    {{ t('nav.cancel') }}
                </button>
                <button @click="emit('confirm')" :disabled="!selectedKbForCopy || copyLoading"
                    class="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-semibold rounded-xl transition-all active:scale-95 flex items-center space-x-2">
                    <div v-if="copyLoading" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                    <Copy v-else :size="14" />
                    <span>{{ t('editor.confirmCopy') }}</span>
                </button>
            </div>
    </BaseDialog>
</template>
