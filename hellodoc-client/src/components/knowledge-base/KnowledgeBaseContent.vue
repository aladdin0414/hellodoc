<script setup lang="ts">
import { computed } from 'vue'
import { useTheme } from '../../composables/useTheme'
import { useI18n } from 'vue-i18n'
import { useLocale } from '../../composables/useLocale'
import { getIconStyle as utilGetIconStyle, getIconBgStyle as utilGetIconBgStyle } from '../../utils/color'
import * as Icons from 'lucide-vue-next'

type TabKey = 'my' | 'recent' | 'shared' | 'favorites'
type ViewMode = 'grid' | 'list'
type NavStyle = 'top' | 'left'

type HomeWidgetSettings = {
    showRecent: boolean
    showFavorites: boolean
}

type FavoriteItem = any

type RecentDoc = {
    kbId: number
    docId: number
    docName: string
    kbTitle: string
    lastAccessed: number
    mode: 'view' | 'edit'
}

type KbSummary = {
    id: number
    title: string
    description: string
    icon: string | null
    color?: string
    ownerId: number
    ownerName: string
    isShared: boolean
    lastModified: string
    visibility: string
    ownerAvatar: string
    role: string
    isPinned: boolean
    pinnedAt?: string
    createdAt?: string
    sortOrder?: number
    docCount?: number
    memberCount?: number
}

const props = defineProps<{
    activeTab: TabKey
    navStyle: NavStyle
    viewMode: ViewMode
    loading: boolean
    favLoading: boolean
    favorites: FavoriteItem[]
    filteredFavorites: FavoriteItem[]
    favoritesForHome: FavoriteItem[]
    filteredRecentDocs: RecentDoc[]
    recentDocsForHome: RecentDoc[]
    myKbs: KbSummary[]
    sharedKbs: KbSummary[]
    homeWidgetSettings: HomeWidgetSettings
    homeWidgetGridClass: string
    shouldHideCreateCard: boolean
    navigateToTab: (tab: TabKey) => void
    clearRecentDocs: () => void
    openRecentDoc: (doc: RecentDoc) => void
    handleFavoriteClick: (fav: FavoriteItem) => void
    handleUnfavorite: (docId: number) => void
    openCreateModal: () => void
    handleKbClick: (kb: KbSummary) => void
    openPublicView: (kbId: number) => void
    openMemberModal: (kb: KbSummary) => void
    openEditModal: (kb: KbSummary) => void
    handlePin: (kb: KbSummary) => void
    handleDelete: (id: number) => void
    handleLeave?: (kb: KbSummary) => void
}>()

const emit = defineEmits<{
    (e: 'update:viewMode', value: ViewMode): void
}>()

const viewModeModel = computed({
    get: () => props.viewMode,
    set: (value: ViewMode) => emit('update:viewMode', value),
})

const { isDark } = useTheme()
const { t } = useI18n()
const { effectiveLocale } = useLocale()

const getIconStyle = (color?: string) => utilGetIconStyle(color, isDark.value)
const getIconBgStyle = (color?: string) => utilGetIconBgStyle(color, isDark.value)

const formatDate = (dateStr?: string) => {
    if (!dateStr) return t('common.noData')
    const date = new Date(dateStr)
    if (isNaN(date.getTime())) return t('common.noData')
    return date.toLocaleDateString(effectiveLocale.value, {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
    })
}

const formatRelativeTime = (ts: number) => {
    if (!ts) return t('common.noData')
    const diffMs = Date.now() - ts
    if (diffMs < 60 * 1000) return t('time.justNow')
    if (diffMs < 60 * 60 * 1000) return t('time.minutesAgo', { count: Math.floor(diffMs / (60 * 1000)) })
    if (diffMs < 24 * 60 * 60 * 1000) return t('time.hoursAgo', { count: Math.floor(diffMs / (60 * 60 * 1000)) })
    return new Date(ts).toLocaleDateString(effectiveLocale.value, { month: '2-digit', day: '2-digit' })
}

const canManageMembers = (kb: KbSummary) => {
    if (!kb.role) return false
    const role = kb.role.toUpperCase()
    return role === 'OWNER' || role === 'ADMIN'
}

const visibilityText = (visibility?: string) => {
    const v = (visibility || '').toLowerCase()
    if (v === 'public') return t('kb.visibilityPublic')
    if (v === 'team' || v === 'internal') return t('kb.visibilityTeam')
    return t('kb.visibilityPrivate')
}

const visibilityBadgeClass = (visibility?: string) => {
    const v = (visibility || '').toLowerCase()
    if (v === 'public') return 'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-900/20 dark:text-emerald-300 dark:border-emerald-800/40'
    if (v === 'team' || v === 'internal') return 'bg-indigo-50 text-indigo-700 border-indigo-100 dark:bg-indigo-900/20 dark:text-indigo-300 dark:border-indigo-800/40'
    return 'bg-slate-50 text-slate-700 border-slate-200 dark:bg-slate-800/40 dark:text-slate-200 dark:border-slate-700/60'
}

