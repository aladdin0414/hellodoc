<script setup lang="ts">
import { ref, watch } from 'vue'
import { aiCompletionStream } from '../../api/ai'
import { message } from '../../utils/message'
import { useI18n } from 'vue-i18n'
import BaseDialog from '../shared/BaseDialog.vue'

const props = defineProps<{
    visible: boolean
    x: number
    y: number
    selectedText: string
    isFullTextMode?: boolean
}>()

const emit = defineEmits<{
    (e: 'update:visible', val: boolean): void
    (e: 'stream-start', model: string): void
    (e: 'stream-chunk', chunk: string): void
    (e: 'stream-end'): void
}>()

const { t } = useI18n()

const prompt = ref('')
const loading = ref(false)

const handleClose = () => {
    emit('update:visible', false)
    prompt.value = ''
    loading.value = false
}

const handleSubmit = async () => {
    const promptText = prompt.value.trim()
    if (!promptText && !props.selectedText?.trim()) return
    handleClose()
    loading.value = true
    try {
        let streamStarted = false
        let model = 'ai'
        const ensureStreamStarted = () => {
            if (!streamStarted) {
                streamStarted = true
                emit('stream-start', model)
            }
        }

        await aiCompletionStream({
            context: props.selectedText,
            prompt: promptText || props.selectedText
        }, {
            onModel: (m: string) => {
                if (m) model = m
                ensureStreamStarted()
            },
            onChunk: (chunk: string) => {
                ensureStreamStarted()
                emit('stream-chunk', chunk)
            },
            onDone: () => {
                ensureStreamStarted()
                emit('stream-end')
            }
        })
    } catch (e: any) {
        // 流式请求使用 fetch，不走 request.ts 拦截器，这里统一兜底提示。
        message.error(e?.message || t('editor.ai.requestFailed'))
        emit('stream-end')
    } finally {
        loading.value = false
    }
}

watch(() => props.visible, (newVal) => {
    if (newVal) {
        prompt.value = ''
        loading.value = false
        return
    }
    loading.value = false
})
</script>

<template>
    <BaseDialog :show="visible" @close="handleClose" max-width-class="max-w-[500px]"
        panel-class="animate-in fade-in zoom-in duration-200 ring-1 ring-slate-900/5 dark:ring-gray-700">
        <div class="p-6">
            <h3 class="text-lg font-bold text-slate-900 dark:text-gray-100 mb-4 flex items-center gap-2">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none"
                    stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                    class="lucide lucide-sparkles text-indigo-500">
                    <path
                        d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z" />
                    <path d="M20 3v4" />
                    <path d="M22 5h-4" />
                    <path d="M4 17v2" />
                    <path d="M5 18H3" />
                </svg>
                {{ t('editor.ai.title') }}
            </h3>

            <div class="space-y-3">
                <div v-if="!isFullTextMode"
                    class="text-xs text-slate-500 dark:text-slate-400 bg-slate-50 dark:bg-slate-800/50 p-3 rounded-xl max-h-24 overflow-y-auto">
                    "{{ selectedText }}"
                </div>
                <div v-else
                    class="text-xs font-bold text-indigo-500 bg-indigo-50 dark:bg-indigo-900/30 p-3 rounded-xl flex items-center justify-center">
                    {{ t('editor.ai.fullTextRef') }}
                </div>

                <textarea v-model="prompt" :placeholder="t('editor.ai.placeholder')"
                    class="w-full text-sm bg-slate-50 dark:bg-gray-700 border border-slate-200 dark:border-gray-600 rounded-xl px-4 py-2.5 text-slate-800 dark:text-slate-200 focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 resize-none h-24"
                    @keydown.enter.prevent="handleSubmit" :disabled="loading"></textarea>

            </div>
        </div>
        <div
            class="px-6 py-4 bg-slate-50 dark:bg-[#161b22] flex justify-end space-x-3 border-t border-slate-100 dark:border-gray-700">
            <button @click="handleClose"
                class="px-5 py-2 text-sm font-medium text-slate-700 dark:text-gray-300 rounded-xl hover:bg-slate-100 dark:hover:bg-gray-700 transition-all">
                {{ t('editor.ai.cancel') }}
            </button>
            <button @click="handleSubmit" :disabled="loading"
                class="flex items-center gap-1 px-5 py-2 text-sm font-semibold bg-indigo-600 text-white rounded-xl hover:bg-indigo-700 transition-all active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed">
                <svg v-if="loading" class="animate-spin h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none"
                    viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor"
                        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <span>{{ loading ? t('editor.ai.processing') : t('editor.ai.send') }}</span>
            </button>
        </div>
    </BaseDialog>
</template>
