<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Folder, FileText, RotateCcw, Trash2, AlertTriangle } from 'lucide-vue-next'
import BaseDialog from '../../shared/BaseDialog.vue'
import { getTrashDocuments, restoreDocument, permanentlyDeleteDocument, clearTrash } from '../../../api/document'
import { message } from '../../../utils/message'

interface TrashItem {
    id: number
    name: string
    type: string
    updatedAt?: string
    createdAt?: string
}

const props = defineProps<{
    visible: boolean
    kbId: number
}>()

const emit = defineEmits<{
    close: []
    restored: []
}>()

const { t } = useI18n()
const loading = ref(false)
const trashItems = ref<TrashItem[]>([])
const actionDocId = ref<number | null>(null)
const actionType = ref<'restore' | 'delete' | 'clear' | null>(null)

const fetchTrash = async () => {
    if (!props.kbId) return
    loading.value = true
    try {
        const res: any = await getTrashDocuments(props.kbId)
        trashItems.value = res || []
    } catch (err) {
        console.error('Fetch trash items failed:', err)
        trashItems.value = []
    } finally {
        loading.value = false
    }
}

watch(() => props.visible, (val) => {
    if (val) {
        void fetchTrash()
    }
})

const handleRestore = async (doc: TrashItem) => {
    actionDocId.value = doc.id
    actionType.value = 'restore'
    try {
        await restoreDocument(props.kbId, doc.id)
        message.success(t('editor.restoreSuccess'))
        trashItems.value = trashItems.value.filter(item => item.id !== doc.id)
        emit('restored')
    } catch (err) {
        console.error('Restore failed:', err)
        message.error(t('editor.actionFailed', { action: t('editor.restore') }))
    } finally {
        actionDocId.value = null
        actionType.value = null
    }
}

const confirmState = ref<{
    show: boolean
    type: 'delete_one' | 'clear_all' | null
    item: TrashItem | null
    title: string
    message: string
}>({
    show: false,
    type: null,
    item: null,
    title: '',
    message: ''
})

const requestPermanentDelete = (doc: TrashItem) => {
    confirmState.value = {
        show: true,
        type: 'delete_one',
        item: doc,
        title: t('editor.permanentlyDelete'),
        message: t('editor.confirmPermanentDelete')
    }
}

const requestClearAll = () => {
    confirmState.value = {
        show: true,
        type: 'clear_all',
        item: null,
        title: t('editor.emptyTrash'),
        message: t('editor.confirmEmptyTrash')
    }
}

const handleConfirmAction = async () => {
    if (confirmState.value.type === 'delete_one' && confirmState.value.item) {
        const doc = confirmState.value.item
        confirmState.value.show = false
        actionDocId.value = doc.id
        actionType.value = 'delete'
        try {
            await permanentlyDeleteDocument(props.kbId, doc.id)
            message.success(t('editor.actionSuccess', { action: t('editor.permanentlyDelete') }))
            trashItems.value = trashItems.value.filter(item => item.id !== doc.id)
        } catch (err) {
            console.error('Permanent delete failed:', err)
            message.error(t('editor.actionFailed', { action: t('editor.permanentlyDelete') }))
        } finally {
            actionDocId.value = null
            actionType.value = null
        }
    } else if (confirmState.value.type === 'clear_all') {
        confirmState.value.show = false
        actionType.value = 'clear'
        try {
            await clearTrash(props.kbId)
            message.success(t('editor.actionSuccess', { action: t('editor.emptyTrash') }))
            trashItems.value = []
        } catch (err) {
            console.error('Clear trash failed:', err)
            message.error(t('editor.actionFailed', { action: t('editor.emptyTrash') }))
        } finally {
            actionType.value = null
        }
    }
}

const formatDate = (dateStr?: string) => {
    if (!dateStr) return ''
    try {
        const d = new Date(dateStr)
        return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    } catch {
        return dateStr
    }
}
</script>

