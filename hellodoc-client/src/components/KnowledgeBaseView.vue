<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue'
import axios from 'axios'
import { useRouter, useRoute } from 'vue-router'
import { getPublicKbDetail, getPublicDocuments, getPublicDocumentDetail, getAuthDocuments, getAuthDocumentDetail, getKbDetail, exportDocument, searchInKb, searchPublicInKb } from '../api/kb'
import { getFrontendConfigs } from '../api/config'
import { toggleFavorite as apiToggleFavorite, checkIsFavorite } from '../api/favorite'
import { getMe } from '../api/user'
import KbCommentSection from './knowledge-base/KbCommentSection.vue'
import SearchResultList from './SearchResultList.vue'
import DocumentTreeNodeLabel from './shared/DocumentTreeNodeLabel.vue'
import type { SearchResult } from './SearchResultList.vue'
import { message } from '../utils/message'
import { recordRecentDoc } from '../utils/recentDocs'
import { buildOrderedDocuments, expandAncestorFolders } from '../utils/documentTree'
import { useTheme } from '../composables/useTheme'
import { getIconStyle as utilGetIconStyle, getIconBgStyle as utilGetIconBgStyle } from '../utils/color'
import { stripMarkdownToc } from '../utils/markdown'
import VisualEditor from './editor/VisualEditor.vue'
import * as Icons from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
    kbId: number
    docId?: number
}>()

const router = useRouter()
const route = useRoute()
const { isDark, toggleTheme } = useTheme()
const { t } = useI18n()

// Standalone mode: hides sidebar if 'standalone=true' is in query
const isStandalone = computed(() => route.query.standalone === 'true')
const isMobile = ref(window.innerWidth < 768)
const sidebarWidth = ref(288)
const isResizingSidebar = ref(false)
const shareStandalone = ref(false)
const SIDEBAR_WIDTH_STORAGE_KEY = 'knowledgeBaseViewSidebarWidth'
const MIN_SIDEBAR_WIDTH = 200
const MAX_SIDEBAR_WIDTH = 600

if (typeof window !== 'undefined') {
    const storedWidth = localStorage.getItem(SIDEBAR_WIDTH_STORAGE_KEY)
    if (storedWidth) {
        const parsed = parseInt(storedWidth)
        if (!Number.isNaN(parsed) && parsed >= MIN_SIDEBAR_WIDTH && parsed <= MAX_SIDEBAR_WIDTH) {
            sidebarWidth.value = parsed
        }
    }
}

const kbDetail = ref<any>(null)
const documents = ref<any[]>([])
const currentDoc = ref<any>(null)
const loading = ref(false)
const docLoading = ref(false)
const searchQuery = ref('')
const searchInputRef = ref<HTMLInputElement | null>(null)
const searchResults = ref<SearchResult[]>([])
const searchLoading = ref(false)
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null
let searchAbortController: AbortController | null = null
const isSearchExpanded = ref<boolean>(false)
const expandedFolders = ref<Set<number>>(new Set())

const saveExpandedFolders = () => {
    if (props.kbId) {
        try {
            sessionStorage.setItem(`kb_expanded_${props.kbId}`, JSON.stringify(Array.from(expandedFolders.value)))
        } catch (e) {}
    }
}

const loadExpandedFolders = () => {
    if (props.kbId) {
        try {
            const saved = sessionStorage.getItem(`kb_expanded_${props.kbId}`)
            if (saved) {
                const arr = JSON.parse(saved)
                if (Array.isArray(arr)) {
                    expandedFolders.value = new Set(arr.map(Number))
                }
            }
        } catch (e) {}
    }
}

watch(() => props.kbId, () => {
    loadExpandedFolders()
}, { immediate: true })

watch(expandedFolders, () => {
    saveExpandedFolders()
}, { deep: true })
const shareContainerRef = ref<HTMLElement | null>(null)
const error = ref<{ code: number, message: string } | null>(null)
const copied = ref(false)
const isFavorite = ref(false)
const isMac = computed(() => /Mac|iPhone|iPod|iPad/.test(navigator.platform))




const hasH1Title = computed(() => {
    if (!currentDoc.value?.content || !currentDoc.value?.name) return false;
    const lines = currentDoc.value.content.split('\n');
    for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) continue;
        if (trimmed.startsWith('# ')) {
            // Checks if any H1 exists
            return true;
        }
        return false;
    }
    return false;
});

const getIconStyle = (color?: string) => utilGetIconStyle(color, isDark.value)
const getIconBgStyle = (color?: string) => utilGetIconBgStyle(color, isDark.value)

const renderedContent = computed(() => {
    if (!currentDoc.value) return '';
    const cleanedContent = stripMarkdownToc(currentDoc.value.content || '')
    // 如果没有 H1 标题，把文章的 name 拼接为 H1 展示
    if (!hasH1Title.value && currentDoc.value.name) {
        return `# ${currentDoc.value.name}\n\n${cleanedContent}`;
    }
    return cleanedContent;
});

