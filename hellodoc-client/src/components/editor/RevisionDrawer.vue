<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineProps<{
    visible: boolean
    revisions: any[]
    revisionsLoading: boolean
    revisionsPage: number
    hasMoreRevisions: boolean
}>()

const emit = defineEmits<{
    close: []
    viewDiff: [rev: any]
    loadMore: []
}>()

const { t } = useI18n()
</script>

<template>
    <div v-if="visible" class="fixed inset-0 z-[250] overflow-hidden" role="dialog" aria-modal="true" @click="emit('close')">
        <div class="absolute inset-0 bg-slate-900/10 backdrop-blur-sm transition-opacity" aria-hidden="true"></div>
        <div class="fixed inset-y-0 right-0 flex max-w-full pl-10">
            <div @click.stop
                class="pointer-events-auto w-screen max-w-md transform transition ease-in-out duration-500 sm:duration-700">
                <div class="flex h-full flex-col overflow-y-scroll bg-white dark:bg-[#161b22] shadow-2xl scrollbar-subtle">
                    <div
                        class="bg-slate-50 dark:bg-[#161b22] px-4 py-6 sm:px-6 border-b border-slate-200 dark:border-gray-700">
                        <div class="flex items-start justify-between">
                            <h2 class="text-lg font-bold text-slate-900 dark:text-gray-100">{{ t('editor.revisions') }}</h2>
                            <div class="ml-3 flex h-7 items-center">
                                <button type="button" @click="emit('close')"
                                    class="rounded-md bg-slate-50 dark:bg-[#161b22] text-slate-400 hover:text-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500">
                                    <span class="sr-only">{{ t('common.close') }}</span>
                                    <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke-width="1.5"
                                        stroke="currentColor" aria-hidden="true">
                                        <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                                    </svg>
                                </button>
                            </div>
                        </div>
                        <p class="mt-1 text-sm text-slate-500">{{ t('editor.revisionDesc') }}</p>
                    </div>
                    <div class="relative flex-1 px-4 py-6 sm:px-6 overflow-y-auto scrollbar-subtle">
                        <div v-if="revisionsLoading && revisionsPage === 0" class="flex justify-center py-10">
                            <svg class="animate-spin h-8 w-8 text-indigo-600" fill="none" viewBox="0 0 24 24">
                                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4">
                                </circle>
                                <path class="opacity-75" fill="currentColor"
                                    d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z">
                                </path>
                            </svg>
                        </div>
                        <div v-else-if="revisions.length === 0" class="text-center py-10">
                            <p class="text-slate-500 text-sm">{{ t('editor.noRevisions') }}</p>
                        </div>
                        <div v-else class="flow-root">
                            <ul role="list" class="-mb-8">
                                <li v-for="(rev, index) in revisions" :key="rev.id">
                                    <div class="relative pb-8">
                                        <span v-if="index !== revisions.length - 1 || hasMoreRevisions"
                                            class="absolute left-4 top-4 -ml-px h-full w-0.5 bg-slate-200"
                                            aria-hidden="true"></span>
                                        <div class="relative flex space-x-3">
                                            <div>
                                                <span
                                                    class="h-8 w-8 rounded-full flex items-center justify-center ring-8 ring-white dark:ring-gray-800"
                                                    :class="['auto', 'AUTO'].includes(rev.type) ? 'bg-slate-100' : 'bg-indigo-100'">
                                                    <svg v-if="['auto', 'AUTO'].includes(rev.type)" class="h-5 w-5 text-slate-500"
                                                        fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                        <path stroke-linecap="round" stroke-linejoin="round"
                                                            stroke-width="2"
                                                            d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                                                    </svg>
                                                    <svg v-else class="h-5 w-5 text-indigo-500" fill="none"
                                                        viewBox="0 0 24 24" stroke="currentColor">
                                                        <path stroke-linecap="round" stroke-linejoin="round"
                                                            stroke-width="2"
                                                            d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l4.414 4.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                                    </svg>
                                                </span>
                                            </div>
                                            <div class="flex-1 min-w-0">
                                                <div class="flex justify-between items-start">
                                                    <div>
                                                        <p class="text-sm font-bold text-slate-900 dark:text-gray-100">
                                                            {{ t('editor.revisionVersion', { version: rev.version }) }}
                                                            <span
                                                                class="ml-2 inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-800">
                                                                {{ ['auto', 'AUTO'].includes(rev.type) ? t('editor.autoSave') : t('editor.manualCommit') }}
                                                            </span>
                                                        </p>
                                                        <p class="mt-0.5 text-xs text-slate-500">
                                                            {{ new Date(rev.createdAt).toLocaleString() }} · {{
                                                                rev.authorName || t('editor.system') }}
                                                        </p>
                                                    </div>
                                                </div>
                                                <div class="mt-2 text-sm text-slate-700 dark:text-gray-300">
                                                    <p>{{ rev.message || t('editor.noDescription') }}</p>
                                                </div>
                                                <div class="mt-2">
                                                    <button @click="emit('viewDiff', rev)"
                                                        class="text-xs font-semibold text-indigo-600 dark:text-indigo-400 hover:text-indigo-800 dark:hover:text-indigo-300">
                                                        {{ t('editor.viewChanges') }}
                                                    </button>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </li>
                            </ul>

                            <div v-if="hasMoreRevisions" class="mt-8 text-center pb-8">
                                <button @click="emit('loadMore')" :disabled="revisionsLoading"
                                    class="text-sm font-bold text-indigo-600 hover:text-indigo-500 disabled:opacity-50">
                                    {{ revisionsLoading ? t('editor.loadingMore') : t('editor.loadMore') }}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
