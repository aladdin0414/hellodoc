<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import Switch from '../Switch.vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const router = useRouter()

interface DocLike {
    id: number
    name: string
    type: string
    status?: string
}

interface ActiveEditor {
    sessionId: string | number
    userId: string | number
    username?: string
    userColor?: string
}

const props = defineProps<{
    currentDoc: DocLike
    kbId: number
    isMobile: boolean
    isSidebarVisible: boolean
    hasUnsavedChanges: boolean
    saving: boolean
    isReadOnlyByCollab: boolean
    isCollabFeatureEnabled: boolean
    collabState: string
    collabStateLabel: string
    collabStateDotClass: string
    activeEditors: ActiveEditor[]
    isLockedByMe: boolean
    docLock: any
    lockOwnerLabel: string
    aiGenerating: boolean
}>()

const emit = defineEmits<{
    toggleSidebar: []
    save: []
    openRevisions: []
    openAiAssistant: []
    toggleStatus: []
    reconnectCollab: []
    releaseEditLock: []
    requestEditLock: []
}>()

const { t } = useI18n()
const showMoreMenu = ref(false)

const handleOutsideClick = (_e: MouseEvent) => {
    if (showMoreMenu.value) {
        showMoreMenu.value = false
    }
}

onMounted(() => {
    window.addEventListener('click', handleOutsideClick)
})

onUnmounted(() => {
    window.removeEventListener('click', handleOutsideClick)
})
</script>

