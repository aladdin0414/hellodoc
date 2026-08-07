<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
    getKbList, createKb, updateKb, deleteKb, pinKb, leaveKbMember
} from '../api/kb'
import { getFrontendConfigs } from '../api/config'
import { getMe } from '../api/user'
import { getMyFavorites, removeFavorite } from '../api/favorite'
import ConfirmDialog from './ConfirmDialog.vue'
import ChangePasswordModal from './ChangePasswordModal.vue'
import SystemSettingsModal from './SystemSettingsModal.vue'
import KbUpsertModal from './knowledge-base/KbUpsertModal.vue'
import KbMemberModal from './knowledge-base/KbMemberModal.vue'
import UserProfileModal from './knowledge-base/UserProfileModal.vue'
import KnowledgeBaseNavbar from './knowledge-base/KnowledgeBaseNavbar.vue'
import KnowledgeBaseContent from './knowledge-base/KnowledgeBaseContent.vue'
import SearchModal from './SearchModal.vue'
import { message } from '../utils/message'
import { useI18n } from 'vue-i18n'
import { setLocaleModeLocal } from '../composables/useLocale'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

interface KbSummary {
    id: number
    title: string
    description: string
    icon: string | null
    color?: string
    ownerId: number
    ownerName: string
    isPinned: boolean
    isShared: boolean
    lastModified: string
    visibility: string
    ownerAvatar: string
    role: string
    pinnedAt?: string
    createdAt?: string
    sortOrder?: number
    docCount?: number
    memberCount?: number
}

interface RecentDoc {
    kbId: number
    docId: number
    docName: string
    kbTitle: string
    lastAccessed: number
    mode: 'view' | 'edit'
}


const kbs = ref<KbSummary[]>([])
const loading = ref(false)
const searchQuery = ref('')
const searchType = ref<'doc' | 'kb'>('doc')
const activeTab = ref<'my' | 'recent' | 'shared' | 'favorites'>('my')
const favorites = ref<any[]>([])
const favLoading = ref(false)
const recentDocs = ref<RecentDoc[]>([])
const submitting = ref(false)
const frontendConfigs = ref<Record<string, string>>({})
const isSearchEnabled = computed(() => frontendConfigs.value['app.kb_search.enabled'] !== 'false')
const showMobileSidebar = ref(false)
const navStyle = computed<'top' | 'left'>(() => {
    const remoteStyle = frontendConfigs.value['app.kb.nav_style'] as 'top' | 'left'
    if (remoteStyle) {
        localStorage.setItem('app.kb.nav_style', remoteStyle)
        return remoteStyle
    }
    return (localStorage.getItem('app.kb.nav_style') as 'top' | 'left') || 'top'
})
const viewMode = ref<'grid' | 'list'>((localStorage.getItem('kbViewMode') as 'grid' | 'list') || 'grid')

watch(viewMode, (newVal) => {
    localStorage.setItem('kbViewMode', newVal)
})

const getTabFromPath = (path: string) => {
    if (path.startsWith('/favorites')) return 'favorites'
    if (path.startsWith('/recent')) return 'recent'
    if (path.startsWith('/shared')) return 'shared'
    return 'my'
}

const syncTabWithRoute = () => {
    const tab = getTabFromPath(route.path)
    if (activeTab.value !== tab) {
        activeTab.value = tab
    }
}

syncTabWithRoute()

watch(() => route.path, () => {
    syncTabWithRoute()
    showMobileSidebar.value = false
    showUserDropdown.value = false
    const tab = getTabFromPath(route.path)
    if (tab === 'recent' || (tab === 'my' && homeWidgetSettings.value.showRecent)) {
        loadRecentDocs()
    }
})

const navigateToTab = (tab: 'my' | 'recent' | 'shared' | 'favorites') => {
    const targetPath = tab === 'my' ? '/' : tab === 'recent' ? '/recent' : tab === 'shared' ? '/shared' : '/favorites'
    if (route.path === targetPath) return
    router.push(targetPath)
}