// 图标处理逻辑
const getKbIcon = (iconName: string | undefined | null) => {
    if (!iconName) return null
    if (iconName.startsWith('http') || iconName.startsWith('/')) {
        return { type: 'image', value: iconName }
    }
    const icon = (Icons as any)[iconName]
    if (icon) {
        return { type: 'icon', value: icon }
    }
    return null
}
</script>

<template>
    <main class="max-w-7xl mx-auto py-10 px-4 sm:px-6 lg:px-8">
        <header class="mb-8 flex justify-between items-end">
            <div>
                <h1 class="text-3xl font-bold text-gray-900 dark:text-gray-100">{{ activeTab === 'my' ? t('kb.titleMy') :
                    activeTab === 'recent' ? t('kb.titleRecent') : activeTab === 'shared' ? t('kb.titleShared') : t('kb.titleFavorites') }}</h1>
                <p class="hidden sm:block mt-2 text-sm text-gray-600 dark:text-gray-400">
                    {{ activeTab === 'my' ? t('kb.descMy') : activeTab === 'recent' ? t('kb.descRecent') :
                        activeTab === 'shared' ? t('kb.descShared') : t('kb.descFavorites') }}
                </p>
            </div>
            <div class="flex items-center gap-2">
                <button v-if="activeTab === 'my' && navStyle !== 'left' && !shouldHideCreateCard" @click="openCreateModal"
                    class="inline-flex items-center justify-center gap-2 h-9 w-9 sm:w-auto px-0 sm:px-3 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition dark:bg-blue-500 dark:hover:bg-blue-600"
                    :title="t('kb.createKb')">
                    <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                    </svg>
                    <span class="hidden sm:inline">{{ t('kb.createKb') }}</span>
                </button>
                <div v-if="activeTab !== 'favorites' && activeTab !== 'recent'"
                    class="flex items-center h-9 bg-gray-100 dark:bg-gray-700 p-1 rounded-lg">
                    <button @click="viewModeModel = 'grid'" class="h-7 w-7 flex items-center justify-center rounded-md transition-all"
                        :class="viewModeModel === 'grid' ? 'bg-white dark:bg-gray-600 shadow-sm text-blue-600 dark:text-blue-400' : 'text-gray-400 hover:text-gray-600 dark:hover:text-gray-300'">
                        <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
                        </svg>
                    </button>
                    <button @click="viewModeModel = 'list'" class="h-7 w-7 flex items-center justify-center rounded-md transition-all"
                        :class="viewModeModel === 'list' ? 'bg-white dark:bg-gray-600 shadow-sm text-blue-600 dark:text-blue-400' : 'text-gray-400 hover:text-gray-600 dark:hover:text-gray-300'">
                        <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
                        </svg>
                    </button>
                </div>
            </div>
        </header>

        <div v-if="loading || (activeTab === 'favorites' && favLoading)" class="text-center py-20">
            <div class="inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-600 border-t-transparent">
            </div>
            <p class="mt-2 text-gray-500">{{ t('common.loading') }}</p>
        </div>

        <div v-else-if="activeTab === 'favorites'" class="space-y-4">
            <div v-if="favorites.length === 0"
                class="text-center py-20 bg-white dark:bg-gray-800 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
                <div class="h-16 w-16 bg-gray-50 rounded-full flex items-center justify-center mx-auto mb-4">
                    <svg class="h-8 w-8 text-gray-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                            d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.175 0l-3.976 2.888c-.783.57-1.837-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.382-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
                    </svg>
                </div>
                <p class="text-gray-500 dark:text-gray-400">{{ t('kb.emptyFavorites') }}</p>
            </div>
            <div v-else-if="filteredFavorites.length === 0"
                class="text-center py-20 bg-white dark:bg-gray-800 rounded-2xl border border-dashed border-gray-200 dark:border-gray-700">
                <p class="text-gray-500 dark:text-gray-400">{{ t('kb.noMatchFavorites') }}</p>
            </div>
            <div v-else v-for="fav in filteredFavorites" :key="fav.id" @click="handleFavoriteClick(fav)"
                class="group bg-white dark:bg-gray-800 p-4 rounded-xl border border-gray-200 dark:border-gray-700 hover:border-blue-200 dark:hover:border-blue-800 hover:shadow-sm transition-all cursor-pointer flex items-center justify-between">
                <div class="flex items-center space-x-4">
                    <div class="h-10 w-10 rounded-lg bg-blue-50 flex items-center justify-center text-blue-600">
                        <svg v-if="fav.type === 'folder'" class="h-6 w-6" fill="none" viewBox="0 0 24 24"
                            stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
                        </svg>
                        <svg v-else class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                        </svg>
                    </div>
                    <div>
                        <h4
                            class="font-medium text-gray-900 dark:text-gray-100 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition">
                            {{ fav.name }}
                        </h4>
                        <div class="flex items-center space-x-2 mt-1">
                            <span class="bg-gray-100 text-gray-500 text-[10px] px-1.5 py-0.5 rounded">{{ fav.kbTitle }}</span>
                            <span class="text-xs text-gray-400">{{ formatDate(fav.createdAt) }} {{ t('kb.favoritedAt') }}</span>
                        </div>
                    </div>
                </div>
                <button @click.stop="handleUnfavorite(fav.id)"
                    class="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors opacity-0 group-hover:opacity-100"
                    :title="t('kb.unfavorite')">
                    <svg class="h-5 w-5" fill="currentColor" viewBox="0 0 20 20">
                        <path
                            d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                    </svg>
                </button>
            </div>
        </div>

        <div v-else-if="activeTab === 'recent'" class="space-y-4">
            <div class="bg-white dark:bg-gray-800 rounded-2xl border border-gray-200 dark:border-gray-700 p-5">
                <div class="flex items-center justify-between mb-3">
                    <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-200">{{ t('kb.titleRecent') }}</h3>
                    <button @click="clearRecentDocs"
                        class="text-xs text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 transition">
                        {{ t('common.clear') }}
                    </button>
                </div>
                <div v-if="filteredRecentDocs.length === 0" class="text-sm text-gray-400 dark:text-gray-500 py-10 text-center">
                    {{ t('kb.emptyRecent') }}
                </div>
                <div v-else class="space-y-1">
                    <div v-for="d in filteredRecentDocs" :key="`${d.kbId}-${d.docId}`" @click="openRecentDoc(d)"
                        class="group flex items-center justify-between px-3 py-2 rounded-xl hover:bg-gray-50 dark:hover:bg-gray-700/40 cursor-pointer transition">
                        <div class="min-w-0">
                            <div class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                                {{ d.docName }}
                            </div>
                            <div class="mt-1 flex items-center space-x-2">
                                <span
                                    class="bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-300 text-[10px] px-1.5 py-0.5 rounded">
                                    {{ d.kbTitle || t('kb.defaultKbTitle') }}
                                </span>
                                <span class="text-[11px] text-gray-400">{{ formatRelativeTime(d.lastAccessed) }}</span>
                            </div>
                        </div>
                        <span
                            class="text-[10px] px-1.5 py-0.5 rounded border border-gray-200 dark:border-gray-700 text-gray-400 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition">
                            {{ d.mode === 'edit' ? t('kb.modeEdit') : t('kb.modeView') }}
                        </span>
                    </div>
                </div>
            </div>
        </div>

        <div v-else-if="viewModeModel === 'list'" class="space-y-8">
            <div v-if="activeTab === 'my' && (homeWidgetSettings.showRecent || homeWidgetSettings.showFavorites)" class="space-y-6">
                <div v-if="homeWidgetSettings.showRecent"
                    class="bg-white dark:bg-gray-800 rounded-2xl border border-gray-200 dark:border-gray-700 p-5">
                    <div class="flex items-center justify-between mb-3">
                        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-200">{{ t('kb.titleRecent') }}</h3>
                        <button @click="clearRecentDocs"
                            class="text-xs text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 transition">
                            {{ t('common.clear') }}
                        </button>
                    </div>
                    <div v-if="filteredRecentDocs.length === 0" class="text-sm text-gray-400 dark:text-gray-500 py-6 text-center">
                        {{ t('kb.emptyRecent') }}
                    </div>
                    <div v-else class="space-y-1">
                        <div v-for="d in recentDocsForHome" :key="`${d.kbId}-${d.docId}`" @click="openRecentDoc(d)"
                            class="group flex items-center justify-between px-3 py-2 rounded-xl hover:bg-gray-50 dark:hover:bg-gray-700/40 cursor-pointer transition">
                            <div class="min-w-0">
                                <div class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                                    {{ d.docName }}
                                </div>
                                <div class="mt-1 flex items-center space-x-2">
                                    <span
                                        class="bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-300 text-[10px] px-1.5 py-0.5 rounded">
                                        {{ d.kbTitle || t('kb.defaultKbTitle') }}
                                    </span>
                                    <span class="text-[11px] text-gray-400">{{ formatRelativeTime(d.lastAccessed) }}</span>
                                </div>
                            </div>
                            <span
                                class="text-[10px] px-1.5 py-0.5 rounded border border-gray-200 dark:border-gray-700 text-gray-400 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition">
                                {{ d.mode === 'edit' ? t('kb.modeEdit') : t('kb.modeView') }}
                            </span>
                        </div>
                    </div>
                </div>

                <div v-if="homeWidgetSettings.showFavorites"
                    class="bg-white dark:bg-gray-800 rounded-2xl border border-gray-200 dark:border-gray-700 p-5">
                    <div class="flex items-center justify-between mb-3">
                        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-200">{{ t('settings.favoritesTitle') }}</h3>
                        <button @click="navigateToTab('favorites')"
                            class="text-xs text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 transition">
                            {{ t('common.all') }}
                        </button>
                    </div>
                    <div v-if="favLoading" class="text-sm text-gray-400 dark:text-gray-500 py-6 text-center">
                        {{ t('common.loading') }}
                    </div>
                    <div v-else-if="favoritesForHome.length === 0" class="text-sm text-gray-400 dark:text-gray-500 py-6 text-center">
                        {{ t('kb.emptyFavorites') }}
                    </div>
                    <div v-else class="space-y-1">
                        <div v-for="fav in favoritesForHome" :key="fav.id" @click="handleFavoriteClick(fav)"
                            class="group flex items-center justify-between px-3 py-2 rounded-xl hover:bg-gray-50 dark:hover:bg-gray-700/40 cursor-pointer transition">
                            <div class="min-w-0">
                                <div class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                                    {{ fav.name }}
                                </div>
                                <div class="mt-1 flex items-center space-x-2">
                                    <span
                                        class="bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-300 text-[10px] px-1.5 py-0.5 rounded">
                                        {{ fav.kbTitle }}
                                    </span>
                                    <span class="text-[11px] text-gray-400">{{ formatDate(fav.createdAt) }} {{ t('kb.favoritedAt') }}</span>
                                </div>
                            </div>
                            <button @click.stop="handleUnfavorite(fav.id)"
                                class="p-2 text-gray-300 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors opacity-0 group-hover:opacity-100"
                                :title="t('kb.unfavorite')">
                                <svg class="h-4 w-4" fill="currentColor" viewBox="0 0 20 20">
                                    <path
                                        d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                                </svg>
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            <div v-if="activeTab === 'my'" class="space-y-3">
                <div v-for="kb in myKbs" :key="kb.id" @click="handleKbClick(kb)"
                    class="group bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 md:hover:border-blue-200 dark:md:hover:border-blue-800 md:hover:shadow-sm transition-all cursor-pointer flex items-center justify-between relative overflow-hidden">
                    <div class="absolute left-0 top-0 bottom-0 w-1.5" :style="{ backgroundColor: kb.color || '#3b82f6' }"></div>
                    <div class="flex items-center space-x-4 min-w-0 flex-1 p-4 pl-5">
                        <div class="kb-icon-shell kb-icon-shell--md" :style="getIconBgStyle(kb.color)">
                            <template v-if="getKbIcon(kb.icon)">
                                <img v-if="getKbIcon(kb.icon)?.type === 'image'" :src="String(getKbIcon(kb.icon)?.value)"
                                    class="kb-icon-glyph kb-icon-glyph--md object-cover rounded" alt="kb-icon" />
                                <component v-else :is="getKbIcon(kb.icon)?.value" class="kb-icon-glyph kb-icon-glyph--md" :style="getIconStyle(kb.color)" />
                            </template>
                            <template v-else>
                                <component :is="(Icons as any).Book" class="kb-icon-glyph kb-icon-glyph--md" :style="getIconStyle(kb.color)" />
                            </template>
                        </div>
                        <div class="min-w-0 flex-1">
                            <div class="flex items-baseline space-x-2">
                                <h4
                                    class="font-medium text-gray-900 dark:text-gray-100 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition truncate">
                                    {{ kb.title }}</h4>
                                <div class="flex items-center space-x-1 flex-shrink-0">
                                </div>
                            </div>
                            <div class="flex items-center space-x-3 text-[11px] text-gray-400 mt-0.5">
                                <span class="truncate">{{ kb.ownerName }}</span>
                                <span>{{ new Date(kb.lastModified).toLocaleDateString() }}</span>
                                <span class="text-[10px] px-1.5 py-0.5 rounded border flex-shrink-0 whitespace-nowrap" :class="visibilityBadgeClass(kb.visibility)">{{
                                    visibilityText(kb.visibility) }}</span>
                                <span v-if="typeof kb.docCount === 'number'" class="text-[10px] text-gray-400">{{ t('kb.docs') }} {{ kb.docCount }}</span>
                                <span v-if="typeof kb.memberCount === 'number'" class="text-[10px] text-gray-400">{{ t('kb.members') }} {{ kb.memberCount }}</span>
                            </div>
                        </div>
                    </div>
                    <div class="flex items-center space-x-1 ml-4 pr-4 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity">
                        <button @click.stop="openPublicView(kb.id)"
                            class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-colors" :title="t('editor.browse')">
                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                            </svg>
                        </button>
                        <button v-if="canManageMembers(kb)" @click.stop="openMemberModal(kb)"
                            class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-colors" :title="t('kb.members')">
                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
                            </svg>
                        </button>
                        <button v-if="canManageMembers(kb)" @click.stop="openEditModal(kb)"
                            class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-colors" :title="t('kb.edit')">
                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                            </svg>
                        </button>
                        <button @click.stop="handlePin(kb)"
                            class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-colors"
                            :class="{ 'text-blue-600': kb.isPinned }" :title="kb.isPinned ? t('kb.unpin') : t('kb.pin')">
                            <svg class="h-4 w-4" :class="{ 'rotate-45': !kb.isPinned, 'fill-current': kb.isPinned }" viewBox="0 0 24 24"
                                fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <line x1="12" y1="17" x2="12" y2="22"></line>
                                <path
                                    d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6a3 3 0 0 0-3-3 3 3 0 0 0-3 3v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z">
                                </path>
                            </svg>
                        </button>
                        <button v-if="kb.role === 'OWNER'" @click.stop="handleDelete(kb.id)"
                            class="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors" :title="t('editor.delete')">
                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                        </button>
                    </div>
                </div>
            </div>

            <div v-else class="space-y-3">
                <div v-for="kb in sharedKbs" :key="kb.id" @click="handleKbClick(kb)"
                    class="group bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 md:hover:border-blue-200 dark:md:hover:border-blue-800 md:hover:shadow-sm transition-all cursor-pointer flex items-center justify-between relative overflow-hidden">
                    <div class="absolute left-0 top-0 bottom-0 w-1.5" :style="{ backgroundColor: kb.color || '#3b82f6' }"></div>
                    <div class="flex items-center space-x-4 min-w-0 flex-1 p-4 pl-5">
                        <div class="kb-icon-shell kb-icon-shell--md" :style="getIconBgStyle(kb.color)">
                            <template v-if="getKbIcon(kb.icon)">
                                <img v-if="getKbIcon(kb.icon)?.type === 'image'" :src="String(getKbIcon(kb.icon)?.value)"
                                    class="kb-icon-glyph kb-icon-glyph--md object-cover rounded" alt="kb-icon" />
                                <component v-else :is="getKbIcon(kb.icon)?.value" class="kb-icon-glyph kb-icon-glyph--md" :style="getIconStyle(kb.color)" />
                            </template>
                            <template v-else>
                                <component :is="(Icons as any).Book" class="kb-icon-glyph kb-icon-glyph--md" :style="getIconStyle(kb.color)" />
                            </template>
                        </div>
                        <div class="min-w-0 flex-1">
                            <div class="flex items-baseline space-x-2">
                                <h4
                                    class="font-medium text-gray-900 dark:text-gray-100 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition truncate">
                                    {{ kb.title }}</h4>
                                <div class="flex items-center space-x-1 flex-shrink-0">
                                    <span v-if="kb.role && kb.role !== 'OWNER'" :title="t('kb.fromShared')"
                                        class="bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 p-1 rounded-md border border-indigo-100 dark:border-indigo-700/50 flex items-center justify-center transition-transform hover:scale-110">
                                        <svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                                            stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                                            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                                            <circle cx="9" cy="7" r="4"></circle>
                                            <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                                            <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                                        </svg>
                                    </span>
                                </div>
                            </div>
                            <div class="flex items-center space-x-3 text-[11px] text-gray-400 mt-0.5">
                                <span class="truncate">{{ kb.ownerName }}</span>
                                <span>{{ new Date(kb.lastModified).toLocaleDateString() }}</span>
                                <span class="text-[10px] px-1.5 py-0.5 rounded border flex-shrink-0 whitespace-nowrap" :class="visibilityBadgeClass(kb.visibility)">{{
                                    visibilityText(kb.visibility) }}</span>
                                <span v-if="typeof kb.docCount === 'number'" class="text-[10px] text-gray-400">{{ t('kb.docs') }} {{ kb.docCount }}</span>
                                <span v-if="typeof kb.memberCount === 'number'" class="text-[10px] text-gray-400">{{ t('kb.members') }} {{ kb.memberCount }}</span>
                            </div>
                        </div>
                    </div>
                    <div class="flex items-center space-x-1 ml-4 pr-4 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity">
                        <button @click.stop="openPublicView(kb.id)"
                            class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-colors" :title="t('editor.browse')">
                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                            </svg>
                        </button>
                        <button @click.stop="handlePin(kb)"
                            class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-colors"
                            :class="{ 'text-blue-600': kb.isPinned }" :title="kb.isPinned ? t('kb.unpin') : t('kb.pin')">
                            <svg class="h-4 w-4" :class="{ 'rotate-45': !kb.isPinned, 'fill-current': kb.isPinned }" viewBox="0 0 24 24"
                                fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <line x1="12" y1="17" x2="12" y2="22"></line>
                                <path
                                    d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6a3 3 0 0 0-3-3 3 3 0 0 0-3 3v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z">
                                </path>
                            </svg>
                        </button>
                        <button v-if="kb.role !== 'OWNER' && handleLeave" @click.stop="handleLeave(kb)"
                            class="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors" :title="t('kb.leave')">
                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                            </svg>
                        </button>
                    </div>
                </div>
            </div>
        </div>

        <div v-else class="space-y-12">
            <div v-if="activeTab === 'my' && (homeWidgetSettings.showRecent || homeWidgetSettings.showFavorites)" :class="homeWidgetGridClass">
                <div v-if="homeWidgetSettings.showRecent"
                    class="bg-white dark:bg-gray-800 rounded-2xl border border-gray-200 dark:border-gray-700 p-5">
                    <div class="flex items-center justify-between mb-3">
                        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-200">{{ t('kb.titleRecent') }}</h3>
                        <button @click="clearRecentDocs"
                            class="text-xs text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 transition">
                            {{ t('common.clear') }}
                        </button>
                    </div>
                    <div v-if="filteredRecentDocs.length === 0" class="text-sm text-gray-400 dark:text-gray-500 py-10 text-center">
                        {{ t('kb.emptyRecent') }}
                    </div>
                    <div v-else class="space-y-1">
                        <div v-for="d in recentDocsForHome" :key="`${d.kbId}-${d.docId}`" @click="openRecentDoc(d)"
                            class="group flex items-center justify-between px-3 py-2 rounded-xl hover:bg-gray-50 dark:hover:bg-gray-700/40 cursor-pointer transition">
                            <div class="min-w-0">
                                <div class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                                    {{ d.docName }}
                                </div>
                                <div class="mt-1 flex items-center space-x-2">
                                    <span
                                        class="bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-300 text-[10px] px-1.5 py-0.5 rounded">
                                        {{ d.kbTitle || t('kb.defaultKbTitle') }}
                                    </span>
                                    <span class="text-[11px] text-gray-400">{{ formatRelativeTime(d.lastAccessed) }}</span>
                                </div>
                            </div>
                            <span
                                class="text-[10px] px-1.5 py-0.5 rounded border border-gray-200 dark:border-gray-700 text-gray-400 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition">
                                {{ d.mode === 'edit' ? t('kb.modeEdit') : t('kb.modeView') }}
                            </span>
                        </div>
                    </div>
                </div>

                <div v-if="homeWidgetSettings.showFavorites"
                    class="bg-white dark:bg-gray-800 rounded-2xl border border-gray-200 dark:border-gray-700 p-5">
                    <div class="flex items-center justify-between mb-3">
                        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-200">{{ t('settings.favoritesTitle') }}</h3>
                        <button @click="navigateToTab('favorites')"
                            class="text-xs text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 transition">
                            {{ t('common.all') }}
                        </button>
                    </div>
                    <div v-if="favLoading" class="text-sm text-gray-400 dark:text-gray-500 py-10 text-center">
                        {{ t('common.loading') }}
                    </div>
                    <div v-else-if="favoritesForHome.length === 0" class="text-sm text-gray-400 dark:text-gray-500 py-10 text-center">
                        {{ t('kb.emptyFavorites') }}
                    </div>
                    <div v-else class="space-y-1">
                        <div v-for="fav in favoritesForHome" :key="fav.id" @click="handleFavoriteClick(fav)"
                            class="group flex items-center justify-between px-3 py-2 rounded-xl hover:bg-gray-50 dark:hover:bg-gray-700/40 cursor-pointer transition">
                            <div class="min-w-0">
                                <div class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                                    {{ fav.name }}
                                </div>
                                <div class="mt-1 flex items-center space-x-2">
                                    <span
                                        class="bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-300 text-[10px] px-1.5 py-0.5 rounded">
                                        {{ fav.kbTitle }}
                                    </span>
                                    <span class="text-[11px] text-gray-400">{{ formatDate(fav.createdAt) }} {{ t('kb.favoritedAt') }}</span>
                                </div>
                            </div>
                            <button @click.stop="handleUnfavorite(fav.id)"
                                class="p-2 text-gray-300 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors opacity-0 group-hover:opacity-100"
                                :title="t('kb.unfavorite')">
                                <svg class="h-4 w-4" fill="currentColor" viewBox="0 0 20 20">
                                    <path
                                        d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                                </svg>
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            <template v-if="activeTab === 'my'">
                <div class="space-y-4">
                    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
                        <div v-for="kb in myKbs" :key="kb.id" @click="handleKbClick(kb)"
                            class="relative group bg-white dark:bg-gray-800 overflow-hidden rounded-xl shadow-sm border border-gray-200 dark:border-gray-700 md:hover:shadow-md transition-all duration-200 cursor-pointer">
                            <div class="h-2" :style="{ backgroundColor: kb.color || '#3b82f6' }"></div>
                            <div class="p-4">
                                <div class="flex justify-between items-start mb-3">
                                    <div class="kb-icon-shell kb-icon-shell--sm" :style="getIconBgStyle(kb.color)">
                                        <template v-if="getKbIcon(kb.icon)">
                                            <img v-if="getKbIcon(kb.icon)?.type === 'image'" :src="String(getKbIcon(kb.icon)?.value)"
                                                class="kb-icon-glyph kb-icon-glyph--sm object-cover rounded" alt="kb-icon" />
                                            <component v-else :is="getKbIcon(kb.icon)?.value" class="kb-icon-glyph kb-icon-glyph--sm" :style="getIconStyle(kb.color)" />
                                        </template>
                                        <template v-else>
                                            <component :is="(Icons as any).Book" class="kb-icon-glyph kb-icon-glyph--sm" :style="getIconStyle(kb.color)" />
                                        </template>
                                    </div>
                                    <div class="flex gap-1.5 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity">
                                        <button @click.stop="openPublicView(kb.id)"
                                            class="p-1 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-colors" :title="t('editor.browse')">
                                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                                    d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                                    d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                                            </svg>
                                        </button>
                                        <button v-if="canManageMembers(kb)" @click.stop="openMemberModal(kb)"
                                            class="p-1 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-colors" :title="t('kb.collabMembers')">
                                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                                    d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
                                            </svg>
                                        </button>
                                        <button v-if="canManageMembers(kb)" @click.stop="openEditModal(kb)"
                                            class="p-1 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-colors" :title="t('kb.settings')">
                                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                                    d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                                            </svg>
                                        </button>
                                        <button v-if="kb.role === 'OWNER'" @click.stop="handleDelete(kb.id)"
                                            class="p-1 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors" :title="t('editor.delete')">
                                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                                    d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                            </svg>
                                        </button>
                                    </div>
                                </div>
                                <div class="flex items-center space-x-2 mb-1 min-w-0">
                                    <h3
                                        class="text-base font-bold text-gray-900 dark:text-gray-100 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition truncate">
                                        {{ kb.title }}</h3>
                                    <span class="text-[10px] px-1.5 py-0.5 rounded border flex-shrink-0 whitespace-nowrap" :class="visibilityBadgeClass(kb.visibility)">{{
                                        visibilityText(kb.visibility) }}</span>
                                </div>
                                <div class="flex items-center space-x-2 mb-2">
                                    <span v-if="typeof kb.docCount === 'number'" class="text-[10px] text-gray-400">{{ t('kb.docs') }} {{ kb.docCount }}</span>
                                    <span v-if="typeof kb.memberCount === 'number'" class="text-[10px] text-gray-400">{{ t('kb.members') }} {{ kb.memberCount }}</span>
                                </div>
                                <p class="text-[11px] text-gray-500 dark:text-gray-400 truncate mb-3">{{
                                    kb.description || t('editor.noDescription') }}</p>

                                <div class="flex items-center justify-between mt-auto pt-3 border-t border-gray-50 dark:border-gray-700/50">
                                    <div class="flex items-center space-x-2">
                                        <div
                                            class="h-6 w-6 rounded-full overflow-hidden bg-gray-100 border border-gray-200 dark:border-gray-700">
                                            <img v-if="kb.ownerAvatar" :src="kb.ownerAvatar" class="h-full w-full object-cover">
                                            <div v-else
                                                class="h-full w-full flex items-center justify-center text-[10px] text-gray-400 font-bold bg-gray-50">
                                                {{ (kb.ownerName || 'U').charAt(0).toUpperCase() }}
                                            </div>
                                        </div>
                                        <span class="text-xs text-gray-500 dark:text-gray-400 font-medium truncate max-w-[80px]">{{
                                            kb.ownerName }}</span>
                                    </div>
                                    <div class="flex items-center space-x-3">
                                        <span class="text-[10px] text-gray-400 font-medium">{{ formatDate(kb.lastModified) }}</span>
                                        <button @click.stop="handlePin(kb)"
                                            class="p-1 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors group/pin"
                                            :class="kb.isPinned ? 'text-blue-600 dark:text-blue-400' : 'text-gray-300 dark:text-gray-600 hover:text-blue-600'">
                                            <svg class="h-4 w-4" :class="{ 'rotate-45': !kb.isPinned, 'fill-current': kb.isPinned }"
                                                viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                                                stroke-linejoin="round">
                                                <line x1="12" y1="17" x2="12" y2="22"></line>
                                                <path
                                                    d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6a3 3 0 0 0-3-3 3 3 0 0 0-3 3v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z">
                                                </path>
                                            </svg>
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </template>

            <template v-else>
                <div class="space-y-4">
                    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
                        <div v-for="kb in sharedKbs" :key="kb.id" @click="handleKbClick(kb)"
                            class="relative group bg-white dark:bg-gray-800 overflow-hidden rounded-xl shadow-sm border border-gray-200 dark:border-gray-700 md:hover:shadow-md transition-all duration-200 cursor-pointer">
                            <div class="h-2" :style="{ backgroundColor: kb.color || '#3b82f6' }"></div>
                            <div class="p-4">
                                <div class="flex justify-between items-start mb-3">
                                    <div class="kb-icon-shell kb-icon-shell--sm" :style="getIconBgStyle(kb.color)">
                                        <template v-if="getKbIcon(kb.icon)">
                                            <img v-if="getKbIcon(kb.icon)?.type === 'image'" :src="String(getKbIcon(kb.icon)?.value)"
                                                class="kb-icon-glyph kb-icon-glyph--sm object-cover rounded" alt="kb-icon" />
                                            <component v-else :is="getKbIcon(kb.icon)?.value" class="kb-icon-glyph kb-icon-glyph--sm" :style="getIconStyle(kb.color)" />
                                        </template>
                                        <template v-else>
                                            <component :is="(Icons as any).Book" class="kb-icon-glyph kb-icon-glyph--sm" :style="getIconStyle(kb.color)" />
                                        </template>
                                    </div>
                                    <div class="flex gap-1.5 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity">
                                        <button @click.stop="openPublicView(kb.id)"
                                            class="p-1 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-colors" :title="t('editor.browse')">
                                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                                    d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                                    d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                                            </svg>
                                        </button>
                                        <button v-if="canManageMembers(kb)" @click.stop="openMemberModal(kb)"
                                            class="p-1 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-colors" :title="t('kb.collabMembers')">
                                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                                    d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
                                            </svg>
                                        </button>
                                        <button v-if="canManageMembers(kb)" @click.stop="openEditModal(kb)"
                                            class="p-1 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-colors" :title="t('kb.settings')">
                                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                                    d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                                            </svg>
                                        </button>
                                        <button v-if="kb.role !== 'OWNER' && handleLeave" @click.stop="handleLeave(kb)"
                                            class="p-1 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors" :title="t('kb.leave')">
                                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                                    d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                                            </svg>
                                        </button>
                                    </div>
                                </div>
                                <div class="flex items-center space-x-2 mb-1 min-w-0">
                                    <h3
                                        class="text-base font-bold text-gray-900 dark:text-gray-100 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition truncate">
                                        {{ kb.title }}</h3>
                                    <span class="text-[10px] px-1.5 py-0.5 rounded border flex-shrink-0 whitespace-nowrap" :class="visibilityBadgeClass(kb.visibility)">{{
                                        visibilityText(kb.visibility) }}</span>
                                </div>
                                <div class="flex items-center space-x-2 mb-2">
                                    <span v-if="typeof kb.docCount === 'number'" class="text-[10px] text-gray-400">{{ t('kb.docs') }} {{ kb.docCount }}</span>
                                    <span v-if="typeof kb.memberCount === 'number'" class="text-[10px] text-gray-400">{{ t('kb.members') }} {{ kb.memberCount }}</span>
                                </div>
                                <p class="text-[11px] text-gray-500 dark:text-gray-400 truncate mb-3">{{
                                    kb.description || t('editor.noDescription') }}</p>

                                <div class="flex items-center justify-between mt-auto pt-3 border-t border-gray-50 dark:border-gray-700/50">
                                    <div class="flex items-center space-x-2">
                                        <div
                                            class="h-6 w-6 rounded-full overflow-hidden bg-gray-100 border border-gray-200 dark:border-gray-700">
                                            <img v-if="kb.ownerAvatar" :src="kb.ownerAvatar" class="h-full w-full object-cover">
                                            <div v-else
                                                class="h-full w-full flex items-center justify-center text-[10px] text-gray-400 font-bold bg-gray-50">
                                                {{ (kb.ownerName || 'U').charAt(0).toUpperCase() }}
                                            </div>
                                        </div>
                                        <span class="text-xs text-gray-500 dark:text-gray-400 font-medium truncate max-w-[80px]">{{
                                            kb.ownerName }}</span>
                                    </div>
                                    <div class="flex items-center space-x-3">
                                        <span class="text-[10px] text-gray-400 font-medium">{{ formatDate(kb.lastModified) }}</span>
                                        <button @click.stop="handlePin(kb)"
                                            class="p-1 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors group/pin"
                                            :class="kb.isPinned ? 'text-blue-600 dark:text-blue-400' : 'text-gray-300 dark:text-gray-600 hover:text-blue-600'">
                                            <svg class="h-4 w-4" :class="{ 'rotate-45': !kb.isPinned, 'fill-current': kb.isPinned }"
                                                viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                                                stroke-linejoin="round">
                                                <line x1="12" y1="17" x2="12" y2="22"></line>
                                                <path
                                                    d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6a3 3 0 0 0-3-3 3 3 0 0 0-3 3v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z">
                                                </path>
                                            </svg>
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </template>
        </div>
    </main>
</template>

<style scoped>
@import '../../styles/kb-icon.css';
</style>
