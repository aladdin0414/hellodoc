<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { searchUsers } from '../../api/user'
import { transferKbOwnership } from '../../api/kb'
import BaseDialog from '../shared/BaseDialog.vue'
import ConfirmDialog from '../ConfirmDialog.vue'

const props = defineProps<{ kbId: number; kbTitle: string; ownerId: number }>()
const show = defineModel<boolean>('show', { required: true })
const emit = defineEmits<{ (e: 'transferred'): void }>()
const { t } = useI18n()
type Candidate = { id: number; username: string; nickname: string; status: number }
const keyword = ref('')
const candidates = ref<Candidate[]>([])
const selected = ref<Candidate | null>(null)
const keepFormerOwner = ref(true)
const searching = ref(false)
const searched = ref(false)
const submitting = ref(false)
const confirm = ref(false)
let searchSequence = 0
watch(show, () => { searchSequence++; keyword.value = ''; candidates.value = []; selected.value = null; keepFormerOwner.value = true; confirm.value = false; searched.value = false })
const search = async () => {
    const sequence = ++searchSequence
    if (!keyword.value.trim()) { candidates.value = []; return }
    searching.value = true
    searched.value = false
    selected.value = null
    try {
        const response: any = await searchUsers(keyword.value.trim())
        if (sequence === searchSequence) {
            candidates.value = (Array.isArray(response) ? response : response?.content || []).filter((u: Candidate) => u.id !== props.ownerId && u.status === 0)
            searched.value = true
        }
    } finally { if (sequence === searchSequence) searching.value = false }
}
const transfer = async () => {
    if (!selected.value || submitting.value) return
    submitting.value = true
    try {
        await transferKbOwnership(props.kbId, { expectedOwnerId: props.ownerId, targetUserId: selected.value.id, keepFormerOwner: keepFormerOwner.value })
        confirm.value = false; show.value = false; emit('transferred')
    } finally { submitting.value = false }
}
</script>
<template>
    <BaseDialog :show="show" z-index-class="z-[90]" max-width-class="max-w-lg" @close="show = false">
        <div class="p-6 space-y-5 bg-white dark:bg-gray-800">
            <div class="flex justify-between items-start gap-4">
                <div><h3 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('kbTransfer.title') }}</h3><p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ kbTitle }}</p></div>
                <button @click="show = false" class="text-gray-500 text-xl" :aria-label="t('nav.cancel')">×</button>
            </div>
            <p class="text-sm text-gray-600 dark:text-gray-300">{{ t('kbTransfer.hint') }}</p>
            <div class="flex gap-2">
                <input v-model="keyword" @keyup.enter="search" :placeholder="t('kbMember.searchPlaceholder')" :aria-label="t('kbMember.searchPlaceholder')" class="min-w-0 flex-1 rounded-lg border border-gray-200 dark:border-gray-600 bg-transparent px-3 py-2 dark:text-white" />
                <button @click="search" :disabled="searching" class="rounded-lg bg-gray-100 dark:bg-gray-700 px-4 py-2 text-sm dark:text-white">{{ t('kbTransfer.search') }}</button>
            </div>
            <div v-if="candidates.length" class="max-h-40 overflow-auto space-y-1">
                <button v-for="candidate in candidates" :key="candidate.id" @click="selected = candidate" class="w-full rounded-lg px-3 py-2 text-left text-sm dark:text-white" :class="selected?.id === candidate.id ? 'bg-blue-50 dark:bg-blue-900/40 text-blue-700' : 'hover:bg-gray-50 dark:hover:bg-gray-700'">{{ candidate.nickname }} (@{{ candidate.username }})</button>
            </div>
            <p v-if="searched && !candidates.length" class="text-sm text-gray-500 dark:text-gray-400">{{ t('kbTransfer.noCandidates') }}</p>
            <p v-if="selected" class="text-sm font-medium text-blue-700 dark:text-blue-300">{{ t('kbTransfer.selected', { name: selected.nickname }) }}</p>
            <label class="flex items-start gap-2 text-sm text-gray-700 dark:text-gray-300"><input type="checkbox" v-model="keepFormerOwner" class="mt-1" />{{ t('kbTransfer.keep') }}</label>
            <p v-if="!keepFormerOwner" class="text-xs text-amber-700 dark:text-amber-400">{{ t('kbTransfer.removeHint') }}</p>
            <div class="flex justify-end gap-3"><button @click="show = false" class="px-4 py-2 text-sm text-gray-600 dark:text-gray-300">{{ t('nav.cancel') }}</button><button :disabled="!selected || submitting" @click="confirm = true" class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{{ t('kbTransfer.title') }}</button></div>
        </div>
        <ConfirmDialog :show="confirm" :title="t('kbTransfer.title')" :message="t('kbTransfer.confirm', { name: selected?.nickname || '', kb: kbTitle })" type="danger" @confirm="transfer" @cancel="confirm = false" />
    </BaseDialog>
</template>