// Predefined colors
const colors = [
    { name: 'Blue', value: '#3b82f6' },
    { name: 'Green', value: '#10b981' },
    { name: 'Purple', value: '#8b5cf6' },
    { name: 'Orange', value: '#f59e0b' },
    { name: 'Red', value: '#ef4444' },
    { name: 'Cyan', value: '#06b6d4' },
    { name: 'Black', value: '#1f2937' },
]

// Modals state
const showCreateModal = ref(false)
const showEditModal = ref(false)
const showConfirmModal = ref(false)
const showMemberModal = ref(false)
const showSystemSettings = ref(false)

type HomeWidgetSettings = {
    showRecent: boolean
    showFavorites: boolean
}

type KbUpsertModel = {
    id?: number
    title?: string
    description?: string
    color?: string
    icon?: string
    visibility?: string
}

const HOME_WIDGET_SETTINGS_KEY = 'homeWidgetSettings'
const getHomeWidgetSettingsStorageKey = () => {
    const userKey = localStorage.getItem('username') || localStorage.getItem('email') || localStorage.getItem('nickname') || 'default'
    return `${HOME_WIDGET_SETTINGS_KEY}:${userKey}`
}
const homeWidgetSettings = ref<HomeWidgetSettings>({
    showRecent: true,
    showFavorites: true
})

const loadHomeWidgetSettings = () => {
    try {
        const namespacedKey = getHomeWidgetSettingsStorageKey()
        const raw = localStorage.getItem(namespacedKey) || localStorage.getItem(HOME_WIDGET_SETTINGS_KEY)
        if (!raw) return
        const parsed = JSON.parse(raw)
        if (!parsed || typeof parsed !== 'object') return
        homeWidgetSettings.value = {
            showRecent: typeof (parsed as any).showRecent === 'boolean' ? (parsed as any).showRecent : true,
            showFavorites: typeof (parsed as any).showFavorites === 'boolean' ? (parsed as any).showFavorites : true
        }
        if (!localStorage.getItem(namespacedKey)) {
            localStorage.setItem(namespacedKey, JSON.stringify(homeWidgetSettings.value))
            localStorage.removeItem(HOME_WIDGET_SETTINGS_KEY)
        }
    } catch {
    }
}

watch(homeWidgetSettings, (val) => {
    localStorage.setItem(getHomeWidgetSettingsStorageKey(), JSON.stringify(val))
}, { deep: true })

watch(() => homeWidgetSettings.value.showFavorites, (enabled) => {
    if (!enabled) return
    if (activeTab.value !== 'my') return
    if (favorites.value.length > 0) return
    fetchFavorites()
})

watch(() => homeWidgetSettings.value.showRecent, (enabled) => {
    if (!enabled) return
    if (activeTab.value !== 'my') return
    if (recentDocs.value.length > 0) return
    loadRecentDocs()
})

const upsertKb = ref<KbUpsertModel>({
    color: colors[0]?.value,
    visibility: 'private'
})

const memberKb = ref<Partial<KbSummary>>({})

const confirmOptions = ref({
    title: '',
    message: '',
    type: 'info' as 'info' | 'danger',
    hideCancel: false,
    onConfirm: () => { }
})

const searchFocused = ref(false)
const showSearchModal = ref(false)

const sortKbs = () => {
    kbs.value.sort((a, b) => {
        // 1. Pinned first
        if (a.isPinned !== b.isPinned) {
            return a.isPinned ? -1 : 1
        }

        if (a.isPinned) {
            // 2. Both pinned: sort by pinnedAt desc
            if (a.pinnedAt && b.pinnedAt) {
                return new Date(b.pinnedAt).getTime() - new Date(a.pinnedAt).getTime()
            }
            return 0
        }

        // 3. Both unpinned: sort by sortOrder asc, then createdAt desc
        const order1 = a.sortOrder ?? Number.MAX_SAFE_INTEGER
        const order2 = b.sortOrder ?? Number.MAX_SAFE_INTEGER
        if (order1 !== order2) {
            return order1 - order2
        }

        if (a.createdAt && b.createdAt) {
            return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        }
        return 0
    })
}

const filteredKbs = computed(() => {
    if (searchType.value !== 'kb') return kbs.value
    if (!searchQuery.value.trim()) return kbs.value
    const query = searchQuery.value.toLowerCase()
    return kbs.value.filter(kb =>
        kb.title.toLowerCase().includes(query) ||
        (kb.description && kb.description.toLowerCase().includes(query))
    )
})

