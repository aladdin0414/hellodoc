<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { listMcpTokens, createMcpToken, revokeMcpToken, type McpTokenInfo } from '../../api/user'
import BaseDialog from '../shared/BaseDialog.vue'
import ConfirmDialog from '../ConfirmDialog.vue'
const show = defineModel<boolean>('show', { required: true })
const { t, locale } = useI18n()
const formatDate = (value: string) => {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium' }).format(date)
}
const mcpTokens = ref<McpTokenInfo[]>([])
const mcpName = ref('')
const newMcpToken = ref('')
const mcpBusy = ref(false)
const revokeId = ref<string | null>(null)
const loadMcpTokens = async () => { try { mcpTokens.value = await listMcpTokens() } catch { /* request layer reports errors */ } }
const createToken = async () => {
    if (!mcpName.value.trim() || mcpBusy.value) return
    mcpBusy.value = true
    try { const result = await createMcpToken(mcpName.value.trim()); newMcpToken.value = result.token; mcpName.value = ''; await loadMcpTokens() }
    catch { /* request layer reports errors */ } finally { mcpBusy.value = false }
}
const revokeToken = async () => {
    const id = revokeId.value
    if (!id) return
    revokeId.value = null
    try { await revokeMcpToken(id); await loadMcpTokens() } catch { /* request layer reports errors */ }
}
watch(show, (visible) => {
    newMcpToken.value = ''
    mcpName.value = ''
    if (visible) void loadMcpTokens()
})
</script>
<template>
    <BaseDialog :show="show" max-width-class="max-w-lg" @close="show = false">
        <div class="bg-white dark:bg-gray-800 px-6 py-6 max-h-[75vh] overflow-y-auto">
            <div class="flex justify-between items-center mb-6">
                <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('userProfile.mcpTitle') }}</h3>
                <button @click="show = false" :aria-label="t('nav.cancel')" class="text-gray-400 hover:text-gray-500 dark:hover:text-gray-300 transition">
                    <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" /></svg>
                </button>
            </div>
            <section class="space-y-5">
                <p class="text-sm leading-6 text-gray-500 dark:text-gray-400">{{ t('userProfile.mcpHint') }}</p>
                <div class="flex gap-2">
                    <input v-model="mcpName" maxlength="100" :placeholder="t('userProfile.mcpName')" :aria-label="t('userProfile.mcpName')" class="min-w-0 flex-1 px-3 py-2.5 rounded-xl border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500" />
                    <button :disabled="!mcpName.trim() || mcpBusy" @click="createToken" class="shrink-0 px-4 py-2 rounded-xl bg-blue-600 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50 disabled:hover:bg-blue-600 transition">{{ t('userProfile.mcpCreate') }}</button>
                </div>
                <div v-if="newMcpToken" class="rounded-xl border border-blue-200 dark:border-blue-800 bg-blue-50 dark:bg-blue-950/30 p-3 text-sm text-blue-900 dark:text-blue-200 space-y-2">
                    <p>{{ t('userProfile.mcpOnce') }}</p>
                    <input readonly :value="newMcpToken" @focus="($event.target as HTMLInputElement).select()" class="w-full px-3 py-2 border border-blue-200 dark:border-blue-800 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100" />
                </div>
                <div class="border-t border-gray-100 dark:border-gray-700 pt-5">
                    <h4 class="mb-3 text-sm font-semibold text-gray-700 dark:text-gray-200">{{ t('userProfile.mcpListTitle') }}</h4>
                    <div v-if="!mcpTokens.length" class="rounded-xl border border-dashed border-gray-200 dark:border-gray-600 px-4 py-6 text-center text-sm text-gray-500 dark:text-gray-400">{{ t('userProfile.mcpEmpty') }}</div>
                    <ul v-else class="space-y-2">
                        <li v-for="token in mcpTokens" :key="token.id" class="flex items-center gap-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50/70 dark:bg-gray-900/30 px-4 py-3">
                            <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-blue-100 text-blue-600 dark:bg-blue-900/50 dark:text-blue-300">
                                <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M15 7a4 4 0 11-7.46 2H3v3h3v3h3l2.54-2.54A4 4 0 0115 7z" /></svg>
                            </div>
                            <div class="min-w-0 flex-1">
                                <p class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">{{ token.name }}</p>
                                <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ t('userProfile.mcpCreatedAt', { date: formatDate(token.createdAt) }) }}</p>
                            </div>
                            <button @click="revokeId = token.id" class="shrink-0 rounded-lg px-3 py-2 text-sm font-medium text-gray-500 dark:text-gray-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/30 dark:hover:text-red-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 transition">{{ t('userProfile.mcpRevoke') }}</button>
                        </li>
                    </ul>
                </div>
            </section>
        </div>
        <ConfirmDialog :show="revokeId !== null" :title="t('userProfile.mcpRevoke')" :message="t('userProfile.mcpConfirm')" type="danger" @confirm="revokeToken" @cancel="revokeId = null" />
    </BaseDialog>
</template>