const isLoggedIn = computed(() => !!localStorage.getItem('accessToken'))
const currentUserId = ref<number | null>(null)
const currentUser = ref<any>(null)
const frontendConfigs = ref<Record<string, string>>({})

const isGuestbookEnabled = computed(() => {
    return frontendConfigs.value['app.enable_guestbook'] !== 'false'
})

const canEdit = computed(() => {
    if (!isLoggedIn.value || !currentDoc.value || !kbDetail.value) return false
    // 1. Check if user is OWNER or ADMIN or EDITOR of the KB
    if (['OWNER', 'ADMIN', 'EDITOR'].includes(kbDetail.value.role)) return true
    // 2. Check if user is the author of the document
    if (currentDoc.value.authorId && currentDoc.value.authorId === currentUserId.value) return true
    return false
})

const handleEdit = () => {
    if (!currentDoc.value) return
    router.push({ name: 'Editor', params: { kbId: props.kbId, docId: currentDoc.value.id } })
}

const handleCatalogResize = () => {
    isMobile.value = window.innerWidth < 768
}

const handleSidebarResize = (event: MouseEvent) => {
    if (!isResizingSidebar.value) return
    let newWidth = event.clientX
    if (newWidth < MIN_SIDEBAR_WIDTH) newWidth = MIN_SIDEBAR_WIDTH
    if (newWidth > MAX_SIDEBAR_WIDTH) newWidth = MAX_SIDEBAR_WIDTH
    sidebarWidth.value = newWidth
}

const stopSidebarResize = () => {
    if (!isResizingSidebar.value) return
    isResizingSidebar.value = false
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    localStorage.setItem(SIDEBAR_WIDTH_STORAGE_KEY, sidebarWidth.value.toString())
    window.removeEventListener('mousemove', handleSidebarResize)
    window.removeEventListener('mouseup', stopSidebarResize)
}

const startSidebarResize = () => {
    isResizingSidebar.value = true
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
    window.addEventListener('mousemove', handleSidebarResize)
    window.addEventListener('mouseup', stopSidebarResize)
}


const fetchConfigs = async () => {
    try {
        const res: any = await getFrontendConfigs()
        frontendConfigs.value = res
    } catch (e) {
        console.error('Fetch frontend configs failed:', e)
    }
}

const fetchKbData = async () => {
    loading.value = true
    try {
        // Try fetching auth KB detail if token exists to get role info
        const token = localStorage.getItem('accessToken')
        const kbPromise = token ? getKbDetail(props.kbId) : getPublicKbDetail(props.kbId)

        const docsPromise = token ? getAuthDocuments(props.kbId) : getPublicDocuments(props.kbId)

        const [kbRes, docsRes]: [any, any] = await Promise.all([
            kbPromise,
            docsPromise
        ])
        kbDetail.value = kbRes

        const filteredDocs = docsRes.filter((d: any) => d.type === 'folder' || d.status === 'published')
        const hasDoc = new Set<number>()
        const parentMap = new Map()

        filteredDocs.forEach((d: any) => {
            parentMap.set(d.id, d.parentId)
        })

        const addParent = (id: number | null) => {
            if (id === null || hasDoc.has(id)) return
            hasDoc.add(id)
            const parentId = parentMap.get(id)
            if (parentId !== undefined && parentId !== null) {
                addParent(parentId)
            }
        }

        filteredDocs.forEach((d: any) => {
            if (d.type !== 'folder') {
                addParent(d.parentId)
            }
        })

        documents.value = filteredDocs.filter((d: any) => d.type !== 'folder' || hasDoc.has(d.id))

        // Handle cover or initial document
        if (!props.docId) {
            const coverDoc = documents.value.find(d => d.isCover)
            if (coverDoc) {
                router.replace({ name: 'PublicView', params: { kbId: props.kbId, docId: coverDoc.id }, query: route.query })
            }
        } else {
            // Check if target doc exists in public list
            const doc = documents.value.find(d => d.id === props.docId)
            if (doc) {
                expandAncestorFolders(expandedFolders.value, documents.value, doc.id)
            }
        }
    } catch (err: any) {
        console.error('Fetch public KB failed:', err)
        if (err.response?.data) {
            error.value = {
                code: err.response.data.code,
                message: err.response.data.message
            }
        } else {
            error.value = {
                code: 500,
                message: t('kbView.error.connectionFailed')
            }
        }
    } finally {
        loading.value = false
    }
}