const myKbs = computed(() => {
    return filteredKbs.value.filter(kb => kb.role === 'OWNER')
})

const sharedKbs = computed(() => {
    return filteredKbs.value.filter(kb => kb.role !== 'OWNER')
})

const docQuery = computed(() => {
    if (searchType.value !== 'doc') return ''
    return searchQuery.value.trim().toLowerCase()
})

const filteredRecentDocs = computed(() => {
    const q = docQuery.value
    if (!q) return recentDocs.value
    return recentDocs.value.filter(d =>
        d.docName.toLowerCase().includes(q) ||
        (d.kbTitle && d.kbTitle.toLowerCase().includes(q))
    )
})

const recentDocsForHome = computed(() => {
    return filteredRecentDocs.value.slice(0, 10)
})

const homeWidgetGridClass = computed(() => {
    const showRecent = homeWidgetSettings.value.showRecent
    const showFavorites = homeWidgetSettings.value.showFavorites
    return showRecent && showFavorites ? 'grid grid-cols-1 gap-6 lg:grid-cols-2' : 'grid grid-cols-1 gap-6'
})

const filteredFavorites = computed(() => {
    const q = searchQuery.value.trim().toLowerCase()
    if (!q) return favorites.value
    return favorites.value.filter((f: any) => {
        const name = String(f?.name || '').toLowerCase()
        const kbTitle = String(f?.kbTitle || '').toLowerCase()
        return name.includes(q) || kbTitle.includes(q)
    })
})

const favoritesForHome = computed(() => {
    const q = docQuery.value
    const list = q ? filteredFavorites.value : favorites.value
    return list.slice(0, 8)
})

const searchPlaceholder = computed(() => {
    if (activeTab.value === 'favorites') return t('kb.searchFavorites')
    if (activeTab.value === 'recent') return t('kb.searchRecent')
    if (searchType.value === 'kb') return t('kb.searchKb')
    return t('kb.searchAll')
})

const shouldHideCreateCard = computed(() => {
    return activeTab.value === 'my' && searchType.value === 'kb' && !!searchQuery.value.trim()
})

const handleKbClick = (kb: KbSummary) => {
    if (kb.role === 'VIEWER') {
        router.push({ name: 'PublicView', params: { kbId: kb.id } })
    } else {
        router.push({ name: 'Editor', params: { kbId: kb.id } })
    }
}

const openPublicView = (kbId: number) => {
    router.push({ name: 'PublicView', params: { kbId } })
}

const RECENT_DOCS_KEY = 'recentDocs'

const loadRecentDocs = () => {
    try {
        const raw = localStorage.getItem(RECENT_DOCS_KEY)
        if (!raw) {
            recentDocs.value = []
            return
        }
        const parsed = JSON.parse(raw)
        if (!Array.isArray(parsed)) {
            recentDocs.value = []
            return
        }
        recentDocs.value = parsed
            .filter((x: any) => x && typeof x === 'object')
            .map((x: any) => ({
                kbId: Number(x.kbId),
                docId: Number(x.docId),
                docName: String(x.docName || ''),
                kbTitle: String(x.kbTitle || ''),
                lastAccessed: Number(x.lastAccessed || 0),
                mode: (x.mode === 'edit' ? 'edit' : 'view') as 'view' | 'edit'
            }))
            .filter(x => Number.isFinite(x.kbId) && Number.isFinite(x.docId) && !!x.docName)
            .sort((a, b) => b.lastAccessed - a.lastAccessed)
            .slice(0, 30)
    } catch {
        recentDocs.value = []
    }
}

const clearRecentDocs = () => {
    recentDocs.value = []
    localStorage.removeItem(RECENT_DOCS_KEY)
}

const openRecentDoc = (doc: RecentDoc) => {
    const name = doc.mode === 'edit' ? 'Editor' : 'PublicView'
    router.push({ name, params: { kbId: doc.kbId, docId: doc.docId } })
}

