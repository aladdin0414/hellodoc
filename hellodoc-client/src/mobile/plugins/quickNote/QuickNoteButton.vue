<template>
  <button
    v-if="isTargetUser(username)"
    @click="handleClick"
    :disabled="creatingQuickNote"
    :title="t('mobile.quickNote.title')"
    class="p-1.5 rounded-xl text-slate-600 dark:text-slate-300 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-slate-100 dark:hover:bg-slate-800 active:scale-95 transition-all disabled:opacity-50 flex items-center justify-center"
  >
    <Zap class="w-5 h-5 stroke-[1.8] scale-x-[0.8]" />
  </button>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Zap } from 'lucide-vue-next'
import { useQuickNote } from './useQuickNote'

const props = defineProps<{
  username?: string
}>()

const { t } = useI18n()
const router = useRouter()
const { isTargetUser, creatingQuickNote, handleCreateQuickNote } = useQuickNote()

const handleClick = () => {
  handleCreateQuickNote({
    router,
    username: props.username
  })
}
</script>
