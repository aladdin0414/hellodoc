<script setup lang="ts">
import { computed } from 'vue'
import * as Icons from 'lucide-vue-next'
import { Search, MoreHorizontal, FilePlus, FolderPlus, Trash2, Plus, Folder, FileText } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useTheme } from '../../composables/useTheme'
import SearchResultList from '../SearchResultList.vue'
import DocumentTreeNodeLabel from '../shared/DocumentTreeNodeLabel.vue'
import type { SearchResult } from '../SearchResultList.vue'

interface DocBaseLike {
    id: number
    __key?: number
    name: string
    type: string
    parentId: number | null
    orderNum: number
    status?: string
    isCover?: boolean
    extraMeta?: Record<string, any>
}

interface OrderedDocLike extends DocBaseLike {
    depth: number
}

interface KbLike {
    id: number
    title: string
    icon?: string | null
    color?: string
}

interface DragStateLike {
    draggingDoc?: DocBaseLike | null
    overDocId?: number | null
    dropPosition?: 'before' | 'after' | 'inside' | null
}

const props = defineProps<{
    visible: boolean
    isMobile: boolean
    sidebarWidth: number
    isResizingSidebar: boolean
    kbDetail: { title?: string; color?: string; icon?: string | null } | null
    showKbDropdown: boolean
    editableKbs: KbLike[]
    kbId: number
    sidebarSearchQuery: string
    loading: boolean
    documents: DocBaseLike[]
    inlineCreating: { type: 'file' | 'folder'; parentId: number | null } | null
    searchResults: SearchResult[]
    searchLoading: boolean
    orderedDocuments: OrderedDocLike[]
    expandedFolders: Set<number>
    dragState: DragStateLike
    selectedDocIds: Set<number>
    activeDocId: number | null
    editingDocId: number | null
    editingName: string
    inlineCreateInsertAfterId: number | null
    inlineCreateDepth: number
    inlineCreateName: string
    showCreateMenu: boolean
}>()

const emit = defineEmits<{
    startResize: []
    updateShowKbDropdown: [value: boolean]
    switchKb: [kb: KbLike]
    updateSidebarSearchQuery: [value: string]
    searchSelect: [docId: number]
    itemClick: [doc: OrderedDocLike, event: MouseEvent]
    contextMenu: [event: MouseEvent, doc: OrderedDocLike]
    dragStart: [event: DragEvent, doc: OrderedDocLike]
    dragOver: [event: DragEvent, doc: OrderedDocLike]
    drop: [event: DragEvent, doc: OrderedDocLike]
    dragEnd: []
    containerDragOver: [event: DragEvent]
    containerDrop: [event: DragEvent]
    startInlineCreate: [type: 'file' | 'folder', parentId?: number]
    deleteDoc: [doc: OrderedDocLike]
    setRenameInputRef: [el: any]
    updateEditingName: [value: string]
    confirmRename: [doc: OrderedDocLike]
    setInlineCreateInputRef: [el: any]
    updateInlineCreateName: [value: string]
    confirmInlineCreate: []
    cancelInlineCreate: []
    goHome: []
    updateShowCreateMenu: [value: boolean]
    openTrashModal: []
    clearSelection: []
}>()

const showKbDropdownModel = computed({
    get: () => props.showKbDropdown,
    set: (value: boolean) => emit('updateShowKbDropdown', value)
})

const sidebarSearchQueryModel = computed({
    get: () => props.sidebarSearchQuery,
    set: (value: string) => emit('updateSidebarSearchQuery', value)
})

const editingNameModel = computed({
    get: () => props.editingName,
    set: (value: string) => emit('updateEditingName', value)
})

const inlineCreateNameModel = computed({
    get: () => props.inlineCreateName,
    set: (value: string) => emit('updateInlineCreateName', value)
})

const showCreateMenuModel = computed({
    get: () => props.showCreateMenu,
    set: (value: boolean) => emit('updateShowCreateMenu', value)
})

const toggleCreateMenu = () => {
    showCreateMenuModel.value = !showCreateMenuModel.value
}