const fetchKbs = async (silent = false) => {
    if (!silent) loading.value = true
    try {
        const data: any = await getKbList()
        kbs.value = data.map((item: any) => ({
            ...item,
            icon: item.icon ?? undefined
        }))
    } catch (error) {
        console.error('Fetch KBs failed:', error)
    } finally {
        if (!silent) loading.value = false
    }
}

const fetchFavorites = async () => {
    favLoading.value = true
    try {
        const data: any = await getMyFavorites()
        favorites.value = data
    } catch (error) {
        console.error('Fetch favorites failed:', error)
    } finally {
        favLoading.value = false
    }
}

const fetchConfigs = async () => {
    try {
        const res: any = await getFrontendConfigs()
        frontendConfigs.value = res
    } catch (error) {
        console.error('Fetch frontend configs failed:', error)
    }
}

const handleUnfavorite = async (docId: number) => {
    try {
        await removeFavorite(docId)
        message.success(t('kb.unfavoriteSuccess'))
        fetchFavorites()
    } catch (err) {
        console.error('Unfavorite failed:', err)
    }
}

const handleFavoriteClick = (fav: any) => {
    router.push({ name: 'PublicView', params: { kbId: fav.kbId, docId: fav.id } })
}

watch(activeTab, (newTab, oldTab) => {
    if (newTab === 'favorites') {
        searchType.value = 'doc'
        fetchFavorites()
        document.title = `HelloDoc - ${t('kb.titleFavorites')}`
        return
    }

    if (newTab === 'recent') {
        searchType.value = 'doc'
        loadRecentDocs()
        document.title = `HelloDoc - ${t('kb.titleRecent')}`
        return
    }

    if (newTab === 'my') {
        searchType.value = 'doc'
        if (homeWidgetSettings.value.showRecent) loadRecentDocs()
        if (homeWidgetSettings.value.showFavorites) fetchFavorites()
    }
    if (newTab === 'shared') {
        searchType.value = 'kb'
    }
    if (oldTab === 'favorites' && kbs.value.length === 0) fetchKbs()

    document.title = newTab === 'shared' ? `HelloDoc - ${t('kb.titleShared')}` : `HelloDoc - ${t('kb.titleMy')}`
})

const resetUpsertKb = () => {
    upsertKb.value = {
        title: '',
        description: '',
        color: colors[0]?.value,
        visibility: 'private'
    }
}

const openCreateModal = () => {
    resetUpsertKb()
    showCreateModal.value = true
}

const openEditModal = (kb: KbSummary) => {
    upsertKb.value = {
        ...kb,
        icon: kb.icon ?? undefined
    }
    showEditModal.value = true
}

const handleCreate = async () => {
    if (!upsertKb.value.title) {
        message.warning(t('kb.inputKbName'))
        return
    }
    submitting.value = true
    try {
        await createKb({
            title: upsertKb.value.title,
            description: upsertKb.value.description,
            color: upsertKb.value.color,
            icon: upsertKb.value.icon,
            visibility: upsertKb.value.visibility
        })
        showCreateModal.value = false
        resetUpsertKb()
        fetchKbs()
        message.success(t('kb.createSuccess'))
    } catch (error) {
        console.error('Create failed:', error)
    } finally {
        submitting.value = false
    }
}

const handleUpdate = async () => {
    if (!upsertKb.value.id) return
    if (!upsertKb.value.title) {
        message.warning(t('kb.inputKbName'))
        return
    }
    submitting.value = true
    try {
        await updateKb(upsertKb.value.id, {
            title: upsertKb.value.title,
            description: upsertKb.value.description,
            color: upsertKb.value.color,
            icon: upsertKb.value.icon,
            visibility: upsertKb.value.visibility
        })
        showEditModal.value = false
        resetUpsertKb()
        fetchKbs()
        message.success(t('kb.updateSuccess'))
    } catch (error) {
        console.error('Update failed:', error)
    } finally {
        submitting.value = false
    }
}

const handleDelete = (id: number) => {
    confirmOptions.value = {
        title: t('kb.confirmDeleteTitle'),
        message: t('kb.confirmDeleteMessage'),
        type: 'danger',
        hideCancel: false,
        onConfirm: async () => {
            try {
                await deleteKb(id)
                fetchKbs()
                showConfirmModal.value = false
            } catch (error) {
                console.error('Delete failed:', error)
            }
        }
    }
    showConfirmModal.value = true
}

