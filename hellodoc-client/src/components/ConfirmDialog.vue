<script setup lang="ts">
import BaseDialog from './shared/BaseDialog.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
    show: boolean
    title: string
    message: string
    confirmText?: string
    cancelText?: string
    type?: 'danger' | 'info'
    hideCancel?: boolean
}>()

const emit = defineEmits(['confirm', 'cancel'])
</script>

<template>
    <Transition enter-from-class="opacity-0 scale-95" enter-to-class="opacity-100 scale-100"
        leave-from-class="opacity-100 scale-100" leave-to-class="opacity-0 scale-95">
        <BaseDialog :show="show" z-index-class="z-[500]" max-width-class="max-w-sm"
            panel-class="ring-1 ring-slate-900/5 dark:ring-gray-700" :close-on-overlay="false">
                <div class="p-6">
                    <div class="flex items-center space-x-3 mb-4">
                        <div v-if="type === 'danger'"
                            class="h-10 w-10 rounded-full bg-rose-50 flex items-center justify-center text-rose-600">
                            <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                            </svg>
                        </div>
                        <div v-else
                            class="h-10 w-10 rounded-full bg-blue-50 flex items-center justify-center text-blue-600">
                            <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                            </svg>
                        </div>
                        <h3 class="text-lg font-bold text-slate-800 dark:text-gray-100">{{ title }}</h3>
                    </div>

                    <p class="text-slate-600 dark:text-gray-300 leading-relaxed font-medium">{{ message }}</p>

                    <div class="mt-8 flex justify-end space-x-3">
                        <button v-if="!hideCancel" @click="emit('cancel')"
                            class="px-5 py-2.5 text-sm font-bold text-slate-500 dark:text-gray-400 hover:text-slate-700 dark:hover:text-gray-200 hover:bg-slate-50 dark:hover:bg-gray-700 rounded-xl transition-colors duration-200">
                            {{ cancelText || t('common.cancel') }}
                        </button>
                        <button @click="emit('confirm')" :class="[
                            'px-5 py-2 text-sm font-semibold text-white rounded-xl transition-all duration-200 active:scale-95',
                            type === 'danger' ? 'bg-rose-600 hover:bg-rose-700' : 'bg-indigo-600 hover:bg-indigo-700'
                        ]">
                            {{ confirmText || t('common.confirm') }}
                        </button>
                    </div>
                </div>
        </BaseDialog>
    </Transition>
</template>