<template>
    <div
        class="h-16 border-b border-slate-200 dark:border-slate-800 px-4 flex items-center justify-between flex-shrink-0 bg-white dark:bg-[#1e1e1e] z-20">
        <div class="flex-1 min-w-0 mr-4 flex items-center">
            <button @click="emit('toggleSidebar')"
                class="p-1.5 mr-1 rounded-xl text-slate-500 hover:text-indigo-600 hover:bg-slate-50 dark:hover:bg-gray-700 transition-all border border-transparent hover:border-slate-200 dark:hover:border-gray-600"
                :title="isSidebarVisible ? t('editor.collapseCatalog') : t('editor.expandCatalog')">
                <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                    stroke-linecap="round" stroke-linejoin="round">
                    <rect width="18" height="18" x="3" y="3" rx="2" ry="2"></rect>
                    <path d="M9 3v18"></path>
                </svg>
            </button>
            <div v-if="hasUnsavedChanges" class="w-2 h-2 rounded-full bg-amber-500 mr-3 flex-shrink-0 ring-4 ring-amber-100"
                :title="t('editor.unsaved')"></div>
            <input v-model="currentDoc.name" type="text" :disabled="isReadOnlyByCollab"
                class="text-xl font-black text-slate-900 dark:text-gray-100 border-none focus:ring-0 focus:outline-none w-full bg-transparent p-0 placeholder-slate-300 dark:placeholder-gray-600 tracking-tight"
                :placeholder="t('editor.inputTitle')" @blur="emit('save')" />
        </div>
        <div class="flex items-center space-x-3">
            <div v-if="currentDoc.type === 'file' && isCollabFeatureEnabled" class="flex items-center space-x-2">
                <div v-if="!isMobile"
                    class="flex items-center space-x-2 px-2 py-1 rounded-xl border border-slate-200/60 dark:border-gray-600 bg-white/60 dark:bg-[#1e1e1e]/60">
                    <span class="w-2 h-2 rounded-full" :class="collabStateDotClass"></span>
                    <span class="text-xs font-bold text-slate-600 dark:text-gray-300">{{ collabStateLabel }}</span>
                </div>
                <div class="flex -space-x-2">
                    <div v-for="u in activeEditors.slice(0, 3)" :key="u.sessionId"
                        class="h-7 w-7 rounded-full border-2 border-white dark:border-[#1e293b] shadow-sm flex items-center justify-center text-[11px] font-black text-white"
                        :style="{ backgroundColor: u.userColor || '#4f46e5' }" :title="u.username || `${t('editor.user')}${u.userId}`">
                        {{ (u.username || `U${u.userId}`).slice(0, 1).toUpperCase() }}
                    </div>
                    <div v-if="activeEditors.length > 3"
                        class="h-7 w-7 rounded-full border-2 border-white dark:border-[#1e293b] shadow-sm flex items-center justify-center text-[11px] font-black bg-slate-200 dark:bg-gray-700 text-slate-700 dark:text-gray-200">
                        +{{ activeEditors.length - 3 }}
                    </div>
                </div>
            </div>

            <template v-if="!isMobile">
                <button v-if="currentDoc.type === 'file'" @click="emit('openAiAssistant')"
                    class="text-slate-500 dark:text-gray-400 hover:text-indigo-600 p-2 rounded-xl hover:bg-slate-50 dark:hover:bg-gray-700 border border-slate-200/60 dark:border-gray-600 transition-all flex items-center space-x-1.5 hover:-translate-y-0.5 active:scale-95"
                    :title="t('editor.ai.title')">
                    <svg v-if="aiGenerating" class="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                    </svg>
                    <svg v-else xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-sparkles"><path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"/><path d="M20 3v4"/><path d="M22 5h-4"/><path d="M4 17v2"/><path d="M5 18H3"/></svg>
                    <span class="text-xs font-bold">{{ t('editor.ai.title') }}</span>
                </button>

                <button v-if="currentDoc.type === 'file'" @click="emit('openRevisions')"
                    class="text-slate-500 dark:text-gray-400 hover:text-indigo-600 p-2 rounded-xl hover:bg-slate-50 dark:hover:bg-gray-700 border border-slate-200/60 dark:border-gray-600 transition-all flex items-center space-x-1.5 hover:-translate-y-0.5 active:scale-95"
                    :title="t('editor.revisions')">
                    <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                            d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    <span class="text-xs font-bold">{{ t('editor.revisions') }}</span>
                </button>

                <router-link v-if="currentDoc.type === 'file'" :to="{
                    name: 'PublicView',
                    params: { kbId: kbId, docId: currentDoc.id }
                }" target="_blank"
                    class="text-slate-500 dark:text-gray-400 hover:text-indigo-600 p-2 rounded-xl hover:bg-slate-50 dark:hover:bg-gray-700 border border-slate-200/60 dark:border-gray-600 transition-all flex items-center space-x-1.5 hover:-translate-y-0.5 active:scale-95"
                    :title="t('editor.browseKb')">
                    <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                            d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                            d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                    </svg>
                    <span class="text-xs font-bold">{{ t('editor.browse') }}</span>
                </router-link>

                <div class="w-px h-5 bg-slate-200 dark:bg-gray-700 mx-1"></div>
                <Switch v-if="currentDoc.type === 'file'" :model-value="currentDoc.status === 'published'"
                    :label="t('editor.publish')" @change="emit('toggleStatus')" />
            </template>

            <div v-else class="relative">
                <button @click.stop="showMoreMenu = !showMoreMenu"
                    class="p-2 rounded-xl text-slate-500 hover:text-indigo-600 hover:bg-slate-50 dark:hover:bg-gray-700 transition-all border border-slate-200 dark:border-gray-600 active:scale-95">
                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-ellipsis-vertical"><circle cx="12" cy="12" r="1"/><circle cx="12" cy="5" r="1"/><circle cx="12" cy="19" r="1"/></svg>
                </button>

                <!-- Mobile More Menu -->
                <div v-if="showMoreMenu" @click.stop
                    class="absolute top-full right-0 mt-2 w-48 bg-white dark:bg-[#161b22] border border-slate-200/60 dark:border-gray-700 rounded-2xl shadow-xl z-[100] py-1.5 overflow-hidden animate-in fade-in slide-in-from-top-2 duration-200">
                    <button v-if="currentDoc.type === 'file'" @click="emit('openAiAssistant'); showMoreMenu = false"
                        class="w-full flex items-center space-x-2.5 px-4 py-2 text-sm font-medium text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 hover:text-indigo-600 transition-colors">
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-sparkles"><path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"/><path d="M20 3v4"/><path d="M22 5h-4"/><path d="M4 17v2"/><path d="M5 18H3"/></svg>
                        <span>{{ t('editor.ai.title') }}</span>
                    </button>
                    <button v-if="currentDoc.type === 'file'" @click="emit('openRevisions'); showMoreMenu = false"
                        class="w-full flex items-center space-x-2.5 px-4 py-2 text-sm font-medium text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 hover:text-indigo-600 transition-colors">
                        <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                        <span>{{ t('editor.revisions') }}</span>
                    </button>
                    <button v-if="currentDoc.type === 'file'" @click="router.push({
                        name: 'PublicView',
                        params: { kbId: kbId, docId: currentDoc.id }
                    }); showMoreMenu = false"
                        class="w-full flex items-center space-x-2.5 px-4 py-2 text-sm font-medium text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 hover:text-indigo-600 transition-colors">
                        <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                        </svg>
                        <span>{{ t('editor.browseKb') }}</span>
                    </button>
                    <div class="h-px bg-slate-100 dark:bg-gray-700 my-1"></div>
                    <div class="px-4 py-2 flex items-center justify-between">
                        <span class="text-sm font-medium text-slate-700 dark:text-gray-300">{{ t('editor.publish') }}</span>
                        <Switch v-if="currentDoc.type === 'file'" :model-value="currentDoc.status === 'published'"
                            @change="emit('toggleStatus')" />
                    </div>
                </div>
            </div>
        </div>
            <button @click="emit('save')" :disabled="saving" v-show="false"
                class="px-5 py-2 bg-indigo-600 text-white rounded-xl text-sm font-bold hover:bg-indigo-700 transition-all disabled:opacity-50 hover:-translate-y-0.5 active:scale-95">
                {{ saving ? t('editor.saving') : t('editor.save') }}
            </button>
        </div>

    <div v-if="currentDoc.type === 'file' && isCollabFeatureEnabled"
        class="px-4 py-2 border-b border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-[#161b22]/20 flex items-center justify-between">
        <div class="flex items-center space-x-2 min-w-0">
            <span v-if="collabState !== 'connected'" class="text-sm font-bold text-slate-700 dark:text-gray-200 truncate">
                {{ t('editor.collabReadOnly', { state: collabStateLabel }) }}
            </span>
            <span v-else-if="isLockedByMe" class="text-sm font-bold text-emerald-700 dark:text-emerald-300 truncate">
                {{ t('editor.editingByMe') }}
            </span>
            <span v-else-if="docLock" class="text-sm font-bold text-amber-700 dark:text-amber-300 truncate">
                {{ t('editor.editingByOther', { user: lockOwnerLabel }) }}
            </span>
            <span v-else class="text-sm font-bold text-slate-700 dark:text-gray-200 truncate">
                {{ t('editor.noOneEditing') }}
            </span>
        </div>
        <div class="flex items-center space-x-2">
            <button v-if="collabState !== 'connected'" @click="emit('reconnectCollab')"
                class="px-3 py-1.5 text-xs font-bold rounded-xl border border-slate-200 dark:border-gray-700 bg-white dark:bg-[#1e1e1e] text-slate-600 dark:text-gray-300 hover:text-indigo-600 hover:border-indigo-200 dark:hover:border-indigo-700 transition-all">
                {{ t('editor.reconnect') }}
            </button>
            <button v-else-if="isLockedByMe" @click="emit('releaseEditLock')"
                class="px-3 py-1.5 text-xs font-bold rounded-xl border border-emerald-200 dark:border-emerald-800 bg-white dark:bg-[#1e1e1e] text-emerald-700 dark:text-emerald-300 hover:bg-emerald-50 dark:hover:bg-emerald-900/20 transition-all">
                {{ t('editor.releaseEdit') }}
            </button>
            <button v-else @click="emit('requestEditLock')"
                class="px-3 py-1.5 text-xs font-bold rounded-xl border border-indigo-200 dark:border-indigo-800 bg-indigo-600 text-white hover:bg-indigo-700 transition-all">
                {{ t('editor.requestEdit') }}
            </button>
        </div>
    </div>
</template>
