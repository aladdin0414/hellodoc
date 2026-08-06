<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import BaseDialog from '../../shared/BaseDialog.vue'

defineProps<{
    visible: boolean
    createType: 'file' | 'folder'
    newName: string
    creating: boolean
    setCreateInputRef: (el: any) => void
}>()

const emit = defineEmits<{
    close: []
    updateName: [value: string]
    confirm: []
}>()

const { t } = useI18n()
</script>

<template>
    <BaseDialog :show="visible" max-width-class="max-w-sm" panel-class="animate-in fade-in zoom-in duration-200 ring-1 ring-slate-900/5 dark:ring-gray-700"
        @close="emit('close')">
            <div class="p-6">
                <h3 class="text-lg font-bold text-slate-900 dark:text-gray-100 mb-6">
                    {{ createType === 'folder' ? t('editor.newFolder') : t('editor.newDoc') }}
                </h3>
                <div>
                    <label class="block text-xs font-bold text-slate-500 dark:text-gray-400 uppercase tracking-wider mb-2">{{ t('editor.name') }}</label>
                    <input :ref="setCreateInputRef" :value="newName" type="text"
                        @input="emit('updateName', ($event.target as HTMLInputElement).value)"
                        class="w-full px-4 py-2.5 bg-slate-50 dark:bg-gray-700 border border-slate-200 dark:border-gray-600 rounded-xl focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 outline-none transition-all text-sm font-medium text-slate-900 dark:text-gray-100"
                        :placeholder="createType === 'folder' ? t('editor.inputFolderName') : t('editor.inputDocName')" @keyup.enter="emit('confirm')" />
                </div>
            </div>
            <div
                class="px-6 py-4 bg-slate-50 dark:bg-[#161b22] flex justify-end space-x-3 border-t border-slate-100 dark:border-gray-700">
                <button @click="emit('close')" class="px-5 py-2 text-sm font-medium text-slate-700 dark:text-gray-300 rounded-xl hover:bg-slate-100 dark:hover:bg-gray-700 transition-all">
                    {{ t('nav.cancel') }}
                </button>
                <button @click="emit('confirm')" :disabled="creating"
                    class="px-5 py-2 text-sm font-semibold bg-indigo-600 text-white rounded-xl hover:bg-indigo-700 transition-all active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed">
                    {{ creating ? t('editor.saving') : t('editor.confirm') }}
                </button>
            </div>
    </BaseDialog>
</template>