const handleLeave = (kb: KbSummary) => {
    confirmOptions.value = {
        title: t('kb.confirmLeaveTitle'),
        message: t('kb.confirmLeaveMessage', { title: kb.title }),
        type: 'danger',
        hideCancel: false,
        onConfirm: async () => {
            showConfirmModal.value = false
            // Optimistic update: remove target KB from local list immediately
            const originalKbs = [...kbs.value]
            kbs.value = kbs.value.filter(item => item.id !== kb.id)
            try {
                await leaveKbMember(kb.id)
                message.success(t('kb.leaveSuccess'))
                // Silent sync from backend
                fetchKbs(true)
            } catch (error) {
                console.error('Leave KB failed:', error)
                // Revert on failure
                kbs.value = originalKbs
                message.error(t('common.operationFailed'))
            }
        }
    }
    showConfirmModal.value = true
}

const handlePin = async (kb: KbSummary) => {
    const originalPinned = kb.isPinned
    const originalPinnedAt = kb.pinnedAt

    // Optimistic UI update
    kb.isPinned = !originalPinned
    if (kb.isPinned) {
        kb.pinnedAt = new Date().toISOString()
    } else {
        kb.pinnedAt = undefined
    }
    sortKbs()

    try {
        await pinKb(kb.id, !originalPinned)
        // Refresh from backend to sync final state and metadata silently
        fetchKbs(true)
    } catch (error) {
        console.error('Pin failed:', error)
        // Revert local state on failure
        kb.isPinned = originalPinned
        kb.pinnedAt = originalPinnedAt
        sortKbs()
        message.error(t('common.operationFailed'))
    }
}

const openMemberModal = (kb: KbSummary) => {
    memberKb.value = { id: kb.id, title: kb.title }
    showMemberModal.value = true
}

// User Context
const nickname = ref(localStorage.getItem('nickname') || 'User')
const avatar = ref(localStorage.getItem('avatar') || '')
const userRole = ref(localStorage.getItem('userRole') || 'user')

const handleLogout = () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('nickname')
    localStorage.removeItem('avatar')
    localStorage.removeItem('userRole')
    localStorage.removeItem('email')
    localStorage.removeItem('phone')
    localStorage.removeItem('username')
    localStorage.removeItem('kbViewMode')
    localStorage.removeItem('recentDocs')
    router.push({ name: 'Login' })
}

// Profile Management
const showUserDropdown = ref(false)
const showProfileModal = ref(false)
const showChangePasswordModal = ref(false)

const fetchProfile = async () => {
    try {
        const data: any = await getMe()
        nickname.value = data.nickname
        avatar.value = data.avatar
        userRole.value = data.role
        if (data.username) localStorage.setItem('username', data.username || '')
        localStorage.setItem('nickname', data.nickname || '')
        localStorage.setItem('avatar', data.avatar || '')
        localStorage.setItem('userRole', data.role || 'user')
        localStorage.setItem('email', data.email || '')
        localStorage.setItem('phone', data.phone || '')
        if (data.languageMode) {
            setLocaleModeLocal(data.languageMode)
        }
    } catch (error) {
        console.error('Fetch profile failed:', error)
    }
}

const openProfileModal = () => {
    showProfileModal.value = true
    showUserDropdown.value = false
}

const handleDocumentClick = (event: MouseEvent) => {
    const target = event.target as HTMLElement | null
    if (!target) return
    if (showUserDropdown.value && !target.closest('.dropdown-toggle') && !target.closest('.sidebar-user-menu')) {
        showUserDropdown.value = false
    }
}

const handleProfileUpdated = (payload: { nickname: string; avatar: string; email: string; phone: string }) => {
    nickname.value = payload.nickname || nickname.value
    avatar.value = payload.avatar || avatar.value
    if (payload.nickname != null) localStorage.setItem('nickname', payload.nickname || '')
    if (payload.avatar != null) localStorage.setItem('avatar', payload.avatar || '')
    if (payload.email != null) localStorage.setItem('email', payload.email || '')
    if (payload.phone != null) localStorage.setItem('phone', payload.phone || '')
}