const applyDocDetail = (id: number, res: any) => {
    currentDoc.value = res
    document.title = `${res.name} - ${kbDetail.value?.title || 'HelloDoc'}`
    recordRecentDoc({
        kbId: props.kbId,
        docId: id,
        docName: String(res.name || ''),
        kbTitle: kbDetail.value?.title,
        mode: 'view'
    })

    if (isLoggedIn.value) {
        checkIsFavorite(id).then((data: any) => isFavorite.value = data).catch(() => isFavorite.value = false)
    }
}

const fetchDocDetail = async (id: number) => {
    docLoading.value = true
    const token = localStorage.getItem('accessToken')
    try {
        if (token) {
            try {
                const authRes: any = await getAuthDocumentDetail(props.kbId, id)
                applyDocDetail(id, authRes)
                return
            } catch (authError: any) {
                const status = authError?.response?.status
                if (status !== 403 && status !== 404) {
                    throw authError
                }
            }
        }

        const publicRes: any = await getPublicDocumentDetail(props.kbId, id)
        applyDocDetail(id, publicRes)
    } catch (error: any) {
        console.error('Fetch document detail failed:', error)
    } finally {
        docLoading.value = false
    }
}

const formatDate = (dateStr: string, fallbackStr?: string) => {
    const finalStr = dateStr || fallbackStr
    if (!finalStr) return '-'
    const date = new Date(finalStr)
    if (isNaN(date.getTime())) return '-'
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    })
}

const orderedDocuments = computed(() => {
    return buildOrderedDocuments(documents.value, expandedFolders.value, searchQuery.value)
})

const toggleFolder = (id: number) => {
    const next = new Set(expandedFolders.value)
    if (next.has(id)) {
        next.delete(id)
    } else {
        next.add(id)
    }
    expandedFolders.value = next
}

const selectDoc = (doc: any) => {
    if (doc.type === 'folder') {
        toggleFolder(doc.id)
    } else {
        router.push({ name: 'PublicView', params: { kbId: props.kbId, docId: doc.id }, query: route.query })
    }
}

// Context Menu Logic
const contextMenu = ref({
    visible: false,
    x: 0,
    y: 0,
    doc: null as any
})

const handleContextMenu = (e: MouseEvent, doc: any) => {
    // Only show for authorized users (OWNER, ADMIN or EDITOR)
    if (!kbDetail.value || !['OWNER', 'ADMIN', 'EDITOR'].includes(kbDetail.value.role)) {
        return
    }

    e.preventDefault()
    contextMenu.value = {
        visible: true,
        x: e.clientX,
        y: e.clientY,
        doc
    }
}

const closeContextMenu = () => {
    contextMenu.value.visible = false
}

const handleExport = async () => {
    if (!contextMenu.value.doc) return
    const doc = contextMenu.value.doc
    try {
        message.info(t('kbView.export.preparing'))
        const res: any = await exportDocument(props.kbId, doc.id)
        // Check if response is blob
        const blob = new Blob([res], { type: 'application/zip' })
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.setAttribute('download', `${doc.name}.zip`)
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
        message.success(t('kbView.export.started'))
    } catch (e) {
        console.error(e)
        message.error(t('kbView.export.failed'))
    }
    closeContextMenu()
}

const handleGlobalClick = (e: MouseEvent) => {
    closeContextMenu()
    if (sharePopover.value && shareContainerRef.value && !shareContainerRef.value.contains(e.target as Node)) {
        sharePopover.value = false
    }
}

const handleToggleFavorite = async () => {
    if (!currentDoc.value) return
    const docId = currentDoc.value.id

    // Optimistic update
    const previousState = isFavorite.value
    isFavorite.value = !previousState

    try {
        await apiToggleFavorite(docId)
        message.success(isFavorite.value ? t('kbView.favorite.added') : t('kbView.favorite.removed'))
    } catch (err) {
        // Rollback on error
        isFavorite.value = previousState
        console.error('Toggle favorite failed:', err)
    }
}

onMounted(() => {
    window.addEventListener('click', handleGlobalClick)
})

// UI State for Share Popover
const sharePopover = ref(false)

const shareUrl = computed(() => {
    // Generate clean URL based on current path
    const url = new URL(window.location.origin + route.path)
    if (shareStandalone.value) {
        url.searchParams.set('standalone', 'true')
    }
    return url.toString()
})
const qrCodeUrl = computed(() => `https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=${encodeURIComponent(shareUrl.value)}`)

const copyToClipboard = async (text: string) => {
    if (navigator.clipboard && window.isSecureContext) {
        try {
            await navigator.clipboard.writeText(text)
            return true
        } catch (err) {
            console.error('Clipboard API copy failed:', err)
        }
    }

    // Fallback to execCommand('copy')
    const textArea = document.createElement('textarea')
    textArea.value = text
    textArea.style.position = 'fixed'
    textArea.style.left = '-999999px'
    textArea.style.top = '-999999px'
    document.body.appendChild(textArea)
    textArea.focus()
    textArea.select()
    try {
        const successful = document.execCommand('copy')
        document.body.removeChild(textArea)
        return successful
    } catch (err) {
        console.error('Fallback copy failed:', err)
        document.body.removeChild(textArea)
        return false
    }
}

