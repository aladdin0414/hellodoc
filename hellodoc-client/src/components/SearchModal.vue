<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { searchAll } from '../api/kb'
import SearchResultList from './SearchResultList.vue'
import type { SearchResult } from './SearchResultList.vue'
import { useI18n } from 'vue-i18n'
import BaseDialog from './shared/BaseDialog.vue'

const props = defineProps<{
    show: boolean
    initialKbResults?: any[]
}>()

const emit = defineEmits<{
    (e: 'update:show', value: boolean): void
    (e: 'select-doc', docId: number, kbId: number): void
    (e: 'select-kb', kbId: number): void
}>()

const searchQuery = ref('')
const searchType = ref<'doc' | 'kb'>('doc')
const results = ref<SearchResult[]>([])
const loading = ref(false)
const inputRef = ref<HTMLInputElement | null>(null)
let debounceTimer: ReturnType<typeof setTimeout> | null = null
const { t } = useI18n()

const close = () => {
    emit('update:show', false)
}

const handleSearch = async () => {
    const q = searchQuery.value.trim()
    if (!q) {
        results.value = []
        return
    }

    if (searchType.value === 'kb') {
        // 知识库过滤逻辑通常在父组件处理，或者这里也可以根据 props 进行本地过滤
        return
    }

    loading.value = true
    try {
        const res: any = await searchAll(q)
        results.value = res
    } catch (err) {
        console.error('Global search failed:', err)
    } finally {
        loading.value = false
    }
}

const updateFilteredKbs = () => {
    if (searchType.value !== 'kb' || !searchQuery.value.trim()) {
        filteredKbs.value = props.initialKbResults || []
        return
    }
    const q = searchQuery.value.toLowerCase()
    filteredKbs.value = (props.initialKbResults || []).filter(kb =>
        kb.title.toLowerCase().includes(q) ||
        (kb.description && kb.description.toLowerCase().includes(q))
    )
}

watch(searchQuery, () => {
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
        handleSearch()
    }, 300)
})

watch(searchType, () => {
    const q = searchQuery.value.trim()
    if (!q) {
        updateFilteredKbs()
        return
    }
    if (searchType.value === 'doc') {
        handleSearch()
    } else {
        updateFilteredKbs()
    }
})

watch(() => props.show, async () => {
    if (props.show) {
        searchQuery.value = ''
        results.value = []
        await nextTick()
        inputRef.value?.focus()
    }
})

const onSelectDoc = (docId: number) => {
    const doc = results.value.find(r => r.docId === docId)
    if (doc) {
        emit('select-doc', docId, doc.kbId)
    }
    close()
}

const onSelectKb = (kb: any) => {
    emit('select-kb', kb.id)
    close()
}

// 快捷键支持
const handleKeyDown = (e: KeyboardEvent) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault()
        emit('update:show', true)
    }
    if (e.key === 'Escape' && props.show) {
        close()
    }
}

onMounted(() => {
    window.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
    window.removeEventListener('keydown', handleKeyDown)
})

// 本地过滤知识库逻辑
const filteredKbs = ref<any[]>([])
watch([searchQuery, () => props.initialKbResults, searchType], () => {
    updateFilteredKbs()
}, { immediate: true })

</script>

<template>
    <BaseDialog :show="show" max-width-class="max-w-2xl" panel-class="flex flex-col max-h-[75vh] animate-in fade-in zoom-in-95 duration-200"
        @close="close">
            <!-- Search Header -->
            <div class="relative p-5 flex items-center gap-4 bg-white/50 dark:bg-gray-800/50 backdrop-blur-md border-b border-gray-100 dark:border-gray-700">
                <div class="shrink-0 w-10 h-10 rounded-xl bg-blue-50 dark:bg-blue-900/20 flex items-center justify-center text-blue-600 dark:text-blue-400">
                    <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                    </svg>
                </div>
                <input
                    ref="inputRef"
                    v-model="searchQuery"
                    type="text"
                    :placeholder="t('search.placeholder')"
                    class="flex-1 bg-transparent border-none focus:outline-none focus:ring-0 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 text-xl font-medium"
                />
                <div class="flex items-center gap-2">
                    <kbd class="hidden sm:inline-flex items-center justify-center h-6 px-1.5 border border-gray-200 dark:border-gray-600 rounded-md text-[10px] font-sans font-bold text-gray-400 bg-white dark:bg-gray-700 shadow-sm uppercase">ESC</kbd>
                </div>
            </div>

            <!-- Enhanced Tabs -->
            <div class="px-5 pt-2 flex items-center gap-1 border-b border-gray-100 dark:border-gray-700/50">
                <button 
                    @click="searchType = 'doc'"
                    :class="searchType === 'doc' ? 'text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/20 shadow-sm' : 'text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700/50'"
                    class="px-4 py-2 text-xs font-bold rounded-t-xl transition-all duration-200"
                >
                    {{ t('search.tabDocs') }}
                </button>
                <button 
                    @click="searchType = 'kb'"
                    :class="searchType === 'kb' ? 'text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/20 shadow-sm' : 'text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700/50'"
                    class="px-4 py-2 text-xs font-bold rounded-t-xl transition-all duration-200"
                >
                    {{ t('search.tabKbs') }}
                </button>
            </div>

            <!-- Results Area -->
            <div class="flex-1 overflow-y-auto min-h-[300px] bg-gray-50/30 dark:bg-gray-900/10">
                <template v-if="searchType === 'doc'">
                    <SearchResultList 
                        :results="results" 
                        :loading="loading" 
                        :query="searchQuery"
                        @select="onSelectDoc"
                    />
                </template>

                <template v-else>
                    <div v-if="filteredKbs.length > 0" class="p-4 space-y-2">
                        <div 
                            v-for="kb in filteredKbs" 
                            :key="kb.id" 
                            @click="onSelectKb(kb)"
                            class="flex items-center gap-4 p-4 rounded-2xl cursor-pointer bg-white dark:bg-gray-800 hover:bg-white dark:hover:bg-gray-700 shadow-sm hover:shadow-xl hover:shadow-blue-500/5 border border-gray-100 dark:border-gray-700/50 hover:border-blue-100 dark:hover:border-blue-900/30 group transition-all duration-200"
                        >
                            <div class="w-12 h-12 rounded-[14px] flex items-center justify-center text-white shrink-0 shadow-lg" :style="{ backgroundColor: kb.color || '#3b82f6' }">
                                <svg class="w-7 h-7" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                                    <path stroke-linecap="round" stroke-linejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
                                </svg>
                            </div>
                            <div class="flex-1 min-w-0">
                                <h4 class="text-sm font-bold text-gray-900 dark:text-gray-100 truncate group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">{{ kb.title }}</h4>
                                <p class="text-xs text-gray-500 dark:text-gray-400 truncate mt-1 leading-relaxed opacity-70">{{ kb.description || t('search.kbNoDescription') }}</p>
                            </div>
                            <div class="shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
                                <svg class="w-5 h-5 text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
                                    <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
                                </svg>
                            </div>
                        </div>
                    </div>
                    <div v-else-if="searchQuery" class="p-12 text-center text-gray-400 dark:text-gray-500 text-sm">
                        <div class="text-3xl mb-2 opacity-20">📭</div>
                        {{ t('search.kbNoMatch') }}
                    </div>
                </template>
            </div>

    </BaseDialog>
</template>

<style scoped>
.dark .dark\:border-gray-750 {
    border-color: rgba(55, 65, 81, 0.5);
}
</style>