onMounted(() => {
    document.addEventListener('click', handleDocumentClick)
    loadHomeWidgetSettings()
    fetchKbs()
    if (activeTab.value === 'favorites' || (activeTab.value === 'my' && homeWidgetSettings.value.showFavorites)) {
        fetchFavorites()
    }
    if (activeTab.value === 'recent' || (activeTab.value === 'my' && homeWidgetSettings.value.showRecent)) {
        loadRecentDocs()
    }
    fetchConfigs()
    fetchProfile()
    document.title = activeTab.value === 'favorites'
        ? `HelloDoc - ${t('kb.titleFavorites')}`
        : activeTab.value === 'recent'
            ? `HelloDoc - ${t('kb.titleRecent')}`
            : activeTab.value === 'shared'
                ? `HelloDoc - ${t('kb.titleShared')}`
                : `HelloDoc - ${t('kb.titleMy')}`
})

onUnmounted(() => {
    document.removeEventListener('click', handleDocumentClick)
})

const handleDocSelected = (docId: number, kbId: number) => {
    router.push({ name: 'Editor', params: { kbId, docId } })
}

const handleKbSelected = (kbId: number) => {
    router.push({ name: 'PublicView', params: { kbId } })
}
</script>

<template>
    <!-- Top Level Container -->
    <div :class="[
        'h-full bg-gray-50 dark:bg-gray-900 transition-all duration-300 flex flex-col',
        navStyle === 'left' ? 'overflow-hidden' : 'overflow-y-auto'
    ]">
        <div v-if="navStyle === 'left'" class="md:hidden h-14 px-4 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 flex items-center justify-between">
            <button type="button" @click="showMobileSidebar = true"
                class="inline-flex items-center justify-center h-9 w-9 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition focus:outline-none"
                :title="t('nav.menu')">
                <svg class="h-5 w-5 text-gray-600 dark:text-gray-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
                </svg>
            </button>
            <div class="flex items-center space-x-2 min-w-0">
                <img src="../assets/logo.svg" alt="HelloDoc Logo" class="h-8 w-8" />
                <span class="text-xl font-bold text-gray-900 dark:text-white">HelloDoc</span>
            </div>
            <button v-if="isSearchEnabled" type="button" @click="showSearchModal = true"
                class="inline-flex items-center justify-center h-9 w-9 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition focus:outline-none"
                :title="searchPlaceholder">
                <svg class="h-5 w-5 text-gray-500 dark:text-gray-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
            </button>
            <div v-else class="h-9 w-9"></div>
        </div>

        <div v-if="navStyle === 'left' && showMobileSidebar" class="fixed inset-0 bg-black/40 z-40 md:hidden"
            @click="showMobileSidebar = false"></div>

        <!-- Main Body Area -->
        <div :class="[navStyle === 'left' ? 'flex-1 flex overflow-hidden' : 'flex-1']">
            <!-- Left Sidebar Navigation (Only nav items) -->
            <aside v-if="navStyle === 'left'" :class="[
                'w-64 flex-shrink-0 bg-white dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 flex-col shadow-sm transition-colors duration-300',
                'fixed md:static inset-y-0 left-0 z-50 md:z-auto transform transition-transform md:translate-x-0',
                showMobileSidebar ? 'translate-x-0 flex' : '-translate-x-full md:flex'
            ]">
                <div class="px-6 h-20 flex items-center justify-between">
                    <div class="flex items-center space-x-2 min-w-0">
                        <img src="../assets/logo.svg" alt="HelloDoc Logo" class="h-8 w-8" />
                        <span class="text-xl font-bold text-gray-900 dark:text-white">HelloDoc</span>
                    </div>
                    <button v-if="isSearchEnabled" type="button" @click="showSearchModal = true"
                        class="inline-flex items-center justify-center h-9 w-9 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition focus:outline-none"
                        :title="searchPlaceholder">
                        <svg class="h-5 w-5 text-gray-500 dark:text-gray-300" fill="none" viewBox="0 0 24 24"
                            stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                        </svg>
                    </button>
                </div>
                <!-- Navigation Items -->
                <nav class="flex-1 px-4 pt-2 pb-6 space-y-2 overflow-y-auto">
                    <button v-for="tab in [
                        { id: 'my', name: t('nav.home'), icon: 'M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z' },
                        { id: 'shared', name: t('nav.shared'), icon: 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z' },
                        { id: 'favorites', name: t('nav.favorites'), icon: 'M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.175 0l-3.976 2.888c-.783.57-1.837-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.382-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z' },
                        { id: 'recent', name: t('nav.recent'), icon: 'M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z' },
                    ]" :key="tab.id" @click="navigateToTab(tab.id as any); showMobileSidebar = false"
                        :class="activeTab === tab.id
                            ? 'bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400 font-bold shadow-sm'
                            : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'"
                        class="w-full flex items-center px-4 py-3 text-sm font-medium rounded-2xl transition-all duration-200 group">
                        <svg class="mr-3 h-5 w-5 flex-shrink-0 transition-transform group-hover:scale-110"
                            :class="activeTab === tab.id ? 'text-blue-600 dark:text-blue-400' : 'text-gray-400 group-hover:text-gray-500'"
                            fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" :d="tab.icon" />
                        </svg>
                        {{ tab.name }}
                        <div v-if="activeTab === tab.id" class="ml-auto w-1.5 h-1.5 rounded-full bg-blue-600 dark:bg-blue-400"></div>
                    </button>
                </nav>

                <!-- Sidebar Footer -->
                <div class="p-4 border-t border-gray-100 dark:border-gray-700 space-y-3">
                    <div class="relative">
                        <button @click="showUserDropdown = !showUserDropdown"
                            class="dropdown-toggle w-full flex items-center justify-between rounded-xl px-2 py-2 hover:bg-gray-100 dark:hover:bg-gray-700 transition focus:outline-none">
                            <div class="flex items-center gap-2 min-w-0">
                                <div
                                    class="h-8 w-8 rounded-full overflow-hidden bg-gray-200 dark:bg-gray-600 border border-gray-300 dark:border-gray-500 flex items-center justify-center flex-shrink-0">
                                    <img v-if="avatar" :src="avatar" class="h-full w-full object-cover" />
                                    <span v-else class="text-xs font-bold text-gray-600">{{ nickname.charAt(0).toUpperCase() }}</span>
                                </div>
                                <span class="text-sm font-medium text-gray-700 dark:text-gray-200 truncate">{{ nickname }}</span>
                            </div>
                            <svg class="h-4 w-4 text-gray-400 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M19 9l-7 7-7-7" />
                            </svg>
                        </button>

                        <div v-if="showUserDropdown"
                            class="sidebar-user-menu absolute bottom-full left-0 right-0 mb-2 bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-100 dark:border-gray-700 py-1 z-50">
                            <button @click="openProfileModal(); showUserDropdown = false"
                                class="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 flex items-center space-x-2">
                                <svg class="h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                                </svg>
                                <span>{{ t('nav.profile') }}</span>
                            </button>
                            <button @click="showChangePasswordModal = true; showUserDropdown = false"
                                class="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 flex items-center space-x-2">
                                <svg class="h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                                </svg>
                                <span>{{ t('nav.changePassword') }}</span>
                            </button>
                            <button v-if="userRole === 'admin'" @click="router.push('/admin'); showUserDropdown = false"
                                class="w-full text-left px-4 py-2 text-sm text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 flex items-center space-x-2 font-semibold">
                                <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                </svg>
                                <span>{{ t('nav.admin') }}</span>
                            </button>
                            <button @click="showSystemSettings = true; showUserDropdown = false"
                                class="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 flex items-center space-x-2">
                                <svg class="h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                </svg>
                                <span>{{ t('nav.settings') }}</span>
                            </button>
                            <button @click="handleLogout(); showUserDropdown = false"
                                class="w-full text-left px-4 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 flex items-center space-x-2">
                                <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                                </svg>
                                <span>{{ t('nav.logout') }}</span>
                            </button>
                        </div>
                    </div>
                </div>
            </aside>

            <!-- Main Content Area -->
            <div :class="[navStyle === 'left' ? 'flex-1 flex flex-col min-w-0 h-full overflow-hidden' : 'flex-1']" class="bg-gray-50 dark:bg-gray-900">
                <!-- Standard Navbar for Top Nav style -->
                <KnowledgeBaseNavbar v-if="navStyle !== 'left'" :activeTab="activeTab" v-model:searchType="searchType" v-model:searchQuery="searchQuery"
                    :searchPlaceholder="searchPlaceholder" v-model:searchFocused="searchFocused" :nickname="nickname"
                    :avatar="avatar" :userRole="userRole" :searchEnabled="isSearchEnabled" v-model:showUserDropdown="showUserDropdown"
                    :navStyle="navStyle"
                    @navigate="navigateToTab"
                    @open-profile="openProfileModal" @open-change-password="showChangePasswordModal = true"
                    @open-system-settings="showSystemSettings = true" @logout="handleLogout" @go-admin="router.push('/admin')"
                    @open-search-modal="showSearchModal = true" />

                <div class="flex-1 overflow-y-auto scroll-smooth">
                    <KnowledgeBaseContent :activeTab="activeTab" :navStyle="navStyle" v-model:viewMode="viewMode" :loading="loading" :favLoading="favLoading"
                        :favorites="favorites" :filteredFavorites="filteredFavorites" :favoritesForHome="favoritesForHome"
                        :filteredRecentDocs="filteredRecentDocs" :recentDocsForHome="recentDocsForHome" :myKbs="myKbs"
                        :sharedKbs="sharedKbs" :homeWidgetSettings="homeWidgetSettings" :homeWidgetGridClass="homeWidgetGridClass"
                        :shouldHideCreateCard="shouldHideCreateCard" :navigateToTab="navigateToTab" :clearRecentDocs="clearRecentDocs" :openRecentDoc="openRecentDoc"
                        :handleFavoriteClick="handleFavoriteClick" :handleUnfavorite="handleUnfavorite" :openCreateModal="openCreateModal"
                        :handleKbClick="handleKbClick" :openPublicView="openPublicView" :openMemberModal="openMemberModal"
                        :openEditModal="openEditModal" :handlePin="handlePin" :handleDelete="handleDelete" :handleLeave="handleLeave" />
                </div>
            </div>
        </div>

        <button v-if="navStyle === 'left' && activeTab === 'my'" @click="openCreateModal()"
            class="fixed right-6 bottom-6 z-30 h-12 w-12 rounded-xl bg-blue-600 hover:bg-blue-700 text-white inline-flex items-center justify-center transition-all active:scale-95"
            :title="t('kb.createKb')">
            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
        </button>

        <KbUpsertModal v-model:show="showCreateModal" v-model:kb="upsertKb" mode="create" :colors="colors"
            :submitting="submitting" @save="handleCreate" />

        <KbUpsertModal v-model:show="showEditModal" v-model:kb="upsertKb" mode="edit" :colors="colors"
            :submitting="submitting" @save="handleUpdate" />

        <KbMemberModal v-model:show="showMemberModal" :kb-id="memberKb.id" :kb-title="memberKb.title" />

        <!-- Confirm Dialog -->
        <ConfirmDialog :show="showConfirmModal" :title="confirmOptions.title" :message="confirmOptions.message"
            :type="confirmOptions.type" :hide-cancel="confirmOptions.hideCancel" @confirm="confirmOptions.onConfirm"
            @cancel="showConfirmModal = false" />

        <UserProfileModal v-model:show="showProfileModal" :nickname="nickname" :avatar="avatar"
            @updated="handleProfileUpdated" />

        <ChangePasswordModal :show="showChangePasswordModal" @close="showChangePasswordModal = false" />
        <SystemSettingsModal :show="showSystemSettings" v-model:showHomeRecent="homeWidgetSettings.showRecent"
            v-model:showHomeFavorites="homeWidgetSettings.showFavorites" @close="showSystemSettings = false" />

        <SearchModal v-model:show="showSearchModal" :initialKbResults="kbs"
            @select-doc="handleDocSelected" @select-kb="handleKbSelected" />
    </div>
</template>

<style scoped>
.text-truncate {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
</style>
