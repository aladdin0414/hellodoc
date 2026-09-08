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

// 独立展示模式：query 参数中带有 standalone=true 时隐藏导航与侧边栏
const isStandalone = computed(() => route.query.standalone === 'true')
const isMobile = ref(window.innerWidth < 1024)
const isMobileDrawerOpen = ref(false)
const isSidebarCollapsed = ref(false)

const sidebarWidth = ref(290)
const isResizingSidebar = ref(false)
const shareStandalone = ref(false)
const SIDEBAR_WIDTH_STORAGE_KEY = 'knowledgeBaseViewSidebarWidth'
const MIN_SIDEBAR_WIDTH = 220
const MAX_SIDEBAR_WIDTH = 520

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
const expandedFolders = ref<Set<number>>(new Set())

// 本地持久化展开文件夹
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

const mainScrollRef = ref<HTMLElement | null>(null)
const error = ref<{ code: number, message: string } | null>(null)
const copied = ref(false)
const isFavorite = ref(false)
const isMac = computed(() => typeof navigator !== 'undefined' && /Mac|iPhone|iPod|iPad/.test(navigator.platform))

// TOC 目录大纲状态
interface TocItem {
    id: string
    text: string
    level: number
}
const tocItems = ref<TocItem[]>([])
const activeHeadingId = ref<string>('')

// 计算目录中最小标题级别，用于动态计算层级缩进
const minTocLevel = computed(() => {
    if (!tocItems.value.length) return 1
    return Math.min(...tocItems.value.map(i => i.level))
})

// TOC 平滑滑动高亮指示器
const tocListRef = ref<HTMLElement | null>(null)
const indicatorStyle = ref({
    top: '0px',
    height: '0px',
    opacity: 0
})

const updateIndicator = () => {
    if (!activeHeadingId.value || !tocListRef.value) {
        indicatorStyle.value = { ...indicatorStyle.value, opacity: 0 }
        return
    }
    nextTick(() => {
        if (!tocListRef.value) return
        const activeEl = tocListRef.value.querySelector(`[data-toc-id="${activeHeadingId.value}"]`) as HTMLElement | null
        if (activeEl) {
            const top = activeEl.offsetTop
            const height = activeEl.offsetHeight
            indicatorStyle.value = {
                top: `${top + 3}px`,
                height: `${Math.max(16, height - 6)}px`,
                opacity: 1
            }
        } else {
            indicatorStyle.value = { ...indicatorStyle.value, opacity: 0 }
        }
    })
}

watch(activeHeadingId, () => {
    updateIndicator()
})

const hasH1Title = computed(() => {
    if (!currentDoc.value?.content || !currentDoc.value?.name) return false
    const lines = currentDoc.value.content.split('\n')
    for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed) continue
        if (trimmed.startsWith('# ')) {
            return true
        }
        return false
    }
    return false
})

const getIconStyle = (color?: string) => utilGetIconStyle(color, isDark.value)
const getIconBgStyle = (color?: string) => utilGetIconBgStyle(color, isDark.value)

const renderedContent = computed(() => {
    if (!currentDoc.value) return ''
    const cleanedContent = stripMarkdownToc(currentDoc.value.content || '')
    if (!hasH1Title.value && currentDoc.value.name) {
        return `# ${currentDoc.value.name}\n\n${cleanedContent}`
    }
    return cleanedContent
})

const isLoggedIn = computed(() => !!localStorage.getItem('accessToken'))
const currentUserId = ref<number | null>(null)
const currentUser = ref<any>(null)
const frontendConfigs = ref<Record<string, string>>({})

const isGuestbookEnabled = computed(() => {
    return frontendConfigs.value['app.enable_guestbook'] !== 'false'
})

const canEdit = computed(() => {
    if (!isLoggedIn.value || !currentDoc.value || !kbDetail.value) return false
    if (['OWNER', 'ADMIN', 'EDITOR'].includes(kbDetail.value.role)) return true
    if (currentDoc.value.authorId && currentDoc.value.authorId === currentUserId.value) return true
    return false
})

const handleEdit = () => {
    if (!currentDoc.value) return
    router.push({ name: 'Editor', params: { kbId: props.kbId, docId: currentDoc.value.id } })
}