const copyLink = async () => {
    const success = await copyToClipboard(shareUrl.value)
    if (success) {
        message.success(t('kbView.clipboard.success'))
        copied.value = true
        setTimeout(() => {
            copied.value = false
        }, 2000)
    } else {
        message.error(t('kbView.clipboard.error'))
    }
}

const getKbIcon = (iconName?: string | null) => {
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

watch(() => props.docId, (newId) => {
    if (newId) {
        fetchDocDetail(newId)
    } else {
        currentDoc.value = null
    }
}, { immediate: true })



// 搜索 debounce 逻辑：输入后 300ms 调用后端搜索 API
watch(searchQuery, (q) => {
    if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
    const trimmed = q.trim()
    if (!trimmed) {
        searchResults.value = []
        searchLoading.value = false
        return
    }
    searchLoading.value = true
    searchDebounceTimer = setTimeout(async () => {
        if (searchAbortController) searchAbortController.abort()
        searchAbortController = new AbortController()

        try {
            const token = localStorage.getItem('accessToken')
            const searchConfig = {
                signal: searchAbortController.signal,
                timeout: 20000 // 恢复为 10s
            }
            const res: any = token
                ? await searchInKb(props.kbId, trimmed, searchConfig)
                : await searchPublicInKb(props.kbId, trimmed, searchConfig)
            searchResults.value = res || []
        } catch (e: any) {
            if (axios.isCancel(e) || e._isCancel) return
            console.error('Search failed:', e)
            searchResults.value = []
        } finally {
            searchLoading.value = false
        }
    }, 300)
})

// 搜索结果选中处理
const handleSearchSelect = (docId: number) => {
    router.push({ name: 'PublicView', params: { kbId: props.kbId, docId }, query: route.query })
}

onUnmounted(() => {
    window.removeEventListener('click', handleGlobalClick)
    if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
    if (searchAbortController) searchAbortController.abort()

    window.removeEventListener('keydown', handleGlobalKeyDown)
    stopSidebarResize()
})

// Sync checkbox with current query param on open
watch(sharePopover, (newVal) => {
    if (newVal) {
        shareStandalone.value = isStandalone.value
    }
})

// 全局快捷键处理
const handleGlobalKeyDown = (e: KeyboardEvent) => {
    // Cmd+K or Ctrl+K or /
    if (((e.metaKey || e.ctrlKey) && e.key === 'k') || (e.key === '/' && document.activeElement?.tagName !== 'INPUT' && document.activeElement?.tagName !== 'TEXTAREA')) {
        e.preventDefault()
        isSearchExpanded.value = true
        nextTick(() => {
            searchInputRef.value?.focus()
        })
    }
}

const reloadPage = () => {
    window.location.reload()
}

onMounted(() => {
    fetchConfigs()
    fetchKbData()

    window.addEventListener('keydown', handleGlobalKeyDown)
    window.addEventListener('resize', handleCatalogResize)
    
    if (isLoggedIn.value) {
        getMe().then((res: any) => {
            currentUserId.value = res.id
            currentUser.value = res
        }).catch(e => console.error('Get me failed', e))
    }
})
</script>

<template>
    <div
        class="fixed inset-0 bg-slate-50 dark:bg-[#161b22] flex flex-col font-sans text-slate-900 dark:text-gray-100 selection:bg-indigo-100 selection:text-indigo-900 overflow-hidden">
        <!-- Error State -->
        <div v-if="error"
            class="fixed inset-0 z-[100] bg-slate-50 dark:bg-[#161b22] flex flex-col items-center justify-center p-6 text-center">
            <div class="max-w-xl w-full">
                <div class="relative mb-16">
                    <!-- Decorative backgrounds -->
                    <div
                        class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-64 h-64 bg-indigo-500/10 blur-[100px] rounded-full">
                    </div>
                    <div
                        class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-48 h-48 bg-purple-500/5 blur-[80px] rounded-full delay-700">
                    </div>

                    <div class="relative flex justify-center">
                        <div
                            class="w-24 h-24 bg-white dark:bg-[#161b22] rounded-3xl shadow-2xl shadow-indigo-100 dark:shadow-none flex items-center justify-center border border-indigo-50 dark:border-gray-700 rotate-12 hover:rotate-0 transition-transform duration-500">
                            <svg xmlns="http://www.w3.org/2000/svg" class="w-12 h-12 text-indigo-600"
                                viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                                <rect width="18" height="11" x="3" y="11" rx="2" ry="2" />
                                <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                            </svg>
                        </div>
                        <div
                            class="absolute -bottom-4 -right-4 w-12 h-12 bg-indigo-50 rounded-2xl flex items-center justify-center text-indigo-400 -rotate-12 border border-white shadow-lg">
                            <svg xmlns="http://www.w3.org/2000/svg" class="w-6 h-6" viewBox="0 0 24 24" fill="none"
                                stroke="currentColor" stroke-width="2">
                                <circle cx="12" cy="12" r="10" />
                                <line x1="12" y1="8" x2="12" y2="12" />
                                <line x1="12" y1="16" x2="12.01" y2="16" />
                            </svg>
                        </div>
                    </div>
                </div>

                <div class="space-y-6">
                    <div
                        class="inline-flex items-center gap-2 px-4 py-1.5 bg-indigo-50 rounded-full text-indigo-600 text-xs font-black uppercase tracking-widest">
                        Access Restricted
                    </div>
                    <h2 class="text-4xl font-black text-slate-900 dark:text-gray-100 tracking-tight leading-tight">
                        {{ t('kbView.error.restrictedTitle') }}
                    </h2>
                    <p class="text-slate-500 text-lg leading-relaxed max-w-md mx-auto">
                        {{ t('kbView.error.restrictedDesc') }}
                    </p>
                    <div class="pt-8 flex flex-col sm:flex-row items-center justify-center gap-4">
                        <button @click="router.push({ name: 'Home' })"
                            class="px-8 py-4 bg-indigo-600 text-white rounded-2xl text-base font-bold shadow-xl shadow-indigo-200 dark:shadow-none hover:bg-indigo-700 hover:scale-[1.02] active:scale-95 transition-all">
                            {{ t('nav.backHome') }}
                        </button>
                        <button @click="reloadPage"
                            class="px-8 py-4 bg-white dark:bg-[#161b22] border border-slate-200 dark:border-gray-700 text-slate-600 dark:text-gray-300 rounded-2xl text-base font-bold hover:bg-slate-50 dark:hover:bg-gray-700 transition-all">
                            {{ t('common.retry') }}
                        </button>
                    </div>
                </div>

                <div class="mt-24 pt-12 border-t border-slate-200/50">
                    <p class="text-[10px] font-black uppercase tracking-[0.3em] text-slate-300">
                        HelloDoc Knowledge Engine
                    </p>
                </div>
            </div>
        </div>


        <!-- Navbar -->
        <nav v-if="!isStandalone && !isMobile"
            class="h-16 bg-white/70 dark:bg-[#161b22]/70 backdrop-blur-xl border-b border-slate-200/50 dark:border-gray-800 flex items-center justify-between px-6 z-40 sticky top-0 transition-all duration-300 shadow-sm shadow-slate-100/50 dark:shadow-none">
            <div class="flex items-center gap-4">
                <template v-if="kbDetail">
                    <div
                        class="kb-icon-shell kb-icon-shell--sm mr-2"
                        :style="getIconBgStyle(kbDetail?.color)">
                        <template v-if="getKbIcon(kbDetail?.icon)">
                            <img v-if="getKbIcon(kbDetail?.icon)?.type === 'image'" :src="String(getKbIcon(kbDetail?.icon)?.value)"
                                class="kb-icon-glyph kb-icon-glyph--sm object-cover rounded" alt="kb-icon" />
                            <component v-else :is="getKbIcon(kbDetail?.icon)?.value" class="kb-icon-glyph kb-icon-glyph--sm"
                                :style="getIconStyle(kbDetail?.color)" />
                        </template>
                        <component v-else :is="(Icons as any).Book" class="kb-icon-glyph kb-icon-glyph--sm"
                            :style="getIconStyle(kbDetail?.color)" />
                    </div>
                </template>
                <div class="flex items-center gap-2">
                    <h1 class="text-lg font-extrabold text-slate-800 dark:text-gray-100 tracking-tight">{{
                        kbDetail?.title || t('kbView.doc.loadingTitle') }}
                    </h1>
                </div>
            </div>

            <div class="flex items-center gap-2">
                <div class="relative flex items-center justify-end">
                    <div :class="[
                        'flex items-center transition-all duration-300 ease-in-out origin-right overflow-hidden',
                        isSearchExpanded || searchQuery.trim() ? 'w-48 lg:w-72' : 'w-0'
                    ]">
                        <input ref="searchInputRef" v-model="searchQuery" type="text"
                            :placeholder="t('kbView.nav.searchPlaceholder', { shortcut: isMac ? '⌘K' : 'Ctrl+K' })"
                            @blur="!searchQuery.trim() && (isSearchExpanded = false)"
                            class="w-full pl-9 pr-4 py-1.5 border border-slate-200 dark:border-gray-700 rounded-full text-sm focus:outline-none focus:border-indigo-400 transition-all bg-slate-50 dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-slate-400 dark:placeholder-slate-500">
                    </div>
                    <button v-if="!isSearchExpanded && !searchQuery.trim()"
                        @click="isSearchExpanded = true; nextTick(() => searchInputRef?.focus())"
                        class="p-2 text-slate-500 dark:text-gray-400 hover:text-indigo-600 transition-colors rounded-full hover:bg-slate-100 dark:hover:bg-gray-800"
                        :title="t('kbView.nav.searchTooltip', { shortcut: isMac ? '⌘K' : 'Ctrl+K' })">
                        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                        </svg>
                    </button>
                    <svg v-else
                        class="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none"
                        fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                    </svg>
                </div>

                <!-- 匿名用户深色模式切换按钮 -->
                <button v-if="!isLoggedIn" @click="toggleTheme"
                    class="p-2 h-9 w-9 flex items-center justify-center text-slate-500 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition-all rounded-full hover:bg-slate-100 dark:hover:bg-gray-800"
                    :title="isDark ? t('kbView.nav.lightMode') : t('kbView.nav.darkMode')">
                    <!-- 太阳图标（深色模式下显示，点击切回亮色） -->
                    <svg v-if="isDark" xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24"
                        fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                        stroke-linejoin="round">
                        <circle cx="12" cy="12" r="5" />
                        <line x1="12" y1="1" x2="12" y2="3" />
                        <line x1="12" y1="21" x2="12" y2="23" />
                        <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
                        <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
                        <line x1="1" y1="12" x2="3" y2="12" />
                        <line x1="21" y1="12" x2="23" y2="12" />
                        <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
                        <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
                    </svg>
                    <!-- 月亮图标（亮色模式下显示，点击切到深色） -->
                    <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none"
                        stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
                    </svg>
                </button>

                <div class="h-4 w-px bg-slate-200 dark:bg-gray-700 mx-1 hidden sm:block"></div>

                <router-link to="/"
                    class="p-2 h-9 w-9 flex items-center justify-center text-slate-500 dark:text-gray-400 hover:text-indigo-600 transition-all rounded-full hover:bg-slate-100 dark:hover:bg-gray-800"
                    :title="t('kbView.nav.homeTooltip')">
                    <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none"
                        stroke="currentColor" stroke-width="2">
                        <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                        <path d="M9 22V12h6v10" />
                    </svg>
                </router-link>

                <button v-if="canEdit" @click="handleEdit"
                    class="p-2 h-9 w-9 flex items-center justify-center text-slate-500 dark:text-gray-400 hover:text-indigo-600 transition-all rounded-full hover:bg-slate-100 dark:hover:bg-gray-800"
                    :title="t('kbView.doc.editTooltip')">
                    <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none"
                        stroke="currentColor" stroke-width="2">
                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                    </svg>
                </button>

                <button v-if="isLoggedIn && currentDoc" @click="handleToggleFavorite"
                    class="p-2 h-9 w-9 flex items-center justify-center transition-all rounded-full hover:bg-slate-100 dark:hover:bg-gray-800 group"
                    :class="isFavorite ? 'text-amber-500 bg-amber-50/30' : 'text-slate-500 dark:text-gray-400 hover:text-indigo-600'"
                    :title="isFavorite ? t('kbView.favorite.tooltipRemove') : t('kbView.favorite.tooltipAdd')">
                    <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5 transition-transform group-hover:scale-110"
                        viewBox="0 0 24 24" :fill="isFavorite ? 'currentColor' : 'none'" stroke="currentColor"
                        stroke-width="2">
                        <path
                            d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
                    </svg>
                </button>

                <div class="relative" ref="shareContainerRef">
                    <button @click="sharePopover = !sharePopover"
                        class="p-2 h-9 w-9 flex items-center justify-center text-slate-500 dark:text-gray-400 hover:text-indigo-600 transition-all rounded-full hover:bg-slate-100 dark:hover:bg-gray-800"
                        :title="t('kbView.share.tooltip')">
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
                            stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="18" cy="5" r="3"></circle>
                            <circle cx="6" cy="12" r="3"></circle>
                            <circle cx="18" cy="19" r="3"></circle>
                            <line x1="8.59" x2="15.42" y1="13.51" y2="17.49"></line>
                            <line x1="15.41" x2="8.59" y1="6.51" y2="10.49"></line>
                        </svg>
                    </button>
                    <!-- Share Popover -->
                    <div v-if="sharePopover"
                        class="absolute right-0 mt-3 w-80 bg-white dark:bg-[#161b22] rounded-3xl shadow-2xl border border-slate-100 dark:border-gray-700 p-6 z-50 overflow-hidden">
                        <div class="flex justify-between items-center mb-6">
                            <h3 class="font-black text-xl text-slate-900 dark:text-gray-100">{{ t('kbView.share.title') }}</h3>
                            <button @click="sharePopover = false"
                                class="text-slate-400 hover:text-slate-600 dark:hover:text-gray-300 p-2 hover:bg-slate-50 dark:hover:bg-gray-700 rounded-full">
                                <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none"
                                    stroke="currentColor" stroke-width="2">
                                    <path d="M18 6 6 18" />
                                    <path d="m6 6 12 12" />
                                </svg>
                            </button>
                        </div>
                        <div class="space-y-6 text-center">
                            <!-- Options -->
                            <div class="flex items-center justify-between px-1">
                                <span class="text-xs font-bold text-slate-500 dark:text-gray-400">{{ t('kbView.share.standaloneMode') }}</span>
                                <label class="relative inline-flex items-center cursor-pointer">
                                    <input type="checkbox" v-model="shareStandalone" class="sr-only peer">
                                    <div
                                        class="w-9 h-5 bg-slate-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-indigo-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-indigo-600">
                                    </div>
                                </label>
                            </div>

                            <div
                                class="p-3 bg-slate-50 dark:bg-[#161b22]/50 rounded-2xl border border-slate-100 dark:border-gray-600 flex items-center gap-3">
                                <div class="flex-1 truncate text-left">
                                    <p class="text-[10px] text-slate-400 font-bold uppercase tracking-wider mb-1">{{ t('kbView.share.pageLink') }}
                                    </p>
                                    <p class="text-xs text-slate-600 dark:text-gray-300 truncate font-mono">{{ shareUrl
                                    }}</p>
                                </div>
                                <button @click="copyLink"
                                    class="p-2 px-3 rounded-xl text-xs font-bold transition-all shadow-lg dark:shadow-none"
                                    :class="[copied ? 'bg-emerald-500 text-white shadow-emerald-200 dark:shadow-none' : 'bg-indigo-600 text-white shadow-indigo-200 dark:shadow-none']">
                                    {{ copied ? t('kbView.share.copied') : t('kbView.share.copy') }}
                                </button>
                            </div>
                            <div
                                class="flex flex-col items-center gap-3 bg-slate-50 dark:bg-[#161b22]/50 p-6 rounded-3xl border border-slate-100 dark:border-gray-600">
                                <img :src="qrCodeUrl" class="w-36 h-36 rounded-2xl border-4 border-white shadow-sm"
                                    alt="QR Code" />
                                <span class="text-xs text-slate-500 font-medium">{{ t('kbView.share.scanMobile') }}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </nav>

        <div class="flex-1 flex overflow-hidden">
            <!-- Sidebar -->
            <aside v-if="!isStandalone"
                class="bg-white dark:bg-[#161b22] border-r border-slate-200 dark:border-gray-700 flex flex-col shrink-0 overflow-y-auto scrollbar-subtle hidden md:flex relative group/sidebar"
                :style="{ width: `${sidebarWidth}px` }">
                <div
                    class="absolute top-0 right-0 bottom-0 w-1 cursor-col-resize hover:bg-indigo-300 z-50 transition-colors opacity-0 group-hover/sidebar:opacity-100"
                    :class="{ 'bg-indigo-400 opacity-100': isResizingSidebar }"
                    @mousedown.prevent.stop="startSidebarResize"
                ></div>
                <div class="p-6">
                    <!-- <div class="flex items-center gap-2 mb-6 px-2">
                        <div class="w-2 h-6 bg-indigo-600 rounded-full"></div>
                        <h2 class="font-black text-slate-900 tracking-tight uppercase text-sm">目录索引</h2>
                    </div> -->
                    <!-- 搜索结果列表 -->
                    <SearchResultList v-if="searchQuery.trim()" :results="searchResults" :loading="searchLoading"
                        :query="searchQuery" :activeDocId="props.docId" @select="handleSearchSelect" />

                    <!-- 文档树（无搜索时显示） -->
                    <div v-else class="space-y-0.5">
                        <div v-for="doc in orderedDocuments" :key="doc.id"
                            :style="{ paddingLeft: `${doc.depth * 1 + 0.5}rem` }" @click="selectDoc(doc)"
                            @contextmenu.prevent="handleContextMenu($event, doc)"
                            class="flex items-center group px-2 py-1.5 cursor-pointer rounded-md text-sm transition-colors select-none"
                            :class="[doc.id === props.docId ? 'bg-zinc-200 dark:bg-zinc-800 text-zinc-900 dark:text-white' : 'text-zinc-700 dark:text-zinc-300 hover:bg-zinc-200 dark:hover:bg-zinc-800']">
                            <DocumentTreeNodeLabel :doc="doc" :expanded="expandedFolders.has(doc.id) || !!searchQuery"
                                folder-chevron-class="mr-1.5 text-zinc-400 shrink-0 flex items-center justify-center"
                                folder-icon-class="mr-2 text-blue-400 shrink-0"
                                file-icon-class="mr-2 text-zinc-400 shrink-0"
                                file-placeholder-class="w-5 shrink-0" />

                            <!-- Draft Indicator -->
                            <span v-if="doc.status === 'draft' && doc.type === 'file'"
                                class="ml-auto text-[10px] bg-slate-100 dark:bg-gray-700 text-slate-500 dark:text-gray-400 px-1.5 py-0.5 rounded border border-slate-200 dark:border-gray-600 font-bold shrink-0">{{ t('kbView.doc.draft') }}</span>
                        </div>
                    </div>
                </div>
            </aside>

            <!-- Main Content -->
            <main class="flex-1 overflow-y-auto bg-white dark:bg-[#161b22] relative">
                <div v-if="docLoading"
                    class="absolute inset-0 flex items-center justify-center bg-white/50 dark:bg-[#161b22]/50 backdrop-blur-sm z-10">
                    <div class="animate-spin rounded-full h-8 w-8 border-2 border-indigo-600 border-t-transparent">
                    </div>
                </div>

                <div v-else-if="currentDoc" class="max-w-4xl mx-auto px-4 sm:px-6 md:px-8 pt-3 md:pt-4 pb-12 md:pb-16">
                    <div class="w-full kb-visual-preview">
                        <VisualEditor
                            :model-value="renderedContent"
                            :is-read-only="true"
                            :hide-toolbar="true"
                            :pure-mode="true"
                        />
                    </div>

                    <footer
                        class="mt-32 pt-8 border-t border-slate-100 dark:border-gray-700 flex flex-wrap items-center justify-center gap-x-8 gap-y-4 pb-12 text-slate-400 dark:text-gray-500">
                        <div class="flex items-center gap-2">
                            <svg xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5 text-slate-300"
                                viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z" />
                                <circle cx="12" cy="12" r="3" />
                            </svg>
                            <span class="text-[10px] font-bold uppercase tracking-wider">{{ t('kbView.doc.views') }}</span>
                            <span class="text-xs font-bold text-slate-500">{{ currentDoc.viewCount || 0 }}</span>
                        </div>
                        <div class="w-px h-3 bg-slate-100 hidden md:block"></div>
                        <div class="flex items-center gap-2">
                            <svg xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5 text-slate-300"
                                viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <circle cx="12" cy="12" r="10" />
                                <polyline points="12 6 12 12 16 14" />
                            </svg>
                            <span class="text-[10px] font-bold uppercase tracking-wider">{{ t('kbView.doc.lastUpdate') }}</span>
                            <span class="text-xs font-semibold text-slate-500">{{ formatDate(currentDoc.updatedAt ||
                                currentDoc.updateTime || currentDoc.updated_at,
                                currentDoc.createdAt || currentDoc.createTime || currentDoc.created_at)
                                }}</span>
                        </div>
                    </footer>

                    <KbCommentSection
                        :doc-id="currentDoc?.id"
                        :current-user-id="currentUserId"
                        :current-user="currentUser"
                        :is-guestbook-enabled="isGuestbookEnabled"
                        :is-logged-in="isLoggedIn"
                        :can-edit="canEdit" />
                </div>



                <div v-if="!currentDoc && !docLoading"
                    class="h-full flex flex-col items-center justify-center p-12 text-center">
                    <h3 class="text-3xl font-black text-slate-900 dark:text-gray-100 mb-4 tracking-tight">{{ t('kbView.doc.exploreTitle') }}</h3>
                    <p class="text-slate-500 max-w-md mx-auto leading-relaxed">
                        {{ t('kbView.doc.exploreDescPrefix') }}<span class="text-indigo-600 font-bold">{{ kbDetail?.title }}</span>{{ t('kbView.doc.exploreDescSuffix') }}
                    </p>
                </div>
            </main>
        </div>
    </div>

    <!-- Context Menu -->
    <div v-if="contextMenu.visible" :style="{ top: `${contextMenu.y}px`, left: `${contextMenu.x}px` }"
        class="fixed z-50 bg-white dark:bg-[#161b22] border border-slate-200 dark:border-gray-700 rounded-lg shadow-xl py-1 min-w-[140px]">
        <button @click.stop="handleExport"
            class="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-gray-300 hover:bg-slate-50 dark:hover:bg-gray-700 flex items-center gap-2">
            <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4 text-slate-400" viewBox="0 0 24 24" fill="none"
                stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="7 10 12 15 17 10" />
                <line x1="12" y1="15" x2="12" y2="3" />
            </svg>
            {{ t('kbView.export.button') }}
        </button>
    </div>


</template>

<style scoped>
@import '../styles/kb-icon.css';
</style>
