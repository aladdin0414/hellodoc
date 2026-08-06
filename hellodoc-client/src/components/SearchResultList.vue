<script setup lang="ts">
import { useI18n } from 'vue-i18n'
// 搜索结果列表组件
// 展示全文搜索结果，每项包含文档标题和关键词上下文摘要

export interface SearchResult {
    docId: number
    docName: string
    kbId: number
    kbTitle: string
    snippet: string
    score: number
    highlightedTitle: string
    highlightedSnippet: string
}

defineProps<{
    results: SearchResult[]
    loading: boolean
    query: string
    activeDocId?: number
}>()

const emit = defineEmits<{
    (e: 'select', docId: number): void
}>()

const { t } = useI18n()
</script>

<template>
    <div class="search-result-list">
        <!-- 加载状态 -->
        <div v-if="loading" class="flex justify-center py-12">
            <div class="relative w-8 h-8">
                <div class="absolute inset-0 rounded-full border-2 border-blue-100 dark:border-blue-900/30"></div>
                <div class="absolute inset-0 rounded-full border-2 border-blue-600 border-t-transparent animate-spin"></div>
            </div>
        </div>

        <!-- 无结果提示 -->
        <div v-else-if="results.length === 0 && query" class="px-6 py-12 text-center">
            <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gray-50 dark:bg-gray-800/50 mb-4">
                <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8 text-gray-300 dark:text-gray-600"
                    fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
            </div>
            <p class="text-sm text-gray-500 dark:text-gray-400 font-medium">{{ t('search.noResultTitle') }}</p>
            <p class="text-xs text-gray-400 dark:text-gray-600 mt-1.5">{{ t('search.noResultDesc') }}</p>
        </div>

        <!-- 搜索结果列表 -->
        <div v-else class="space-y-0.5 px-0.5 py-1">
            <div v-for="item in results" :key="item.docId" @click="emit('select', item.docId)"
                class="group p-2 rounded-xl cursor-pointer transition-all duration-200 shadow-none border border-transparent"
                :class="[
                    item.docId === activeDocId 
                    ? 'bg-zinc-200 dark:bg-zinc-800 border-zinc-300/50 dark:border-zinc-700/50' 
                    : 'hover:bg-zinc-100 dark:hover:bg-zinc-800/50 hover:shadow-xl hover:shadow-zinc-500/5 hover:border-zinc-200 dark:hover:border-zinc-700'
                ]">
                <!-- 文档标题 -->
                <div class="flex items-center gap-2 mb-1">
                        <h4 class="text-sm font-bold text-gray-800 dark:text-gray-100 truncate search-highlight"
                            v-html="item.highlightedTitle"></h4>
                </div>
                <!-- 关键词上下文摘要 -->
                <div>
                    <p class="text-xs text-gray-500 dark:text-gray-400 leading-relaxed line-clamp-2 search-highlight"
                        v-html="item.highlightedSnippet || t('search.emptySnippet')"></p>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
/* 搜索结果关键词高亮 - 优化为柔和背景 */
.search-highlight :deep(mark) {
    background: #fef9c3;
    color: #854d0e;
    padding: 1px 3px;
    border-radius: 3px;
    font-weight: 600;
}

.dark .search-highlight :deep(mark) {
    background: #854d0e;
    color: #fef9c3;
}

.line-clamp-2 {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.line-clamp-3 {
    display: -webkit-box;
    -webkit-line-clamp: 3;
    line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.pl-5\.5 {
    padding-left: 1.375rem;
}
</style>