const handleCatalogResize = () => {
    isMobile.value = window.innerWidth < 1024
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

        if (!props.docId) {
            const coverDoc = documents.value.find(d => d.isCover)
            if (coverDoc) {
                router.replace({ name: 'PublicView', params: { kbId: props.kbId, docId: coverDoc.id }, query: route.query })
            }
        } else {
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
    document.title = `${res.name} - ${kbDetail.value?.title || 'Hellodoc'}`
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

    // 重新提取本页 TOC
    extractToc()
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

// 平铺的所有可阅读文档列表（过滤文件夹，按顺序排列）
const allFlatDocs = computed(() => {
    const allExpanded = new Set(documents.value.filter(d => d.type === 'folder').map(d => d.id))
    const fullList = buildOrderedDocuments(documents.value, allExpanded, '')
    return fullList.filter((d: any) => d.type !== 'folder')
})

// 上一篇文档
const prevDoc = computed(() => {
    if (!currentDoc.value) return null
    const list = allFlatDocs.value
    const idx = list.findIndex(d => d.id === currentDoc.value.id)
    return idx > 0 ? list[idx - 1] : null
})

// 下一篇文档
const nextDoc = computed(() => {
    if (!currentDoc.value) return null
    const list = allFlatDocs.value
    const idx = list.findIndex(d => d.id === currentDoc.value.id)
    return idx >= 0 && idx < list.length - 1 ? list[idx + 1] : null
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
        expandAncestorFolders(expandedFolders.value, documents.value, doc.id)
        router.push({ name: 'PublicView', params: { kbId: props.kbId, docId: doc.id }, query: route.query })
        if (isMobile.value) {
            isMobileDrawerOpen.value = false
        }
    }
}

// 代码块右上角一键复制按钮增强
const copySvgHtml = `<svg class="copy-icon" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg><svg class="check-icon" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>`

const enhanceCodeBlocks = () => {
    const previewEl = document.querySelector('.kb-visual-preview')
    if (!previewEl) return

    const preElements = previewEl.querySelectorAll('pre')
    preElements.forEach((pre) => {
        if (pre.querySelector('.code-copy-btn')) return
        const btn = document.createElement('button')
        btn.className = 'code-copy-btn'
        btn.setAttribute('type', 'button')
        btn.setAttribute('contenteditable', 'false')
        btn.setAttribute('title', '复制代码')
        btn.setAttribute('aria-label', '复制代码')
        btn.innerHTML = copySvgHtml
        pre.appendChild(btn)
    })
}

// 正文容器点击事件代理，点击复制按钮时一键复制代码
const handlePreviewContainerClick = async (event: MouseEvent) => {
    const target = event.target as HTMLElement
    const copyBtn = target.closest('.code-copy-btn') as HTMLButtonElement | null
    if (!copyBtn) return

    event.stopPropagation()
    event.preventDefault()

    const pre = copyBtn.closest('pre')
    if (!pre) return

    const codeEl = pre.querySelector('code')
    const clone = (codeEl || pre).cloneNode(true) as HTMLElement
    clone.querySelectorAll('.code-copy-btn').forEach(el => el.remove())
    const codeText = clone.textContent || ''

    try {
        await navigator.clipboard.writeText(codeText)
        copyBtn.classList.add('copied')
        setTimeout(() => {
            copyBtn.classList.remove('copied')
        }, 2000)
    } catch (err) {
        console.error('Copy code failed:', err)
    }
}

// 提取当前文档的标题作为本页 TOC 列表
let extractTocTimer: any = null
const extractToc = () => {
    if (extractTocTimer) clearTimeout(extractTocTimer)

    const doExtract = () => {
        const previewEl = document.querySelector('.kb-visual-preview')
        if (!previewEl) {
            tocItems.value = []
            return
        }

        // 提取 h1, h2, h3, h4
        const headings = previewEl.querySelectorAll('h1, h2, h3, h4')
        const items: TocItem[] = []

        headings.forEach((heading, index) => {
            const tag = heading.tagName.toLowerCase()
            const level = parseInt(tag.replace('h', ''))
            const text = heading.textContent?.trim() || ''
            if (!text) return

            let id = heading.id
            if (!id) {
                id = `doc-heading-${index}`
                heading.id = id
            }

            items.push({ id, text, level })
        })

        if (items.length > 0) {
            tocItems.value = items
            if (!activeHeadingId.value || !items.some(i => i.id === activeHeadingId.value)) {
                activeHeadingId.value = items[0]?.id || ''
            }
        } else {
            tocItems.value = []
            activeHeadingId.value = ''
        }

        // 提取完成后立即校对一次滚动位置，并为代码块挂载复制按钮与更新指示器
        handleContentScroll()
        enhanceCodeBlocks()
        updateIndicator()
    }

    nextTick(doExtract)
    extractTocTimer = setTimeout(doExtract, 150)
    setTimeout(doExtract, 400)
    setTimeout(doExtract, 900)
}

// 点击 TOC 标题定位平滑滚动
let isClickNavigating = false
let scrollStopTimer: any = null

const unlockClickNav = () => {
    isClickNavigating = false
    if (scrollStopTimer) {
        clearTimeout(scrollStopTimer)
        scrollStopTimer = null
    }
}

const scrollToHeading = (id: string) => {
    // 立即激活目标节点，使滑块直接一次性滑向目标位置
    activeHeadingId.value = id
    isClickNavigating = true
    if (scrollStopTimer) clearTimeout(scrollStopTimer)

    const el = document.getElementById(id)
    const scrollContainer = mainScrollRef.value
    if (el && scrollContainer) {
        const containerRect = scrollContainer.getBoundingClientRect()
        const elRect = el.getBoundingClientRect()
        const targetScrollTop = scrollContainer.scrollTop + (elRect.top - containerRect.top) - 24
        scrollContainer.scrollTo({
            top: Math.max(0, targetScrollTop),
            behavior: 'smooth'
        })
    }

    // 保底防抖定时器：若 400ms 内完全未触发任何滚动（如极近距离微小滚动），则自动解锁
    scrollStopTimer = setTimeout(() => {
        isClickNavigating = false
    }, 400)
}

// 滚动监听联动高亮 TOC 项 (ScrollSpy)
const handleContentScroll = () => {
    // 正在通过点击目录进行平滑滚动跳转时：
    if (isClickNavigating) {
        // 只要滚动还在继续，就持续顺延静止定时器，直到滚动真正彻底停下
        if (scrollStopTimer) clearTimeout(scrollStopTimer)
        scrollStopTimer = setTimeout(() => {
            isClickNavigating = false
        }, 150)
        // 绝不在此期间重算高亮，让滑块与目标项完全保持一致，杜绝任何中间跳跃
        return
    }

    if (!tocItems.value.length) return
    const scrollContainer = mainScrollRef.value
    if (!scrollContainer) return

    const scrollContainerRect = scrollContainer.getBoundingClientRect()
    const containerTop = scrollContainerRect.top
    const scrollHeight = scrollContainer.scrollHeight
    const scrollTop = scrollContainer.scrollTop
    const clientHeight = scrollContainer.clientHeight

    // 滚动到底部附近时直接高亮最后一项
    if (scrollHeight - (scrollTop + clientHeight) < 50) {
        activeHeadingId.value = tocItems.value[tocItems.value.length - 1]?.id || ''
        return
    }

    // 从上往下找到当前已进入阅读视口（top <= 100px）的最后一个标题
    let currentActive = tocItems.value[0]?.id || ''
    for (const item of tocItems.value) {
        const el = document.getElementById(item.id)
        if (el) {
            const rect = el.getBoundingClientRect()
            if (rect.top - containerTop <= 100) {
                currentActive = item.id
            } else {
                break
            }
        }
    }
    activeHeadingId.value = currentActive
}

// 右键菜单逻辑
const contextMenu = ref({
    visible: false,
    x: 0,
    y: 0,
    doc: null as any
})

const handleContextMenu = (e: MouseEvent, doc: any) => {
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

const moreMenuRef = ref<HTMLElement | null>(null)
const moreMenuOpen = ref(false)

const handleExportCurrentDoc = async () => {
    if (!currentDoc.value) return
    try {
        message.info(t('kbView.export.preparing'))
        const res: any = await exportDocument(props.kbId, currentDoc.value.id)
        const blob = new Blob([res], { type: 'application/zip' })
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.setAttribute('download', `${currentDoc.value.name}.zip`)
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
        message.success(t('kbView.export.started'))
    } catch (e) {
        console.error(e)
        message.error(t('kbView.export.failed'))
    }
}

const openShareModal = () => {
    moreMenuOpen.value = false
    sharePopover.value = true
}

const handleGlobalClick = (e: MouseEvent) => {
    closeContextMenu()
    if (moreMenuOpen.value && moreMenuRef.value && !moreMenuRef.value.contains(e.target as Node)) {
        moreMenuOpen.value = false
    }
}

const handleToggleFavorite = async () => {
    if (!currentDoc.value) return
    const docId = currentDoc.value.id
    const previousState = isFavorite.value
    isFavorite.value = !previousState

    try {
        await apiToggleFavorite(docId)
        message.success(isFavorite.value ? t('kbView.favorite.added') : t('kbView.favorite.removed'))
    } catch (err) {
        isFavorite.value = previousState
        console.error('Toggle favorite failed:', err)
    }
}

// 分享浮窗
const sharePopover = ref(false)

const shareUrl = computed(() => {
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
        tocItems.value = []
    }
}, { immediate: true })

// 搜索 debounce 逻辑
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
                timeout: 20000
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

const handleSearchSelect = (docId: number) => {
    expandAncestorFolders(expandedFolders.value, documents.value, docId)
    router.push({ name: 'PublicView', params: { kbId: props.kbId, docId }, query: route.query })
    searchQuery.value = ''
    if (isMobile.value) {
        isMobileDrawerOpen.value = false
    }
}

const handleGlobalKeyDown = (e: KeyboardEvent) => {
    if (((e.metaKey || e.ctrlKey) && e.key === 'k') || (e.key === '/' && document.activeElement?.tagName !== 'INPUT' && document.activeElement?.tagName !== 'TEXTAREA')) {
        e.preventDefault()
        if (isSidebarCollapsed.value) {
            isSidebarCollapsed.value = false
        }
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

    window.addEventListener('click', handleGlobalClick)
    window.addEventListener('keydown', handleGlobalKeyDown)
    window.addEventListener('resize', handleCatalogResize)

    if (isLoggedIn.value) {
        getMe().then((res: any) => {
            currentUserId.value = res.id
            currentUser.value = res
        }).catch(e => console.error('Get me failed', e))
    }
})

onUnmounted(() => {
    window.removeEventListener('click', handleGlobalClick)
    if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
    if (searchAbortController) searchAbortController.abort()
    if (extractTocTimer) clearTimeout(extractTocTimer)
    if (scrollStopTimer) clearTimeout(scrollStopTimer)
    window.removeEventListener('keydown', handleGlobalKeyDown)
    window.removeEventListener('resize', handleCatalogResize)
    stopSidebarResize()
})

watch(sharePopover, (newVal) => {
    if (newVal) {
        shareStandalone.value = isStandalone.value
    }
})
</script>

<template>
    <div class="fixed inset-0 bg-white dark:bg-[#0f1117] flex flex-col font-sans text-slate-900 dark:text-zinc-100 selection:bg-indigo-100 dark:selection:bg-indigo-900/40 selection:text-indigo-900 dark:selection:text-indigo-200 overflow-hidden">
        <!-- 错误提示 -->
        <div v-if="error" class="fixed inset-0 z-[100] bg-white dark:bg-[#0f1117] flex flex-col items-center justify-center p-6 text-center">
            <div class="max-w-md w-full">
                <div class="w-16 h-16 mx-auto mb-6 bg-red-50 dark:bg-red-950/40 text-red-600 dark:text-red-400 rounded-2xl flex items-center justify-center border border-red-100 dark:border-red-900/40">
                    <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <circle cx="12" cy="12" r="10" />
                        <line x1="12" y1="8" x2="12" y2="12" />
                        <line x1="12" y1="16" x2="12.01" y2="16" />
                    </svg>
                </div>
                <h2 class="text-2xl font-bold mb-3 tracking-tight">{{ t('kbView.error.restrictedTitle') }}</h2>
                <p class="text-slate-500 dark:text-zinc-400 mb-8 leading-relaxed">{{ t('kbView.error.restrictedDesc') }}</p>
                <div class="flex items-center justify-center gap-3">
                    <button @click="router.push({ name: 'Home' })" class="px-5 py-2.5 bg-indigo-600 text-white rounded-xl text-sm font-medium hover:bg-indigo-700 transition-colors">
                        {{ t('kbView.nav.backHome') }}
                    </button>
                    <button @click="reloadPage" class="px-5 py-2.5 border border-slate-200 dark:border-zinc-700 rounded-xl text-sm font-medium hover:bg-slate-50 dark:hover:bg-zinc-800 transition-colors">
                        {{ t('common.retry') }}
                    </button>
                </div>
            </div>
        </div>

        <!-- 移动端顶部顶栏 (吸顶) -->
        <header v-if="isMobile && !isStandalone" class="h-14 border-b border-slate-200/80 dark:border-zinc-800 flex items-center justify-between px-4 bg-white/80 dark:bg-[#0f1117]/80 backdrop-blur-md z-30 shrink-0">
            <div class="flex items-center gap-3 min-w-0">
                <template v-if="kbDetail">
                    <div class="kb-icon-shell kb-icon-shell--sm shrink-0" :style="getIconBgStyle(kbDetail?.color)">
                        <template v-if="getKbIcon(kbDetail?.icon)">
                            <img v-if="getKbIcon(kbDetail?.icon)?.type === 'image'" :src="String(getKbIcon(kbDetail?.icon)?.value)" class="kb-icon-glyph kb-icon-glyph--sm object-cover rounded" alt="kb-icon" />
                            <component v-else :is="getKbIcon(kbDetail?.icon)?.value" class="kb-icon-glyph kb-icon-glyph--sm" :style="getIconStyle(kbDetail?.color)" />
                        </template>
                        <component v-else :is="(Icons as any).Book" class="kb-icon-glyph kb-icon-glyph--sm" :style="getIconStyle(kbDetail?.color)" />
                    </div>
                </template>
                <span class="font-semibold text-sm truncate">{{ kbDetail?.title || t('kbView.doc.loadingTitle') }}</span>
            </div>

            <div class="flex items-center gap-1.5 shrink-0">
                <button @click="toggleTheme" class="p-2 text-slate-500 dark:text-zinc-400 hover:text-slate-900 dark:hover:text-zinc-100 rounded-lg">
                    <Icons.Sun v-if="isDark" class="w-4 h-4" />
                    <Icons.Moon v-else class="w-4 h-4" />
                </button>
                <button @click="isMobileDrawerOpen = true" class="p-2 text-slate-600 dark:text-zinc-300 hover:bg-slate-100 dark:hover:bg-zinc-800 rounded-lg" aria-label="打开侧边菜单">
                    <Icons.Menu class="w-5 h-5" />
                </button>
            </div>
        </header>

        <!-- 移动端侧栏抽屉遮罩 -->
        <div v-if="isMobile && isMobileDrawerOpen" @click="isMobileDrawerOpen = false" class="fixed inset-0 z-40 bg-black/40 backdrop-blur-sm"></div>

        <!-- 整体三栏布局主体 -->
        <div class="flex-1 flex overflow-hidden relative">

            <!-- 左侧侧边栏 (Sidebar) - SiliconFlow 现代导轨风格 -->
            <aside
                v-if="!isStandalone"
                :class="[
                    'z-40 flex flex-col shrink-0 bg-slate-50/70 dark:bg-[#12141c]/90 border-r border-slate-200/80 dark:border-zinc-800/80 transition-all duration-200',
                    isMobile ? (isMobileDrawerOpen ? 'fixed top-0 bottom-0 left-0 w-80 shadow-2xl z-50 bg-white dark:bg-[#12141c]' : 'hidden') : (isSidebarCollapsed ? 'w-0 border-r-0 overflow-hidden' : 'relative')
                ]"
                :style="!isMobile && !isSidebarCollapsed ? { width: `${sidebarWidth}px` } : {}"
            >
                <!-- 拖拽调整宽度手柄 -->
                <div
                    v-if="!isMobile && !isSidebarCollapsed"
                    class="absolute top-0 right-0 bottom-0 w-1 cursor-col-resize hover:bg-indigo-400/80 z-50 transition-colors opacity-0 hover:opacity-100"
                    :class="{ 'bg-indigo-500 opacity-100': isResizingSidebar }"
                    @mousedown.prevent.stop="startSidebarResize"
                ></div>

                <!-- 侧边栏头部：Logo / 知识库名称 + 收起按钮 -->
                <div class="h-14 px-4 border-b border-slate-200/70 dark:border-zinc-800/70 flex items-center justify-between shrink-0">
                    <router-link to="/" class="flex items-center gap-2.5 min-w-0 group" :title="kbDetail?.title || 'Hellodoc'">
                        <div class="kb-icon-shell kb-icon-shell--sm shrink-0 shadow-sm" :style="getIconBgStyle(kbDetail?.color)">
                            <template v-if="getKbIcon(kbDetail?.icon)">
                                <img v-if="getKbIcon(kbDetail?.icon)?.type === 'image'" :src="String(getKbIcon(kbDetail?.icon)?.value)" class="kb-icon-glyph kb-icon-glyph--sm object-cover rounded" alt="kb-icon" />
                                <component v-else :is="getKbIcon(kbDetail?.icon)?.value" class="kb-icon-glyph kb-icon-glyph--sm" :style="getIconStyle(kbDetail?.color)" />
                            </template>
                            <component v-else :is="(Icons as any).Book" class="kb-icon-glyph kb-icon-glyph--sm" :style="getIconStyle(kbDetail?.color)" />
                        </div>
                        <span class="font-semibold text-sm text-slate-800 dark:text-zinc-100 truncate group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
                            {{ kbDetail?.title || t('kbView.doc.loadingTitle') }}
                        </span>
                    </router-link>

                    <!-- 收起侧边栏按钮 (Desktop) / 关闭按钮 (Mobile) -->
                    <button
                        v-if="!isMobile"
                        @click="isSidebarCollapsed = true"
                        class="p-1.5 text-slate-400 hover:text-slate-700 dark:hover:text-zinc-200 hover:bg-slate-200/60 dark:hover:bg-zinc-800 rounded-md transition-colors"
                        :title="t('kbView.doc.collapseSidebar', '收起侧边栏')"
                    >
                        <Icons.PanelLeftClose class="w-4 h-4" />
                    </button>
                    <button
                        v-else
                        @click="isMobileDrawerOpen = false"
                        class="p-1.5 text-slate-400 hover:text-slate-700 dark:hover:text-zinc-200 hover:bg-slate-200/60 dark:hover:bg-zinc-800 rounded-md transition-colors"
                    >
                        <Icons.X class="w-4 h-4" />
                    </button>
                </div>

                <!-- 侧边栏搜索框 (SiliconFlow 胶囊/内嵌搜索样式) -->
                <div class="p-3 pb-2 shrink-0">
                    <div class="relative flex items-center">
                        <Icons.Search class="absolute left-3 w-4 h-4 text-slate-400 pointer-events-none" />
                        <input
                            ref="searchInputRef"
                            v-model="searchQuery"
                            type="text"
                            :placeholder="t('kbView.nav.searchPlaceholder', { shortcut: isMac ? '⌘K' : 'Ctrl+K' })"
                            class="w-full pl-9 pr-14 py-1.5 bg-white dark:bg-zinc-900 border border-slate-200/80 dark:border-zinc-700/70 rounded-lg text-xs text-slate-800 dark:text-zinc-200 placeholder-slate-400 dark:placeholder-zinc-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500 transition-all shadow-2xs"
                        />
                        <div v-if="!searchQuery.trim()" class="absolute right-2 flex items-center gap-0.5 pointer-events-none">
                            <kbd class="px-1.5 py-0.5 text-[10px] font-mono text-slate-400 dark:text-zinc-500 bg-slate-100 dark:bg-zinc-800 border border-slate-200 dark:border-zinc-700 rounded">
                                {{ isMac ? '⌘K' : 'Ctrl+K' }}
                            </kbd>
                        </div>
                        <button
                            v-else
                            @click="searchQuery = ''"
                            class="absolute right-2 p-1 text-slate-400 hover:text-slate-600 dark:hover:text-zinc-300"
                        >
                            <Icons.X class="w-3.5 h-3.5" />
                        </button>
                    </div>
                </div>

                <!-- 侧边栏目录滚动列表 -->
                <div class="flex-1 overflow-y-auto px-3 py-2 scrollbar-subtle">
                    <!-- 搜索结果列表 -->
                    <SearchResultList
                        v-if="searchQuery.trim()"
                        :results="searchResults"
                        :loading="searchLoading"
                        :query="searchQuery"
                        :activeDocId="props.docId"
                        @select="handleSearchSelect"
                    />

                    <!-- 文档树（宽松舒适的现代文档导轨风格） -->
                    <div v-else class="space-y-1 pb-4">
                        <div
                            v-for="doc in orderedDocuments"
                            :key="doc.id"
                            :style="{ paddingLeft: `${doc.depth * 1.1 + 0.35}rem` }"
                            @click="selectDoc(doc)"
                            @contextmenu.prevent="handleContextMenu($event, doc)"
                            class="relative flex items-center group px-2.5 py-2 cursor-pointer rounded-lg text-[13px] min-h-[36px] transition-all select-none"
                            :class="[
                                doc.id === props.docId
                                    ? 'bg-indigo-50/90 dark:bg-indigo-950/50 text-indigo-600 dark:text-indigo-400 font-medium'
                                    : 'text-slate-600 dark:text-zinc-400 hover:bg-slate-100/80 dark:hover:bg-zinc-800/60 hover:text-slate-900 dark:hover:text-zinc-100'
                            ]"
                        >
                            <!-- 激活项左侧小高亮条（内嵌于卡片左侧） -->
                            <div
                                v-if="doc.id === props.docId"
                                class="absolute left-1 top-2 bottom-2 w-0.5 bg-indigo-600 dark:bg-indigo-500 rounded-full"
                            ></div>

                            <DocumentTreeNodeLabel
                                :doc="doc"
                                :expanded="expandedFolders.has(doc.id) || !!searchQuery"
                                folder-chevron-class="text-slate-400 dark:text-zinc-500 hover:text-slate-600 dark:hover:text-zinc-300"
                                folder-icon-class="text-indigo-500 dark:text-indigo-400"
                                file-icon-class="text-slate-400 dark:text-zinc-500"
                            />

                            <!-- 草稿状态标识 -->
                            <span
                                v-if="doc.status === 'draft' && doc.type === 'file'"
                                class="ml-auto text-[10px] bg-slate-100 dark:bg-zinc-800 text-slate-500 dark:text-zinc-400 px-1.5 py-0.5 rounded border border-slate-200 dark:border-zinc-700 shrink-0"
                            >
                                {{ t('kbView.doc.draft') }}
                            </span>
                        </div>
                    </div>
                </div>

                <!-- 侧边栏底部工具栏 -->
                <div class="h-12 px-3 border-t border-slate-200/70 dark:border-zinc-800/70 flex items-center justify-between shrink-0 text-slate-500 dark:text-zinc-400 text-xs">
                    <router-link
                        to="/"
                        class="inline-flex items-center gap-1.5 hover:text-slate-900 dark:hover:text-zinc-100 transition-colors"
                        :title="t('kbView.nav.homeTooltip')"
                    >
                        <Icons.Home class="w-4 h-4" />
                        <span>{{ t('kbView.nav.backHome') }}</span>
                    </router-link>

                    <button
                        @click="toggleTheme"
                        class="p-1.5 hover:text-slate-900 dark:hover:text-zinc-100 rounded-md hover:bg-slate-200/50 dark:hover:bg-zinc-800 transition-colors"
                        :title="isDark ? t('kbView.nav.lightMode') : t('kbView.nav.darkMode')"
                    >
                        <Icons.Sun v-if="isDark" class="w-4 h-4" />
                        <Icons.Moon v-else class="w-4 h-4" />
                    </button>
                </div>
            </aside>

            <!-- 中间主文档区 + 右侧 TOC 视口容器 -->
            <div class="flex-1 flex flex-col min-w-0 h-full overflow-hidden bg-white dark:bg-[#0f1117] relative">

                <!-- 左上角悬浮展开侧边栏按钮 (侧边栏收起时显示) -->
                <button
                    v-if="!isMobile && isSidebarCollapsed"
                    @click="isSidebarCollapsed = false"
                    class="absolute top-4 left-5 z-30 p-2 text-slate-400 hover:text-slate-700 dark:hover:text-zinc-200 hover:bg-slate-100 dark:hover:bg-zinc-800 rounded-lg transition-colors bg-white/80 dark:bg-zinc-900/80 backdrop-blur-xs border border-slate-200/60 dark:border-zinc-800 shadow-2xs"
                    :title="t('kbView.doc.expandSidebar', '展开侧边栏')"
                >
                    <Icons.PanelLeft class="w-4 h-4" />
                </button>

                <!-- 页面最右上角 ... 更多操作按钮 -->
                <div v-if="!isStandalone && currentDoc" class="absolute top-4 right-6 z-30" ref="moreMenuRef">
                    <button
                        @click.stop="moreMenuOpen = !moreMenuOpen"
                        class="p-2 text-slate-400 hover:text-slate-700 dark:hover:text-zinc-200 hover:bg-slate-100 dark:hover:bg-zinc-800 rounded-lg transition-colors bg-white/80 dark:bg-zinc-900/80 backdrop-blur-xs border border-slate-200/60 dark:border-zinc-800 shadow-2xs"
                        title="更多操作"
                    >
                        <Icons.MoreHorizontal class="w-4 h-4" />
                    </button>

                    <!-- 下拉菜单 -->
                    <div
                        v-if="moreMenuOpen"
                        class="absolute right-0 mt-1.5 w-44 bg-white dark:bg-zinc-900 rounded-xl shadow-xl border border-slate-200/80 dark:border-zinc-800 py-1 z-40 text-xs text-slate-700 dark:text-zinc-300 divide-y divide-slate-100 dark:divide-zinc-800/60"
                    >
                        <div class="py-1">
                            <!-- 编辑 -->
                            <button
                                v-if="canEdit"
                                @click.stop="handleEdit(); moreMenuOpen = false"
                                class="w-full text-left px-3.5 py-2 hover:bg-slate-100 dark:hover:bg-zinc-800 flex items-center gap-2.5 transition-colors"
                            >
                                <Icons.Edit3 class="w-3.5 h-3.5 text-slate-400 dark:text-zinc-500" />
                                <span>{{ t('kbView.doc.editTooltip') }}</span>
                            </button>

                            <!-- 收藏 -->
                            <button
                                v-if="isLoggedIn"
                                @click.stop="handleToggleFavorite(); moreMenuOpen = false"
                                class="w-full text-left px-3.5 py-2 hover:bg-slate-100 dark:hover:bg-zinc-800 flex items-center gap-2.5 transition-colors"
                            >
                                <Icons.Star class="w-3.5 h-3.5" :class="isFavorite ? 'text-amber-500 fill-amber-500' : 'text-slate-400 dark:text-zinc-500'" />
                                <span>{{ isFavorite ? t('kbView.favorite.tooltipRemove') : t('kbView.favorite.tooltipAdd') }}</span>
                            </button>
                        </div>

                        <div class="py-1">
                            <!-- 分享 -->
                            <button
                                @click.stop="openShareModal"
                                class="w-full text-left px-3.5 py-2 hover:bg-slate-100 dark:hover:bg-zinc-800 flex items-center gap-2.5 transition-colors"
                            >
                                <Icons.Share2 class="w-3.5 h-3.5 text-slate-400 dark:text-zinc-500" />
                                <span>{{ t('kbView.share.title') }}</span>
                            </button>

                            <!-- 导出 ZIP -->
                            <button
                                @click.stop="handleExportCurrentDoc(); moreMenuOpen = false"
                                class="w-full text-left px-3.5 py-2 hover:bg-slate-100 dark:hover:bg-zinc-800 flex items-center gap-2.5 transition-colors"
                            >
                                <Icons.Download class="w-3.5 h-3.5 text-slate-400 dark:text-zinc-500" />
                                <span>{{ t('kbView.export.button') }}</span>
                            </button>
                        </div>
                    </div>
                </div>

                <!-- 正文 + TOC 滚动排版容器 -->
                <main
                    ref="mainScrollRef"
                    @scroll="handleContentScroll"
                    @wheel="unlockClickNav"
                    @touchmove="unlockClickNav"
                    @pointerdown="unlockClickNav"
                    class="flex-1 overflow-y-auto relative flex"
                >
                    <!-- 文档加载中遮罩 -->
                    <div v-if="docLoading" class="absolute inset-0 flex items-center justify-center bg-white/60 dark:bg-[#0f1117]/60 backdrop-blur-xs z-10">
                        <div class="animate-spin rounded-full h-8 w-8 border-2 border-indigo-600 border-t-transparent"></div>
                    </div>

                    <!-- 正文内容区 (中栏) -->
                    <div class="flex-1 min-w-0">
                        <div v-if="currentDoc" class="max-w-4xl mx-auto px-6 sm:px-10 lg:px-12 py-8 md:py-10">

                            <!-- Markdown 正文渲染 (使用 VisualEditor 只读纯净渲染) -->
                            <article class="w-full kb-visual-preview" @click="handlePreviewContainerClick">
                                <VisualEditor
                                    :model-value="renderedContent"
                                    :is-read-only="true"
                                    :hide-toolbar="true"
                                    :pure-mode="true"
                                />
                            </article>

                            <!-- 上一篇 / 下一篇翻页卡片 (SiliconFlow / Fumadocs 现代卡片导航) -->
                            <div v-if="prevDoc || nextDoc" class="mt-14 pt-8 border-t border-slate-200/80 dark:border-zinc-800/80 grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <!-- 上一篇 -->
                                <div
                                    v-if="prevDoc"
                                    @click="selectDoc(prevDoc)"
                                    class="group p-4 rounded-xl border border-slate-200/80 dark:border-zinc-800/80 hover:border-indigo-400 dark:hover:border-indigo-500 bg-slate-50/40 dark:bg-zinc-900/30 hover:bg-white dark:hover:bg-zinc-900 transition-all cursor-pointer flex flex-col justify-between"
                                >
                                    <div class="flex items-center gap-1.5 text-xs text-slate-400 dark:text-zinc-500 mb-1">
                                        <Icons.ChevronLeft class="w-3.5 h-3.5 group-hover:-translate-x-0.5 transition-transform" />
                                        <span>{{ t('kbView.doc.prevDoc', '上一篇') }}</span>
                                    </div>
                                    <span class="text-sm font-medium text-slate-800 dark:text-zinc-200 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors line-clamp-1">
                                        {{ prevDoc.name }}
                                    </span>
                                </div>
                                <div v-else class="hidden sm:block"></div>

                                <!-- 下一篇 -->
                                <div
                                    v-if="nextDoc"
                                    @click="selectDoc(nextDoc)"
                                    class="group p-4 rounded-xl border border-slate-200/80 dark:border-zinc-800/80 hover:border-indigo-400 dark:hover:border-indigo-500 bg-slate-50/40 dark:bg-zinc-900/30 hover:bg-white dark:hover:bg-zinc-900 transition-all cursor-pointer flex flex-col justify-between text-right"
                                >
                                    <div class="flex items-center justify-end gap-1.5 text-xs text-slate-400 dark:text-zinc-500 mb-1">
                                        <span>{{ t('kbView.doc.nextDoc', '下一篇') }}</span>
                                        <Icons.ChevronRight class="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" />
                                    </div>
                                    <span class="text-sm font-medium text-slate-800 dark:text-zinc-200 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors line-clamp-1">
                                        {{ nextDoc.name }}
                                    </span>
                                </div>
                            </div>

                            <!-- 文章元信息底部栏 (阅读次数、最后更新时间) -->
                            <footer class="mt-8 pt-6 border-t border-slate-100 dark:border-zinc-800/50 flex flex-wrap items-center justify-between text-xs text-slate-400 dark:text-zinc-500 gap-4">
                                <div class="flex items-center gap-2">
                                    <Icons.Eye class="w-3.5 h-3.5" />
                                    <span>{{ t('kbView.doc.views') }}: {{ currentDoc.viewCount || 0 }}</span>
                                </div>
                                <div class="flex items-center gap-2">
                                    <Icons.Clock class="w-3.5 h-3.5" />
                                    <span>{{ t('kbView.doc.lastUpdate') }}: {{ formatDate(currentDoc.updatedAt || currentDoc.updateTime || currentDoc.updated_at, currentDoc.createdAt || currentDoc.createTime || currentDoc.created_at) }}</span>
                                </div>
                            </footer>

                            <!-- 留言讨论评论区 -->
                            <div class="mt-8">
                                <KbCommentSection
                                    :doc-id="currentDoc?.id"
                                    :current-user-id="currentUserId"
                                    :current-user="currentUser"
                                    :is-guestbook-enabled="isGuestbookEnabled"
                                    :is-logged-in="isLoggedIn"
                                    :can-edit="canEdit"
                                />
                            </div>
                        </div>

                        <!-- 未选择文档时的空状态 -->
                        <div v-else-if="!docLoading" class="h-full flex flex-col items-center justify-center p-12 text-center min-h-[60vh]">
                            <div class="w-16 h-16 bg-indigo-50 dark:bg-indigo-950/40 text-indigo-600 dark:text-indigo-400 rounded-2xl flex items-center justify-center mb-6">
                                <Icons.BookOpen class="w-8 h-8" />
                            </div>
                            <h3 class="text-2xl font-bold text-slate-900 dark:text-zinc-100 mb-3 tracking-tight">
                                {{ t('kbView.doc.exploreTitle') }}
                            </h3>
                            <p class="text-slate-500 dark:text-zinc-400 max-w-md leading-relaxed text-sm">
                                {{ t('kbView.doc.exploreDescPrefix') }}<span class="text-indigo-600 dark:text-indigo-400 font-semibold">{{ kbDetail?.title }}</span>{{ t('kbView.doc.exploreDescSuffix') }}
                            </p>
                        </div>
                    </div>

                    <!-- 右侧 TOC 目录大纲 (On this page) - SiliconFlow 风格 -->
                    <aside
                        v-if="!isStandalone && currentDoc && tocItems.length > 0"
                        class="w-64 hidden xl:block shrink-0 sticky top-0 h-fit max-h-screen overflow-y-auto px-4 py-8 text-xs select-none"
                    >
                        <div class="flex items-center gap-1.5 font-medium text-slate-800 dark:text-zinc-200 mb-3 text-xs tracking-tight">
                            <Icons.AlignLeft class="w-3.5 h-3.5 text-slate-500 dark:text-zinc-400" />
                            <span>{{ t('kbView.doc.tocTitle', 'On this page') }}</span>
                        </div>
                        <ul ref="tocListRef" class="space-y-1 relative border-l border-slate-200/80 dark:border-zinc-800/80 pl-2.5">
                            <!-- 动态平滑滑动的左侧紫色高亮指示条 (Indicator Slider) -->
                            <div
                                class="absolute -left-[1px] w-[2px] bg-purple-600 dark:bg-purple-400 rounded-full pointer-events-none transition-all duration-300 ease-[cubic-bezier(0.25,1,0.5,1)]"
                                :style="indicatorStyle"
                            ></div>

                            <li
                                v-for="item in tocItems"
                                :key="item.id"
                                :data-toc-id="item.id"
                                class="relative group"
                                :style="{ paddingLeft: `${Math.max(0, item.level - minTocLevel) * 0.75}rem` }"
                            >
                                <a
                                    :href="`#${item.id}`"
                                    @click.prevent="scrollToHeading(item.id)"
                                    class="block py-1 text-slate-500 dark:text-zinc-400 hover:text-slate-900 dark:hover:text-zinc-100 transition-colors truncate leading-relaxed"
                                    :class="[
                                        activeHeadingId === item.id
                                            ? 'text-purple-600 dark:text-purple-400 font-medium'
                                            : ''
                                    ]"
                                    :title="item.text"
                                >
                                    {{ item.text }}
                                </a>
                            </li>
                        </ul>
                    </aside>
                </main>
            </div>
        </div>

        <!-- 目录右键导出菜单 -->
        <div
            v-if="contextMenu.visible"
            :style="{ top: `${contextMenu.y}px`, left: `${contextMenu.x}px` }"
            class="fixed z-50 bg-white dark:bg-zinc-900 border border-slate-200 dark:border-zinc-800 rounded-xl shadow-xl py-1 min-w-[130px]"
        >
            <button
                @click.stop="handleExport"
                class="w-full text-left px-3 py-2 text-xs text-slate-700 dark:text-zinc-300 hover:bg-slate-100 dark:hover:bg-zinc-800 flex items-center gap-2"
            >
                <Icons.Download class="w-3.5 h-3.5 text-slate-400" />
                <span>{{ t('kbView.export.button') }}</span>
            </button>
        </div>

        <!-- 分享弹窗 Modal -->
        <div
            v-if="sharePopover"
            class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-xs"
            @click.self="sharePopover = false"
        >
            <div
                @click.stop
                class="w-full max-w-sm bg-white dark:bg-zinc-900 rounded-2xl shadow-2xl border border-slate-200/80 dark:border-zinc-800 p-5"
            >
                <div class="flex justify-between items-center mb-4">
                    <h3 class="font-bold text-sm text-slate-900 dark:text-zinc-100">{{ t('kbView.share.title') }}</h3>
                    <button @click="sharePopover = false" class="text-slate-400 hover:text-slate-600 dark:hover:text-zinc-300 p-1 rounded-md">
                        <Icons.X class="w-4 h-4" />
                    </button>
                </div>
                <div class="space-y-4 text-center">
                    <div class="flex items-center justify-between text-xs text-slate-600 dark:text-zinc-400">
                        <span>{{ t('kbView.share.standaloneMode') }}</span>
                        <label class="relative inline-flex items-center cursor-pointer">
                            <input type="checkbox" v-model="shareStandalone" class="sr-only peer">
                            <div class="w-8 h-4 bg-slate-200 dark:bg-zinc-700 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-3 after:w-3 after:transition-all peer-checked:bg-indigo-600"></div>
                        </label>
                    </div>

                    <div class="p-2.5 bg-slate-50 dark:bg-zinc-800/60 rounded-xl border border-slate-100 dark:border-zinc-800 flex items-center gap-2">
                        <p class="flex-1 text-xs text-slate-600 dark:text-zinc-300 truncate font-mono text-left">{{ shareUrl }}</p>
                        <button
                            @click="copyLink"
                            class="px-2.5 py-1 rounded-lg text-xs font-medium text-white transition-colors"
                            :class="copied ? 'bg-emerald-600' : 'bg-indigo-600 hover:bg-indigo-700'"
                        >
                            {{ copied ? t('kbView.share.copied') : t('kbView.share.copy') }}
                        </button>
                    </div>

                    <div class="flex flex-col items-center gap-2 bg-slate-50 dark:bg-zinc-800/60 p-4 rounded-xl border border-slate-100 dark:border-zinc-800">
                        <img :src="qrCodeUrl" class="w-28 h-28 rounded-lg border border-white shadow-xs" alt="QR Code" />
                        <span class="text-[11px] text-slate-500 dark:text-zinc-400">{{ t('kbView.share.scanMobile') }}</span>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
