<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Editor } from '@tiptap/vue-3'

const props = defineProps<{
    editor?: Editor
    isPlainTextMode: boolean
    isPreviewMode: boolean
    pageMode: string
    paperBgColor?: string
    showPaperColorButton?: boolean
    isExportingWord: boolean
    isFormatBrushActive: boolean
}>()

const emit = defineEmits<{
    'update:pageMode': [val: string]
    'togglePlainTextMode': []
    'togglePreviewMode': []
    'toggleFormatBrush': []
    'exportWord': []
    'uploadImg': [files: File[], callback: (urls: string[]) => void]
    'update:paperBgColor': [val: string]
}>()

const { t } = useI18n()
const fileInputRef = ref<HTMLInputElement | null>(null)
const showPaperColorPicker = ref(false)
const paperColorPresets = [
    '#ffffff',
    '#faf9de',
    '#f5f5dc',
    '#c7edcc',
    '#f7f7f7',
    '#f2f2f2'
]

const toggleBold = () => props.editor?.chain().focus().toggleBold().run()
const toggleItalic = () => props.editor?.chain().focus().toggleItalic().run()
const toggleStrike = () => props.editor?.chain().focus().toggleStrike().run()
const toggleUnderline = () => props.editor?.chain().focus().toggleUnderline().run()
const toggleHighlight = () => props.editor?.chain().focus().toggleHighlight().run()
const toggleCode = () => props.editor?.chain().focus().toggleCode().run()
const setAlignLeft = () => props.editor?.chain().focus().setTextAlign('left').run()
const setAlignCenter = () => props.editor?.chain().focus().setTextAlign('center').run()
const setAlignRight = () => props.editor?.chain().focus().setTextAlign('right').run()
const setParagraph = () => props.editor?.chain().focus().clearNodes().setParagraph().run()
const setHeading = (level: any) => props.editor?.chain().focus().setHeading({ level }).run()
const toggleHeading = (level: any) => props.editor?.chain().focus().toggleHeading({ level }).run()

const useBlockTypeDropdown = true
const getCurrentBlockType = () => {
    if (!props.editor) return 'paragraph'
    for (const level of [1, 2, 3, 4, 5, 6]) {
        if (props.editor.isActive('heading', { level })) return `h${level}`
    }
    return 'paragraph'
}
const onBlockTypeChange = (event: Event) => {
    const value = (event.target as HTMLSelectElement).value
    if (value === 'paragraph') {
        setParagraph()
        return
    }
    const level = Number(value.replace('h', ''))
    if (level >= 1 && level <= 6) setHeading(level)
}

const toggleBulletList = () => props.editor?.chain().focus().toggleBulletList().run()
const toggleOrderedList = () => props.editor?.chain().focus().toggleOrderedList().run()
const toggleTaskList = () => props.editor?.chain().focus().toggleTaskList().run()
const toggleBlockquote = () => props.editor?.chain().focus().toggleBlockquote().run()
const toggleCodeBlock = () => props.editor?.chain().focus().toggleCodeBlock().run()
const undo = () => props.editor?.chain().focus().undo().run()
const redo = () => props.editor?.chain().focus().redo().run()

const exportElapsedSeconds = ref(0)
let exportTimer: ReturnType<typeof setInterval> | null = null

watch(() => props.isExportingWord, (isExporting) => {
    if (isExporting) {
        exportElapsedSeconds.value = 0
        exportTimer = setInterval(() => {
            exportElapsedSeconds.value++
        }, 1000)
    } else {
        if (exportTimer) {
            clearInterval(exportTimer)
            exportTimer = null
        }
    }
})

onUnmounted(() => {
    if (exportTimer) clearInterval(exportTimer)
})

const togglePlainTextMode = () => {
    emit('togglePlainTextMode')
}

const toggleFormatBrush = () => {
    emit('toggleFormatBrush')
}

const togglePreviewMode = () => {
    emit('togglePreviewMode')
}

const exportWord = () => {
    emit('exportWord')
}

