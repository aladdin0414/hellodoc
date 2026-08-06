import { ref, watch, type Ref } from 'vue'

interface UseDocumentUiStateOptions<TSearchResult> {
    routeDocId: Ref<number | undefined>
    searchInKb: (query: string) => Promise<TSearchResult[]>
}

const SIDEBAR_WIDTH_STORAGE_KEY = 'documentEditorSidebarWidth'
const MIN_SIDEBAR_WIDTH = 200
const MAX_SIDEBAR_WIDTH = 600

export const useDocumentUiState = <TSearchResult>(options: UseDocumentUiStateOptions<TSearchResult>) => {
    const isMobile = ref(false)
    const showKbDropdown = ref(false)
    const showCreateMenu = ref(false)
    const showContextMenu = ref(false)
    const sidebarSearchQuery = ref('')
    const searchResults = ref<TSearchResult[]>([])
    const searchLoading = ref(false)
    const sidebarWidth = ref(288)
    const isResizingSidebar = ref(false)
    const isSidebarVisible = ref(true)

    let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null

    const updateIsMobile = () => {
        isMobile.value = window.matchMedia('(max-width: 767px)').matches
    }

    if (typeof window !== 'undefined') {
        const storedWidth = localStorage.getItem(SIDEBAR_WIDTH_STORAGE_KEY)
        if (storedWidth) {
            const parsed = parseInt(storedWidth)
            if (!Number.isNaN(parsed) && parsed >= MIN_SIDEBAR_WIDTH && parsed <= MAX_SIDEBAR_WIDTH) {
                sidebarWidth.value = parsed
            }
        }
    }

    watch(
        () => [options.routeDocId.value, isMobile.value] as const,
        ([docId, mobile]) => {
            if (!mobile) return
            isSidebarVisible.value = !docId
        },
        { immediate: true }
    )

    watch(sidebarSearchQuery, (query) => {
        if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
        const trimmed = query.trim()
        if (!trimmed) {
            searchResults.value = []
            searchLoading.value = false
            return
        }
        searchLoading.value = true
        searchDebounceTimer = setTimeout(async () => {
            try {
                searchResults.value = await options.searchInKb(trimmed)
            } catch (error) {
                console.error('Search failed:', error)
                searchResults.value = []
            } finally {
                searchLoading.value = false
            }
        }, 300)
    })

    const closeAllMenus = () => {
        showKbDropdown.value = false
        showCreateMenu.value = false
        showContextMenu.value = false
    }

    const toggleSidebar = () => {
        isSidebarVisible.value = !isSidebarVisible.value
    }

    const handleResize = (event: MouseEvent) => {
        if (!isResizingSidebar.value) return
        let newWidth = event.clientX
        if (newWidth < MIN_SIDEBAR_WIDTH) newWidth = MIN_SIDEBAR_WIDTH
        if (newWidth > MAX_SIDEBAR_WIDTH) newWidth = MAX_SIDEBAR_WIDTH
        sidebarWidth.value = newWidth
    }

    const stopResize = () => {
        isResizingSidebar.value = false
        document.body.style.cursor = ''
        document.body.style.userSelect = ''
        localStorage.setItem(SIDEBAR_WIDTH_STORAGE_KEY, sidebarWidth.value.toString())
        window.removeEventListener('mousemove', handleResize)
        window.removeEventListener('mouseup', stopResize)
    }

    const startResize = () => {
        isResizingSidebar.value = true
        document.body.style.cursor = 'col-resize'
        document.body.style.userSelect = 'none'
        window.addEventListener('mousemove', handleResize)
        window.addEventListener('mouseup', stopResize)
    }

    const cleanupUiState = () => {
        if (searchDebounceTimer) {
            clearTimeout(searchDebounceTimer)
            searchDebounceTimer = null
        }
        stopResize()
    }

    return {
        isMobile,
        updateIsMobile,
        showKbDropdown,
        showCreateMenu,
        showContextMenu,
        sidebarSearchQuery,
        searchResults,
        searchLoading,
        sidebarWidth,
        isResizingSidebar,
        isSidebarVisible,
        closeAllMenus,
        toggleSidebar,
        startResize,
        cleanupUiState
    }
}