@import '../styles/kb-icon.css';

/* 滚动条轻微美化 */
.scrollbar-subtle::-webkit-scrollbar {
    width: 4px;
    height: 4px;
}
.scrollbar-subtle::-webkit-scrollbar-track {
    background: transparent;
}
.scrollbar-subtle::-webkit-scrollbar-thumb {
    background: rgba(148, 163, 184, 0.3);
    border-radius: 9999px;
}
.scrollbar-subtle::-webkit-scrollbar-thumb:hover {
    background: rgba(148, 163, 184, 0.5);
}

/* 优化知识库正文标题排版与留白呼吸感 */
:deep(.kb-visual-preview .ProseMirror h1) {
    margin-top: 2.2em;
    margin-bottom: 1.1em;
    line-height: 1.35;
}
:deep(.kb-visual-preview .ProseMirror h1:first-child) {
    margin-top: 0.5em;
    margin-bottom: 1.4em;
}
:deep(.kb-visual-preview .ProseMirror h2) {
    margin-top: 2.2em;
    margin-bottom: 0.85em;
    line-height: 1.4;
}
:deep(.kb-visual-preview .ProseMirror h3) {
    margin-top: 1.8em;
    margin-bottom: 0.7em;
    line-height: 1.45;
}
:deep(.kb-visual-preview .ProseMirror h4) {
    margin-top: 1.5em;
    margin-bottom: 0.6em;
    line-height: 1.45;
}