const togglePageMode = () => {
    let nextMode = 'full'
    if (props.pageMode === 'full') nextMode = 'portrait'
    else if (props.pageMode === 'portrait') nextMode = 'landscape'
    else nextMode = 'full'
    emit('update:pageMode', nextMode)
}

const openPaperColorPicker = () => {
    showPaperColorPicker.value = !showPaperColorPicker.value
}

const handlePaperColorChange = (value: string) => {
    emit('update:paperBgColor', value)
    showPaperColorPicker.value = false
}

const addLink = () => {
    const url = window.prompt(t('editor.toolbar.inputLinkUrl'))
    if (url) {
        props.editor?.chain().focus().setLink({ href: url }).run()
    }
}

const addImage = () => {
    fileInputRef.value?.click()
}

const handleFileChange = (event: Event) => {
    const files = (event.target as HTMLInputElement).files
    if (!files || files.length === 0) return

    const fileList = Array.from(files)
    emit('uploadImg', fileList, (urls) => {
        if (urls && urls.length > 0 && props.editor) {
            let htmlContent = ''
            urls.forEach((url, index) => {
                const file = fileList[index]
                const isImage = file?.type?.startsWith('image/') || 
                                /\.(jpg|jpeg|png|gif|webp|svg|bmp)(\?.*)?$/i.test(url)
                
                if (isImage) {
                    htmlContent += `<img src="${url}" />`
                } else {
                    const defaultFileText = t('toolbar.file') || 'File'
                    const fileName = file?.name || url.split('/').pop()?.split('?')[0] || defaultFileText
                    htmlContent += `<p><a href="${url}" target="_blank" class="file-link">${fileName}</a></p>`
                }
            })
            props.editor.chain().focus().insertContent(htmlContent).run()
        }
        if (fileInputRef.value) fileInputRef.value.value = ''
    })
}

const showTableSelector = ref(false)
const hoverRows = ref(0)
const hoverCols = ref(0)

const onTableHover = (r: number, c: number) => {
    hoverRows.value = r
    hoverCols.value = c
}

const insertTableWithGrid = () => {
    if (hoverRows.value > 0 && hoverCols.value > 0) {
        props.editor?.chain().focus().insertTable({ rows: hoverRows.value, cols: hoverCols.value, withHeaderRow: true }).run()
        showTableSelector.value = false
    }
}

const addTable = () => {
    showTableSelector.value = !showTableSelector.value
}

const closeDropdowns = () => {
    showTableSelector.value = false
    showPaperColorPicker.value = false
}

defineExpose({ closeDropdowns })
</script>