const startRootCreate = (type: 'file' | 'folder') => {
    emit('startInlineCreate', type)
    showCreateMenuModel.value = false
}

const openTrash = () => {
    emit('openTrashModal')
    showCreateMenuModel.value = false
}

const { t } = useI18n()
const { isDark } = useTheme()

const hexToRgb = (hex: string) => {
    const shorthandRegex = /^#?([a-f\d])([a-f\d])([a-f\d])$/i
    hex = hex.replace(shorthandRegex, (_m, r, g, b) => {
        return r + r + g + g + b + b
    })
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
    return result ? {
        r: parseInt(result[1]!, 16),
        g: parseInt(result[2]!, 16),
        b: parseInt(result[3]!, 16)
    } : null
}

const isColorDark = (color?: string) => {
    if (!color) return false
    const rgb = hexToRgb(color)
    if (!rgb) return false
    const yiq = ((rgb.r * 299) + (rgb.g * 587) + (rgb.b * 114)) / 1000
    return yiq < 128
}

const getIconStyle = (color?: string) => {
    const baseColor = color || '#3b82f6'
    if (isDark.value && isColorDark(baseColor)) {
        return { color: '#e5e7eb' }
    }
    return { color: baseColor }
}

const getIconBgStyle = (color?: string) => {
    const baseColor = color || '#3b82f6'
    if (isDark.value && isColorDark(baseColor)) {
        return { backgroundColor: 'rgba(255, 255, 255, 0.1)' }
    }
    return { backgroundColor: `${baseColor}20` }
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
</script>

<template>
    <div v-show="visible"
        @dragover.prevent="emit('containerDragOver', $event)"
        @drop.prevent="emit('containerDrop', $event)"
        :class="isMobile
            ? 'fixed inset-y-0 left-0 z-50 flex flex-col bg-white dark:bg-[#161b22] border-r border-slate-200 dark:border-slate-800'
            : 'flex-shrink-0 border-r border-slate-200 dark:border-slate-800 flex flex-col bg-white dark:bg-[#161b22] relative group/sidebar'"
        :style="isMobile ? { width: '85vw' } : { width: sidebarWidth + 'px' }">
        <div v-if="!isMobile"
            class="absolute top-0 right-0 bottom-0 w-1 cursor-col-resize hover:bg-indigo-300 z-50 transition-colors opacity-0 group-hover/sidebar:opacity-100"
            :class="{ 'bg-indigo-400 opacity-100': isResizingSidebar }" @mousedown.prevent.stop="emit('startResize')"></div>
        <div class="h-16 px-6 border-b border-slate-200/50 dark:border-slate-800 flex items-center shrink-0 relative">
            <div @click.stop="showKbDropdownModel = !showKbDropdownModel"
                class="flex items-center flex-1 min-w-0 cursor-pointer hover:bg-slate-50 dark:hover:bg-gray-700/50 -ml-2 px-2 py-1.5 rounded-xl transition-all group/kb-title">
                <div
                    class="kb-icon-shell kb-icon-shell--sm mr-3"
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
                <h2 class="text-lg font-black text-slate-900 dark:text-gray-100 truncate tracking-tight pr-1">
                    {{ kbDetail?.title || t('editor.docList') }}
                </h2>
                <svg class="h-4 w-4 text-slate-400 group-hover/kb-title:text-indigo-500 transition-all flex-shrink-0"
                    :class="{ 'rotate-180': showKbDropdownModel }" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M19 9l-7 7-7-7" />
                </svg>
            </div>
            <div v-if="showKbDropdownModel"
                class="absolute top-full left-4 right-4 mt-1 bg-white dark:bg-[#161b22] border border-slate-200 dark:border-gray-700 rounded-2xl shadow-2xl z-[100] py-2 animate-in fade-in slide-in-from-top-2 duration-200">
                <div class="px-4 py-2 text-[10px] font-bold text-slate-400 dark:text-gray-500 uppercase tracking-wider">
                    {{ t('editor.switchKb') }}
                </div>
                <div class="max-h-[320px] overflow-y-auto scrollbar-subtle">
                    <div v-for="kb in editableKbs" :key="kb.id" @click="emit('switchKb', kb)"
                        class="px-4 py-2.5 flex items-center space-x-3 cursor-pointer select-none transition-all hover:bg-slate-50 dark:hover:bg-gray-700/50"
                        :class="{ 'bg-indigo-50/50 dark:bg-indigo-900/20': kb.id === kbId }">
                        <div class="kb-icon-shell kb-icon-shell--sm" :style="getIconBgStyle(kb.color)">
                            <template v-if="getKbIcon(kb.icon)">
                                <img v-if="getKbIcon(kb.icon)?.type === 'image'" :src="String(getKbIcon(kb.icon)?.value)"
                                    class="kb-icon-glyph kb-icon-glyph--sm object-cover rounded" alt="kb-icon" />
                                <component v-else :is="getKbIcon(kb.icon)?.value" class="kb-icon-glyph kb-icon-glyph--sm"
                                    :style="getIconStyle(kb.color)" />
                            </template>
                            <component v-else :is="(Icons as any).Book" class="kb-icon-glyph kb-icon-glyph--sm"
                                :style="getIconStyle(kb.color)" />
                        </div>
                        <span class="text-sm font-medium truncate"
                            :class="kb.id === kbId ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-700 dark:text-gray-300'">
                            {{ kb.title }}
                        </span>
                        <div v-if="kb.id === kbId" class="flex-1 flex justify-end">
                            <svg class="h-4 w-4 text-indigo-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5"
                                    d="M5 13l4 4L19 7" />
                            </svg>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="px-6 py-4 bg-white dark:bg-[#161b22]">
            <div class="relative group">
                <input v-model="sidebarSearchQueryModel" type="text"
                    class="w-full pl-9 pr-3 py-2 text-xs bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 focus:bg-white dark:focus:bg-slate-800 outline-none transition-all text-slate-900 dark:text-slate-100"
                    :placeholder="t('editor.searchCatalog')" />
                <Search
                    class="h-4 w-4 text-slate-400 absolute left-3 top-2 group-focus-within:text-indigo-500 transition-colors" />
                <button v-if="sidebarSearchQueryModel" @click="sidebarSearchQueryModel = ''"
                    class="absolute right-2 top-2 text-slate-400 hover:text-slate-600 transition-colors">
                    <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                </button>
            </div>
        </div>

        <div class="flex-1 overflow-y-auto py-2 px-3 scrollbar-subtle" @click="emit('clearSelection')"
            @dragover.prevent="emit('containerDragOver', $event)" @drop.prevent="emit('containerDrop', $event)">
            <div v-if="loading" class="flex justify-center py-8">
                <div class="animate-spin rounded-full h-6 w-6 border-2 border-indigo-600 border-t-transparent"></div>
            </div>
            <div v-else-if="documents.length === 0 && !inlineCreating"
                class="px-6 py-20 flex flex-col items-center justify-center space-y-6">
                <div
                    class="p-6 bg-slate-50 dark:bg-[#161b22]/50 rounded-3xl border border-dotted border-slate-200 dark:border-gray-700 animate-pulse">
                    <svg class="h-12 w-12 text-slate-300 dark:text-gray-600" fill="none" viewBox="0 0 24 24"
                        stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1"
                            d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                    </svg>
                </div>
                <div class="text-center space-y-2">
                    <p class="text-base font-black text-slate-900 dark:text-gray-100 tracking-tight">{{ t('editor.emptyTitle') }}</p>
                    <p class="text-xs text-slate-400 dark:text-gray-500 leading-relaxed max-w-[180px] mx-auto">
                        {{ t('editor.emptyDescPrefix') }} <span class="text-indigo-500 font-bold">+</span> {{ t('editor.emptyDescSuffix') }}
                    </p>
                </div>
                <div class="pt-8 animate-bounce">
                    <svg class="h-5 w-5 text-indigo-400/50" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                            d="M19 14l-7 7m0 0l-7-7m7 7V3" />
                    </svg>
                </div>
            </div>
            <div v-else class="space-y-1.5">
                <SearchResultList v-if="sidebarSearchQueryModel.trim()" :results="searchResults" :loading="searchLoading"
                    :query="sidebarSearchQueryModel" @select="emit('searchSelect', $event)" />
                <template v-else>
                    <template v-for="doc in orderedDocuments" :key="doc.__key ?? doc.id">
                        <div :data-doc-id="doc.id" @click.stop="emit('itemClick', doc, $event)"
                            @contextmenu.prevent="emit('contextMenu', $event, doc)"
                            :draggable="!isMobile && !sidebarSearchQueryModel.trim() ? true : undefined"
                            @dragstart="emit('dragStart', $event, doc)" @dragover.prevent.stop="emit('dragOver', $event, doc)"
                            @drop.prevent.stop="emit('drop', $event, doc)" @dragend="emit('dragEnd')"
                            class="group w-full text-left px-2 py-2 rounded-xl text-sm transition-all flex items-center cursor-pointer select-none relative"
                            :class="[
                                selectedDocIds.has(doc.id)
                                    ? 'bg-indigo-50 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 font-black shadow-sm ring-1 ring-indigo-200 dark:ring-indigo-800'
                                    : 'text-slate-600 dark:text-slate-400 active:bg-white/50 dark:active:bg-slate-800/50 sm:hover:bg-white/50 sm:dark:hover:bg-slate-800/50',
                                dragState.draggingDoc?.id === doc.id && 'opacity-40',
                                dragState.draggingDoc && selectedDocIds.has(doc.id) && 'opacity-40',
                                dragState.overDocId === doc.id && dragState.dropPosition === 'inside' && 'drag-drop-inside'
                            ]"
                            :style="{ paddingLeft: (doc.depth * 16 + 8) + 'px' }">
                            <div v-if="dragState.overDocId === doc.id && dragState.dropPosition === 'before'"
                                class="absolute -top-1 left-2 right-2 h-0.5 bg-indigo-500 rounded-full pointer-events-none z-10" />
                            <div v-if="dragState.overDocId === doc.id && dragState.dropPosition === 'after'"
                                class="absolute -bottom-1 left-2 right-2 h-0.5 bg-indigo-500 rounded-full pointer-events-none z-10" />
                            <DocumentTreeNodeLabel :doc="doc" :expanded="expandedFolders.has(doc.id)"
                                folder-chevron-class="mr-1.5 text-slate-400 flex items-center justify-center"
                                folder-icon-class="mr-2 shrink-0 text-blue-500 dark:text-blue-400"
                                file-icon-class="mr-2 shrink-0 text-slate-400" file-placeholder-class="w-5 flex-shrink-0">
                                <template #name>
                                    <div v-if="editingDocId === doc.id" class="flex-1 min-w-0" @click.stop>
                                        <input :ref="(el) => emit('setRenameInputRef', el)" v-model="editingNameModel"
                                            type="text"
                                            class="doc-sidebar-inline-input w-full px-2 py-0.5 text-sm bg-slate-50 dark:bg-slate-900 border-2 border-indigo-500 dark:border-indigo-400 rounded shadow-sm focus:ring-2 focus:ring-indigo-200 dark:focus:ring-indigo-500/30 outline-none text-slate-900 dark:text-slate-100 placeholder-slate-400 dark:placeholder-slate-500 selection:bg-indigo-200 dark:selection:bg-indigo-700 selection:text-indigo-900 dark:selection:text-slate-100"
                                            @blur="emit('confirmRename', doc)" @keyup.enter="emit('confirmRename', doc)"
                                            @click.stop />
                                    </div>
                                    <span v-else class="block truncate">{{ doc.name }}</span>
                                </template>
                            </DocumentTreeNodeLabel>
                            <div
                                :class="[isMobile ? 'opacity-100' : 'opacity-0 group-hover:opacity-100', 'flex items-center gap-1 ml-2 shrink-0']">
                                <template v-if="isMobile">
                                    <button @click.stop="emit('contextMenu', $event, doc)"
                                        class="p-1 hover:bg-slate-300 dark:hover:bg-slate-700 rounded text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white"
                                        :title="t('editor.moreOptions')">
                                        <MoreHorizontal :size="14" />
                                    </button>
                                </template>
                                <template v-else>
                                    <template v-if="doc.type === 'folder'">
                                        <button @click.stop="emit('startInlineCreate', 'file', doc.id)"
                                            class="p-1 hover:bg-slate-300 dark:hover:bg-slate-700 rounded text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white"
                                            :title="t('editor.newDoc')">
                                            <FilePlus :size="14" />
                                        </button>
                                        <button @click.stop="emit('startInlineCreate', 'folder', doc.id)"
                                            class="p-1 hover:bg-slate-300 dark:hover:bg-slate-700 rounded text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white"
                                            :title="t('editor.newFolder')">
                                            <FolderPlus :size="14" />
                                        </button>
                                    </template>
                                </template>
                            </div>
                        </div>

                        <div v-if="inlineCreating && doc.id === inlineCreateInsertAfterId"
                            class="w-full text-left px-2 py-1.5 rounded-md text-sm flex items-center bg-slate-200/50 dark:bg-slate-800/50"
                            :style="{ paddingLeft: (inlineCreateDepth * 16 + 8) + 'px' }">
                            <span class="w-5 mr-1.5 flex-shrink-0"></span>
                            <template v-if="inlineCreating.type === 'folder'">
                                <Folder :size="16" class="mr-2 text-blue-500 dark:text-blue-400 shrink-0" />
                            </template>
                            <template v-else>
                                <FileText :size="16" class="mr-2 text-slate-400 shrink-0" />
                            </template>
                            <div class="flex-1 min-w-0" @click.stop>
                                <input :ref="(el) => emit('setInlineCreateInputRef', el)" v-model="inlineCreateNameModel"
                                    type="text"
                                    class="doc-sidebar-inline-input w-full px-2 py-0.5 text-sm bg-slate-50 dark:bg-slate-900 border-2 border-indigo-500 dark:border-indigo-400 rounded shadow-sm focus:ring-2 focus:ring-indigo-200 dark:focus:ring-indigo-500/30 outline-none text-slate-900 dark:text-slate-100 placeholder-slate-400 dark:placeholder-slate-500 selection:bg-indigo-200 dark:selection:bg-indigo-700 selection:text-indigo-900 dark:selection:text-slate-100"
                                    @blur="emit('confirmInlineCreate')" @keyup.enter="emit('confirmInlineCreate')"
                                    @keyup.escape="emit('cancelInlineCreate')" @click.stop />
                            </div>
                        </div>
                    </template>

                    <div v-if="inlineCreating && inlineCreateInsertAfterId === null"
                        class="w-full text-left px-2 py-1.5 rounded-md text-sm flex items-center bg-slate-200/50 dark:bg-slate-800/50"
                        :style="{ paddingLeft: (inlineCreateDepth * 16 + 8) + 'px' }">
                        <span class="w-5 mr-1.5 flex-shrink-0"></span>
                        <template v-if="inlineCreating.type === 'folder'">
                            <Folder :size="16" class="mr-2 text-blue-500 dark:text-blue-400 shrink-0" />
                        </template>
                        <template v-else>
                            <FileText :size="16" class="mr-2 text-slate-400 shrink-0" />
                        </template>
                        <div class="flex-1 min-w-0" @click.stop>
                            <input :ref="(el) => emit('setInlineCreateInputRef', el)" v-model="inlineCreateNameModel"
                                type="text"
                                class="doc-sidebar-inline-input w-full px-2 py-0.5 text-sm bg-slate-50 dark:bg-slate-900 border-2 border-indigo-500 dark:border-indigo-400 rounded shadow-sm focus:ring-2 focus:ring-indigo-200 dark:focus:ring-indigo-500/30 outline-none text-slate-900 dark:text-slate-100 placeholder-slate-400 dark:placeholder-slate-500 selection:bg-indigo-200 dark:selection:bg-indigo-700 selection:text-indigo-900 dark:selection:text-slate-100"
                                @blur="emit('confirmInlineCreate')" @keyup.enter="emit('confirmInlineCreate')"
                                @keyup.escape="emit('cancelInlineCreate')" @click.stop />
                        </div>
                    </div>
                </template>
            </div>
        </div>

        <div
            class="px-4 py-2.5 border-t border-slate-200/50 dark:border-slate-800 flex items-center justify-between bg-white dark:bg-[#161b22] shrink-0 z-10">
            <button @click="emit('goHome')"
                class="text-slate-500 dark:text-gray-400 hover:text-indigo-600 p-1.5 rounded-xl hover:bg-slate-50 dark:hover:bg-gray-700 transition-all hover:-translate-x-0.5 flex items-center space-x-2"
                :title="t('editor.backHome')">
                <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
                </svg>
                <span class="text-sm font-bold">{{ t('editor.back') }}</span>
            </button>
            <div class="relative">
                <button @click.stop="toggleCreateMenu"
                    class="text-slate-500 dark:text-gray-400 hover:text-indigo-600 p-1.5 rounded-xl hover:bg-slate-50 dark:hover:bg-gray-700 transition-all"
                    :class="{ 'bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400': showCreateMenuModel }"
                    :title="t('editor.new')">
                    <Plus class="h-5 w-5 transition-transform duration-200" :class="{ 'rotate-45': showCreateMenuModel }" />
                </button>
                <div v-if="showCreateMenuModel"
                    class="absolute bottom-full right-0 mb-2 w-44 bg-white dark:bg-[#161b22] border border-slate-200 dark:border-gray-700 rounded-xl shadow-xl z-50 py-1.5 animate-in fade-in slide-in-from-bottom-2 duration-200">
                    <button @click="startRootCreate('folder')"
                        class="w-full text-left px-4 py-2.5 text-sm font-medium hover:bg-slate-50 dark:hover:bg-gray-700 text-slate-700 dark:text-gray-200 transition-colors flex items-center space-x-3">
                        <div class="relative flex-shrink-0 flex items-center justify-center">
                            <svg class="h-4 w-4 text-blue-500" viewBox="0 0 24 24" fill="currentColor">
                                <path d="M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.93a2 2 0 0 1-1.66-.9l-.82-1.2A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z" />
                            </svg>
                            <Plus :size="10" class="absolute text-white stroke-[3.5px]" />
                        </div>
                        <span>{{ t('editor.newRootFolder') }}</span>
                    </button>
                    <button @click="startRootCreate('file')"
                        class="w-full text-left px-4 py-2.5 text-sm font-medium hover:bg-slate-50 dark:hover:bg-gray-700 text-slate-700 dark:text-gray-200 transition-colors flex items-center space-x-3">
                        <FileText :size="16" class="text-slate-400 dark:text-slate-500 flex-shrink-0" />
                        <span>{{ t('editor.newRootDoc') }}</span>
                    </button>
                    <div class="my-1 border-t border-slate-100 dark:border-gray-800"></div>
                    <button @click="openTrash"
                        class="w-full text-left px-4 py-2.5 text-sm font-medium hover:bg-slate-50 dark:hover:bg-gray-700 text-slate-700 dark:text-gray-200 transition-colors flex items-center space-x-3">
                        <Trash2 :size="16" class="text-rose-500 flex-shrink-0" />
                        <span>{{ t('editor.trashTitle') }}</span>
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
@import '../../styles/kb-icon.css';

.drag-drop-inside {
    background-color: rgba(79, 70, 229, 0.1) !important;
    box-shadow: inset 0 0 0 2px rgba(79, 70, 229, 0.4) !important;
}

.dark .drag-drop-inside {
    background-color: rgba(99, 102, 241, 0.15) !important;
    box-shadow: inset 0 0 0 2px rgba(99, 102, 241, 0.4) !important;
}

/* 优化指示线阴影增强可见度 */
.absolute.bg-indigo-500 {
    box-shadow: 0 0 4px rgba(79, 70, 229, 0.4);
}

</style>