<template>
    <BaseDialog :show="visible" z-index-class="z-[200]" max-width-class="max-w-2xl"
        panel-class="ring-1 ring-slate-900/5 dark:ring-gray-700" @close="emit('close')">
        <div class="bg-white dark:bg-[#161b22] px-6 pt-5 pb-4 border-b border-slate-100 dark:border-gray-800 flex items-center justify-between">
            <div class="flex items-center space-x-2">
                <Trash2 class="h-5 w-5 text-rose-500" />
                <h3 class="text-lg font-bold text-slate-900 dark:text-gray-100">{{ t('editor.trashTitle') }}</h3>
            </div>
            <button v-if="trashItems.length > 0" @click="requestClearAll" :disabled="actionType === 'clear'"
                class="text-xs text-rose-600 hover:text-rose-700 dark:text-rose-400 font-medium hover:underline flex items-center space-x-1 disabled:opacity-50">
                <AlertTriangle :size="12" />
                <span>{{ t('editor.emptyTrash') }}</span>
            </button>
        </div>

        <div class="p-6 max-h-[420px] overflow-y-auto scrollbar-subtle bg-white dark:bg-[#161b22]">
            <div v-if="loading" class="flex justify-center py-12">
                <div class="animate-spin rounded-full h-6 w-6 border-2 border-indigo-600 border-t-transparent"></div>
            </div>
            <div v-else-if="trashItems.length === 0" class="py-16 text-center text-slate-400 dark:text-gray-500 text-sm space-y-2">
                <Trash2 class="h-10 w-10 mx-auto text-slate-300 dark:text-slate-600 stroke-1" />
                <p>{{ t('editor.trashEmpty') }}</p>
            </div>
            <div v-else class="space-y-2">
                <div v-for="item in trashItems" :key="item.id"
                    class="flex items-center justify-between p-3 rounded-xl bg-slate-50 dark:bg-slate-800/40 border border-slate-200/60 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 transition-all">
                    <div class="flex items-center space-x-3 min-w-0 pr-4">
                        <template v-if="item.type === 'folder'">
                            <Folder class="h-5 w-5 text-blue-500 dark:text-blue-400 shrink-0" />
                        </template>
                        <template v-else>
                            <FileText class="h-5 w-5 text-slate-400 shrink-0" />
                        </template>
                        <div class="min-w-0">
                            <p class="text-sm font-medium text-slate-800 dark:text-slate-200 truncate">{{ item.name }}</p>
                            <p class="text-[11px] text-slate-400 dark:text-slate-500">{{ formatDate(item.updatedAt || item.createdAt) }}</p>
                        </div>
                    </div>
                    <div class="flex items-center space-x-2 shrink-0">
                        <button @click="handleRestore(item)" :disabled="actionDocId === item.id"
                            class="px-3 py-1.5 text-xs font-semibold rounded-lg bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 hover:bg-indigo-100 dark:hover:bg-indigo-900/50 transition-colors flex items-center space-x-1 disabled:opacity-50">
                            <RotateCcw :size="13" />
                            <span>{{ t('editor.restore') }}</span>
                        </button>
                        <button @click="requestPermanentDelete(item)" :disabled="actionDocId === item.id"
                            class="p-1.5 text-xs font-semibold rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-900/20 transition-colors disabled:opacity-50"
                            :title="t('editor.permanentlyDelete')">
                            <Trash2 :size="14" />
                        </button>
                    </div>
                </div>
            </div>
        </div>

        <div class="bg-slate-50 dark:bg-[#161b22] px-6 py-3 border-t border-slate-100 dark:border-gray-800 flex justify-end">
            <button type="button" @click="emit('close')"
                class="px-4 py-2 text-sm font-medium text-slate-700 dark:text-gray-300 bg-white dark:bg-gray-800 border border-slate-300 dark:border-gray-600 rounded-xl hover:bg-slate-50 dark:hover:bg-gray-700 transition-colors">
                {{ t('nav.cancel') }}
            </button>
        </div>
    </BaseDialog>

    <!-- 自定义精致二次确认弹窗（替代浏览器原生 window.confirm） -->
    <BaseDialog :show="confirmState.show" z-index-class="z-[220]" max-width-class="max-w-md"
        panel-class="ring-1 ring-slate-900/5 dark:ring-gray-700" @close="confirmState.show = false">
        <div class="bg-white dark:bg-[#161b22] px-4 pt-5 pb-4 sm:p-6 sm:pb-4">
            <div class="sm:flex sm:items-start">
                <div class="mx-auto flex-shrink-0 flex items-center justify-center h-12 w-12 rounded-full bg-rose-100 dark:bg-rose-950/50 sm:mx-0 sm:h-10 sm:w-10">
                    <AlertTriangle class="h-6 w-6 text-rose-600 dark:text-rose-400" />
                </div>
                <div class="mt-3 text-center sm:mt-0 sm:ml-4 sm:text-left">
                    <h3 class="text-lg leading-6 font-bold text-slate-900 dark:text-gray-100">{{ confirmState.title }}</h3>
                    <div class="mt-2">
                        <p class="text-sm text-slate-500 dark:text-gray-400">{{ confirmState.message }}</p>
                    </div>
                </div>
            </div>
        </div>
        <div class="bg-slate-50 dark:bg-[#161b22] px-4 py-3 sm:px-6 sm:flex sm:flex-row-reverse space-x-reverse space-x-3 border-t border-slate-100 dark:border-gray-800">
            <button type="button" @click="handleConfirmAction"
                class="w-full inline-flex justify-center rounded-xl border border-transparent px-5 py-2 text-sm font-semibold bg-rose-600 text-white hover:bg-rose-700 focus:outline-none sm:ml-3 sm:w-auto transition-all active:scale-95">
                {{ t('editor.confirm') }}
            </button>
            <button type="button" @click="confirmState.show = false"
                class="mt-3 w-full inline-flex justify-center rounded-xl border border-slate-300 dark:border-gray-600 px-5 py-2 text-sm font-medium bg-white dark:bg-[#161b22] text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 focus:outline-none sm:mt-0 sm:w-auto transition-all">
                {{ t('nav.cancel') }}
            </button>
        </div>
    </BaseDialog>
</template>