<template>
    <div v-if="editor" class="visual-toolbar w-full flex flex-wrap items-center gap-1 px-4 py-2 border-b border-gray-200 dark:border-gray-700 sticky top-0 z-10 bg-white dark:bg-[#161b22]" @click.stop>
        <!-- Hidden File Input -->
        <input type="file" ref="fileInputRef" class="hidden" accept="image/*" multiple @change="handleFileChange" />

        <div class="toolbar-group flex gap-0.5 px-2 border-r border-gray-200 dark:border-gray-700">
            <button @click="togglePlainTextMode" :class="{ 'is-active': isPlainTextMode }" :title="t('editor.toolbar.togglePlainTextMode')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M19 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2"/>
                    <path d="M9 7l-3 5 3 5"/>
                    <path d="M15 7l3 5-3 5"/>
                </svg>
            </button>
            <button
                v-if="!isPlainTextMode"
                @click="togglePreviewMode"
                :class="{ 'is-active': isPreviewMode }"
                :title="isPreviewMode ? t('editor.toolbar.exitDemoMode') : t('editor.toolbar.demoMode')"
                class="toolbar-btn"
            >
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="3" y="4" width="18" height="12" rx="2"/>
                    <path d="M8 20h8"/>
                    <path d="M12 16v4"/>
                    <path d="m10 8 5 2-5 2z"/>
                </svg>
            </button>
        </div>
        <template v-if="!isPlainTextMode">
        <div class="toolbar-group flex gap-0.5 px-2 border-r border-gray-200 dark:border-gray-700">
            <button @click="toggleBold" :class="{ 'is-active': editor.isActive('bold') }" :title="t('editor.toolbar.bold')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 4h8a4 4 0 0 1 4 4 4 4 0 0 1-4 4H6z"/><path d="M6 12h9a4 4 0 0 1 4 4 4 4 0 0 1-4 4H6z"/></svg>
            </button>
            <button @click="toggleUnderline" :class="{ 'is-active': editor.isActive('underline') }" :title="t('editor.toolbar.underline')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 4v6a6 6 0 0 0 12 0V4"/><line x1="4" x2="20" y1="20" y2="20"/></svg>
            </button>
            <button @click="toggleHighlight" :class="{ 'is-active': editor.isActive('highlight') }" :title="t('editor.toolbar.highlight')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 11 8-8 4 4-8 8"/><path d="M4 20h4l9-9"/><path d="M2 22h20"/></svg>
            </button>
            <button @click="toggleItalic" :class="{ 'is-active': editor.isActive('italic') }" :title="t('editor.toolbar.italic')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="19" x2="10" y1="4" y2="4"/><line x1="14" x2="5" y1="20" y2="20"/><line x1="15" x2="9" y1="4" y2="20"/></svg>
            </button>
            <button @click="toggleStrike" :class="{ 'is-active': editor.isActive('strike') }" :title="t('editor.toolbar.strike')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 4H9a3 3 0 0 0-2.83 4"/><path d="M14 12a4 4 0 0 1 0 8H6"/><line x1="4" x2="20" y1="12" y2="12"/></svg>
            </button>
            <button @click="toggleFormatBrush" :class="{ 'is-active': isFormatBrushActive }" :title="isFormatBrushActive ? t('editor.toolbar.cancelFormatBrush') : t('editor.toolbar.formatBrush')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="12" height="4" x="6" y="2" rx="1"/><path d="M6 6v4a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V6"/><path d="M11 12v4"/><path d="M13 12v4"/><rect width="6" height="4" x="9" y="16" rx="1"/></svg>
            </button>
        </div>

        <div class="toolbar-group flex gap-0.5 px-2 border-r border-gray-200 dark:border-gray-700">
            <select
                v-if="useBlockTypeDropdown"
                class="toolbar-select"
                :title="t('editor.toolbar.blockType')"
                :value="getCurrentBlockType()"
                @change="onBlockTypeChange"
            >
                <option value="paragraph">P</option>
                <option value="h1">H1</option>
                <option value="h2">H2</option>
                <option value="h3">H3</option>
                <option value="h4">H4</option>
                <option value="h5">H5</option>
                <option value="h6">H6</option>
            </select>
            <template v-else>
                <button @click="setParagraph" :class="{ 'is-active': editor.isActive('paragraph') }" :title="t('editor.toolbar.paragraph')" class="toolbar-btn">P</button>
                <button @click="toggleHeading(1)" :class="{ 'is-active': editor.isActive('heading', { level: 1 }) }" :title="t('editor.toolbar.heading1')" class="toolbar-btn">H1</button>
                <button @click="toggleHeading(2)" :class="{ 'is-active': editor.isActive('heading', { level: 2 }) }" :title="t('editor.toolbar.heading2')" class="toolbar-btn">H2</button>
                <button @click="toggleHeading(3)" :class="{ 'is-active': editor.isActive('heading', { level: 3 }) }" :title="t('editor.toolbar.heading3')" class="toolbar-btn">H3</button>
                <button @click="toggleHeading(4)" :class="{ 'is-active': editor.isActive('heading', { level: 4 }) }" :title="t('editor.toolbar.heading4')" class="toolbar-btn">H4</button>
                <button @click="toggleHeading(5)" :class="{ 'is-active': editor.isActive('heading', { level: 5 }) }" :title="t('editor.toolbar.heading5')" class="toolbar-btn">H5</button>
                <button @click="toggleHeading(6)" :class="{ 'is-active': editor.isActive('heading', { level: 6 }) }" :title="t('editor.toolbar.heading6')" class="toolbar-btn">H6</button>
            </template>
        </div>

        <div class="toolbar-group flex gap-0.5 px-2 border-r border-gray-200 dark:border-gray-700">
            <button @click="setAlignLeft" :class="{ 'is-active': editor.isActive({ textAlign: 'left' }) }" :title="t('editor.toolbar.alignLeft')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="3" x2="21" y1="6" y2="6"/><line x1="3" x2="15" y1="12" y2="12"/><line x1="3" x2="19" y1="18" y2="18"/></svg>
            </button>
            <button @click="setAlignCenter" :class="{ 'is-active': editor.isActive({ textAlign: 'center' }) }" :title="t('editor.toolbar.alignCenter')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="3" x2="21" y1="6" y2="6"/><line x1="6" x2="18" y1="12" y2="12"/><line x1="4" x2="20" y1="18" y2="18"/></svg>
            </button>
            <button @click="setAlignRight" :class="{ 'is-active': editor.isActive({ textAlign: 'right' }) }" :title="t('editor.toolbar.alignRight')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="3" x2="21" y1="6" y2="6"/><line x1="9" x2="21" y1="12" y2="12"/><line x1="5" x2="21" y1="18" y2="18"/></svg>
            </button>
        </div>

        <div class="toolbar-group flex gap-0.5 px-2 border-r border-gray-200 dark:border-gray-700">
            <button @click="toggleBulletList" :class="{ 'is-active': editor.isActive('bulletList') }" :title="t('editor.toolbar.bulletList')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="8" x2="21" y1="6" y2="6"/><line x1="8" x2="21" y1="12" y2="12"/><line x1="8" x2="21" y1="18" y2="18"/><line x1="3" x2="3.01" y1="6" y2="6"/><line x1="3" x2="3.01" y1="12" y2="12"/><line x1="3" x2="3.01" y1="18" y2="18"/></svg>
            </button>
            <button @click="toggleOrderedList" :class="{ 'is-active': editor.isActive('orderedList') }" :title="t('editor.toolbar.orderedList')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="10" x2="21" y1="6" y2="6"/><line x1="10" x2="21" y1="12" y2="12"/><line x1="10" x2="21" y1="18" y2="18"/><path d="M4 6h1v4"/><path d="M4 10h2"/><path d="M6 18H4c0-1 2-2 2-3s-1-1.5-2-1"/></svg>
            </button>
            <button @click="toggleTaskList" :class="{ 'is-active': editor.isActive('taskList') }" :title="t('editor.toolbar.taskList')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="6" height="6" x="3" y="5" rx="1"/><path d="m3 17 2 2 4-4"/><line x1="13" x2="21" y1="6" y2="6"/><line x1="13" x2="21" y1="12" y2="12"/><line x1="13" x2="21" y1="18" y2="18"/></svg>
            </button>
        </div>

        <div class="toolbar-group flex gap-0.5 px-2 border-r border-gray-200 dark:border-gray-700">
            <button @click="toggleBlockquote" :class="{ 'is-active': editor.isActive('blockquote') }" :title="t('editor.toolbar.blockquote')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 6H3"/><path d="M21 12H8"/><path d="M21 18H8"/><path d="M3 12v6"/></svg>
            </button>
            <button @click="toggleCode" :class="{ 'is-active': editor.isActive('code') }" :title="t('editor.toolbar.inlineCode')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>
            </button>
            <button @click="toggleCodeBlock" :class="{ 'is-active': editor.isActive('codeBlock') }" :title="t('editor.toolbar.codeBlock')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="18" x="3" y="3" rx="2"/><path d="m10 10-2 2 2 2"/><path d="m14 14 2-2-2-2"/></svg>
            </button>
            <button @click="addLink" :class="{ 'is-active': editor.isActive('link') }" :title="t('editor.toolbar.insertLink')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
            </button>
            <button @click="addImage" :title="t('editor.toolbar.insertImage')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="18" x="3" y="3" rx="2" ry="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/></svg>
            </button>
            <div class="relative">
                <button @click="addTable" :title="t('editor.toolbar.insertTable')" class="toolbar-btn" :class="{ 'is-active': showTableSelector }">
                    <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="18" x="3" y="3" rx="2"/><path d="M3 9h18"/><path d="M3 15h18"/><path d="M9 3v18"/><path d="M15 3v18"/></svg>
                </button>
                
                <!-- Table Selector Popover -->
                <div v-if="showTableSelector" class="absolute top-full left-0 mt-1 p-3 bg-white dark:bg-[#1f2937] border border-gray-200 dark:border-gray-700 shadow-xl rounded-lg z-50">
                    <div class="mb-2 text-[10px] font-bold text-gray-400 uppercase tracking-wider flex justify-between items-center">
                        <span>{{ t('editor.toolbar.tableSize') }}</span>
                        <span class="text-blue-500 bg-blue-50 dark:bg-blue-900/30 px-1.5 py-0.5 rounded">{{ hoverRows }} x {{ hoverCols }}</span>
                    </div>
                    <div class="flex flex-col gap-1">
                        <div v-for="r in 10" :key="r" class="flex gap-1">
                            <div 
                                v-for="c in 10" :key="c"
                                @mouseover="onTableHover(r, c)"
                                @click="insertTableWithGrid"
                                :class="['w-4 h-4 border transition-all duration-150 cursor-pointer rounded-sm', 
                                         r <= hoverRows && c <= hoverCols ? 'bg-blue-500 border-blue-600 scale-110 z-10' : 'bg-gray-100 dark:bg-gray-800 border-gray-200 dark:border-gray-700 hover:border-blue-300']"
                            ></div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="toolbar-group flex gap-0.5 px-2 border-r border-gray-200 dark:border-gray-700">
            <button @click="undo" :title="t('editor.toolbar.undo')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 7v6h6"/><path d="M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13"/></svg>
            </button>
            <button @click="redo" :title="t('editor.toolbar.redo')" class="toolbar-btn">
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 7v6h-6"/><path d="M3 17a9 9 0 0 1 9-9 9 9 0 0 1 6 2.3l3 2.7"/></svg>
            </button>
        </div>
        </template>

        <div class="toolbar-group flex gap-0.5 px-2">
            <div class="relative" v-if="!isPlainTextMode && (showPaperColorButton ?? true)">
                <button
                    @click="openPaperColorPicker"
                    :title="t('toolbar.paperColor')"
                    class="toolbar-btn"
                    :class="{ 'is-active': !!paperBgColor && paperBgColor !== '#ffffff' }"
                >
                    <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M12 3a9 9 0 1 0 0 18h1a2 2 0 0 0 0-4h-1a2 2 0 0 1 0-4h2a4 4 0 0 0 4-4 6 6 0 0 0-6-6z" />
                        <circle cx="7.5" cy="10" r="1" />
                        <circle cx="10" cy="7.5" r="1" />
                        <circle cx="14" cy="7.5" r="1" />
                        <circle cx="16.5" cy="10" r="1" />
                    </svg>
                </button>
                <div v-if="showPaperColorPicker" class="absolute top-full right-0 mt-1 p-2 bg-white dark:bg-[#1f2937] border border-gray-200 dark:border-gray-700 shadow-xl rounded-lg z-50">
                    <div class="paper-color-grid">
                        <button
                            v-for="color in paperColorPresets"
                            :key="color"
                            class="paper-color-item"
                            :class="{ 'is-active': (paperBgColor || '#ffffff') === color }"
                            :style="{ backgroundColor: color }"
                            @click="handlePaperColorChange(color)"
                            :title="color"
                        ></button>
                    </div>
                </div>
            </div>
            <button
                v-if="!isPlainTextMode"
                @click="exportWord"
                :title="isExportingWord ? t('editor.toolbar.exportingWord') : t('editor.toolbar.exportWord')"
                class="toolbar-btn relative"
                :class="{ 'opacity-50 cursor-not-allowed': isExportingWord }"
                :disabled="isExportingWord"
            >
                <template v-if="isExportingWord">
                    <svg class="toolbar-icon animate-spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M21 12a9 9 0 1 1-6.219-8.56" />
                    </svg>
                    <span class="absolute inset-0 flex items-center justify-center text-[9px] font-mono leading-none pt-px">{{ exportElapsedSeconds }}</span>
                </template>
                <svg v-else viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <path d="M14 2v6h6"/>
                    <path d="M12 18v-6"/>
                    <path d="m9 15 3 3 3-3"/>
                </svg>
            </button>
            <button v-if="!isPlainTextMode" @click="togglePageMode" :class="{ 'is-active': pageMode !== 'full' }" :title="t('editor.toolbar.togglePageMode')" class="toolbar-btn">
                <!-- 全宽模式图标 -->
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" v-if="pageMode === 'full'">
                    <path d="M8 3v3a2 2 0 0 1-2 2H3"/><path d="M21 8h-3a2 2 0 0 1-2-2V3"/><path d="M3 16h3a2 2 0 0 1 2 2v3"/><path d="M16 21v-3a2 2 0 0 1 2-2h3"/>
                </svg>
                <!-- A4 竖向图标 -->
                <svg viewBox="0 0 24 24" class="toolbar-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" v-else-if="pageMode === 'portrait'">
                    <path d="M8 3H5a2 2 0 0 0-2 2v3"/><path d="M21 8V5a2 2 0 0 0-2-2h-3"/><path d="M3 16v3a2 2 0 0 0 2 2h3"/><path d="M16 21h3a2 2 0 0 0 2-2v-3"/>
                </svg>
                <!-- A4 横向图标 (在竖向基础上旋转) -->
                <svg viewBox="0 0 24 24" class="toolbar-icon rotate-90" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" v-else-if="pageMode === 'landscape'">
                    <path d="M8 3H5a2 2 0 0 0-2 2v3"/><path d="M21 8V5a2 2 0 0 0-2-2h-3"/><path d="M3 16v3a2 2 0 0 0 2 2h3"/><path d="M16 21h3a2 2 0 0 0 2-2v-3"/>
                </svg>
            </button>
        </div>
    </div>