/* 优化列表项有序序号与无序圆点的颜色 (参考 Tailwind Prose / SiliconFlow) */
:deep(.kb-visual-preview .ProseMirror ol > li::marker) {
    color: #64748b;
    font-weight: 500;
}
:deep(.dark .kb-visual-preview .ProseMirror ol > li::marker) {
    color: #94a3b8;
}
:deep(.kb-visual-preview .ProseMirror ul > li::marker) {
    color: #94a3b8;
}
:deep(.dark .kb-visual-preview .ProseMirror ul > li::marker) {
    color: #64748b;
}
:deep(.kb-visual-preview .ProseMirror p) {
    margin: 1.25em 0;
    line-height: 1.8;
    letter-spacing: 0.005em;
}
:deep(.kb-visual-preview .ProseMirror ul),
:deep(.kb-visual-preview .ProseMirror ol) {
    margin: 0.85em 0;
}
:deep(.kb-visual-preview .ProseMirror li > ul),
:deep(.kb-visual-preview .ProseMirror li > ol) {
    margin: 0.25em 0 0.35em;
}
:deep(.kb-visual-preview .ProseMirror li) {
    margin: 0.3em 0;
    line-height: 1.75;
}
:deep(.kb-visual-preview .ProseMirror li p) {
    margin: 0.15em 0;
    line-height: 1.75;
}
</style>
