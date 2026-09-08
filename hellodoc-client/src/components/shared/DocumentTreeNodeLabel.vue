<script setup lang="ts">
import { ChevronDown, ChevronRight, Folder, FolderOpen, FileText, Check } from 'lucide-vue-next'

interface TreeNodeLike {
    id: number
    name: string
    type: string
    status?: string
    isCover?: boolean
    extraMeta?: Record<string, any>
}

const props = withDefaults(defineProps<{
    doc: TreeNodeLike
    expanded: boolean
    folderChevronClass?: string
    folderIconClass?: string
    fileIconClass?: string
    filePlaceholderClass?: string
}>(), {
    folderChevronClass: 'mr-1.5 text-slate-400 shrink-0 flex items-center justify-center',
    folderIconClass: 'mr-2 shrink-0 text-blue-500 dark:text-blue-400',
    fileIconClass: 'mr-2 shrink-0 text-slate-400',
    filePlaceholderClass: 'w-5 shrink-0'
})

const getFileIconClass = (doc: TreeNodeLike) => {
    if (doc.isCover) {
        return 'shrink-0 text-yellow-500 dark:text-yellow-400'
    }
    if (doc.status === 'published') {
        return 'shrink-0 text-emerald-600 dark:text-emerald-400'
    }
    return props.fileIconClass
}

const getIconColor = (doc: TreeNodeLike) => doc.extraMeta?.iconColor

const getFolderIconStyle = (doc: TreeNodeLike) => {
    const color = getIconColor(doc)
    if (color) {
        return { color, fill: color }
    }
    return undefined
}

const getFileIconStyle = (doc: TreeNodeLike) => {
    const color = getIconColor(doc)
    if (color) {
        return { color, fill: color }
    }
    return undefined
}

const isPublishedFile = (doc: TreeNodeLike) => doc.type === 'file' && doc.status === 'published' && !doc.isCover
</script>

<template>
    <div class="flex items-center flex-1 min-w-0">
        <span v-if="doc.type === 'folder'" :class="folderChevronClass" class="w-5 h-5 shrink-0 flex items-center justify-center">
            <ChevronDown v-if="expanded" :size="14" />
            <ChevronRight v-else :size="14" />
        </span>
        <span v-else :class="filePlaceholderClass" class="w-5 h-5 shrink-0"></span>

        <!-- 文件夹图标容器 (保证统一基准与舒适的 mr-3 间距) -->
        <span v-if="doc.type === 'folder'" class="w-5 h-5 mr-3 shrink-0 flex items-center justify-center">
            <component :is="expanded ? FolderOpen : Folder" :size="16"
                :class="[doc.isCover ? 'text-yellow-500 dark:text-yellow-400' : folderIconClass]"
                :style="getFolderIconStyle(doc)" />
        </span>

        <!-- 文件图标容器 (保证与文件夹图标完全相同的宽度与 mr-3 间距) -->
        <template v-else>
            <span class="relative w-5 h-5 mr-3 shrink-0 flex items-center justify-center">
                <FileText :size="16"
                    :class="getFileIconClass(doc)"
                    :style="getFileIconStyle(doc)" />
                <span v-if="isPublishedFile(doc)"
                    class="absolute -bottom-0.5 -right-0.5 flex h-2.5 w-2.5 items-center justify-center rounded-full bg-emerald-500 text-white ring-1 ring-white dark:ring-slate-900">
                    <Check :size="8" class="stroke-[3]" />
                </span>
            </span>
        </template>

        <div class="min-w-0 flex-1 overflow-hidden">
            <slot name="name">
                <span class="block truncate">{{ doc.name }}</span>
            </slot>
        </div>
    </div>
</template>