</template>

<style scoped>
.toolbar-btn {
    @apply h-8 w-8 inline-flex items-center justify-center p-0 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-600 dark:text-gray-400 transition-colors duration-200 font-bold text-xs leading-none;
}
.toolbar-btn.is-active {
    @apply bg-blue-50 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400;
}
.toolbar-icon {
    width: 16px;
    height: 16px;
    flex-shrink: 0;
    display: block;
}
.toolbar-select {
    @apply h-8 pl-1.5 pr-4 rounded bg-transparent border-none text-[13px] font-bold text-gray-600 dark:text-gray-400 outline-none hover:bg-gray-100 dark:hover:bg-gray-700 transition-all duration-200 cursor-pointer;
    appearance: none;
    background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke='%2394a3b8' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E");
    background-repeat: no-repeat;
    background-position: right 2px center;
    background-size: 12px;
}
.toolbar-select:focus {
    @apply bg-blue-50 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400 ring-0;
}
.dark .toolbar-select {
    background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke='%2364748b' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E");
}

.paper-color-grid {
    width: 164px;
    display: grid;
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: 6px;
}

.paper-color-item {
    width: 22px;
    height: 22px;
    padding: 0;
    margin: 0;
    border-radius: 6px;
    border: 1px solid #d1d5db;
    display: block;
    cursor: pointer;
}

.dark .paper-color-item {
    border-color: #4b5563;
}

.paper-color-item.is-active {
    outline: 2px solid #3b82f6;
    outline-offset: 1px;
}
</style>
