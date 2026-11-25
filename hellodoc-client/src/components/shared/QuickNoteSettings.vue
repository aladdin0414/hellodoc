<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getKbList } from '../../api/kb'
import { getMe, updateQuickNotePreference } from '../../api/user'
import type { KnowledgeBase } from '../../types/kb'
import { message } from '../../utils/message'

const { t } = useI18n()
const kbs = ref<KnowledgeBase[]>([])
const selected = ref<number | null>(null)
const saved = ref<number | null>(null)
const loading = ref(true)

onMounted(async () => {
    try {
        const [user, list]: any[] = await Promise.all([getMe(), getKbList()])
        selected.value = saved.value = user.quickNoteKbId ?? null
        kbs.value = list.filter((kb: KnowledgeBase) => ['OWNER', 'ADMIN', 'EDITOR'].includes((kb.role || '').toUpperCase()))
    } finally {
        loading.value = false
    }
})

const update = async () => {
    loading.value = true
    try {
        await updateQuickNotePreference(selected.value)
        saved.value = selected.value
        message.success(t('mobile.quickNote.saved'))
    } catch {
        selected.value = saved.value
    } finally {
        loading.value = false
    }
}
</script>

<template>
    <div>
        <label for="quick-note-kb" class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1">{{ t('mobile.quickNote.setting') }}</label>
        <select id="quick-note-kb" v-model="selected" :disabled="loading" @change="update"
            class="w-full px-4 py-2 rounded-xl border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100">
            <option :value="null">{{ t('mobile.quickNote.none') }}</option>
            <option v-if="selected && !kbs.some(kb => kb.id === selected)" :value="selected" disabled>{{ t('mobile.quickNote.select') }}</option>
            <option v-for="kb in kbs" :key="kb.id" :value="kb.id">{{ kb.title }}</option>
        </select>
        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ t('mobile.quickNote.autoSave') }}</p>
    </div>
</template>
