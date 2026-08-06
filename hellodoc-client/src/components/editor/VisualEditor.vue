<script setup lang="ts">
import { watch, onBeforeUnmount, ref, nextTick, onMounted, computed } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import { DOMParser as ProseMirrorDOMParser } from '@tiptap/pm/model'
import { TextSelection } from '@tiptap/pm/state'
import { StarterKit } from '@tiptap/starter-kit'
import { Placeholder } from '@tiptap/extension-placeholder'
import { Subscript } from '@tiptap/extension-subscript'
import { Superscript } from '@tiptap/extension-superscript'
import { TaskList } from '@tiptap/extension-task-list'
import { TaskItem } from '@tiptap/extension-task-item'
import { ResizableImage } from './ResizableImage'
import { TableRow } from '@tiptap/extension-table-row'
import { TableHeader } from '@tiptap/extension-table-header'
import { TableCell } from '@tiptap/extension-table-cell'
import { TextAlign } from '@tiptap/extension-text-align'
import CodeBlockLowlight from '@tiptap/extension-code-block-lowlight'
import { Markdown } from 'tiptap-markdown'
import { MermaidBlock } from './extensions/MermaidBlock'
import { exportHtmlToWord } from './wordExport'
import 'katex/dist/katex.min.css'
import { common, createLowlight } from 'lowlight'
import css from 'highlight.js/lib/languages/css'
import js from 'highlight.js/lib/languages/javascript'
import ts from 'highlight.js/lib/languages/typescript'
import html from 'highlight.js/lib/languages/xml'

// Import extracted modules
import {
    PlainTextMarkdownText,
    PlainTextMarkdownHardBreak,
    MarkdownParagraph,
    MarkdownHeading,
    MarkdownHighlight,
    MarkdownInlineMath,
    MarkdownBlockMath,
    MarkdownVideo,
    TabIndentExtension,
    SmartMarkdownTable
} from './extensions/custom-extensions'
import { useFormatBrush } from './composables/useFormatBrush'
import VisualEditorToolbar from './VisualEditorToolbar.vue'
import ImagePreview from '../ImagePreview.vue'
import './editor-styles.css'

const props = defineProps<{
    modelValue: string
    placeholder?: string
    isReadOnly?: boolean
    hideToolbar?: boolean
    pureMode?: boolean
    isUploadingAssets?: boolean
    paperBgColor?: string
    showPaperColorButton?: boolean
}>()

const emit = defineEmits<{
    'update:modelValue': [value: string]
    'update:paperBgColor': [value: string]
    'update:previewMode': [value: boolean]
    'save': []
    'uploadImg': [files: File[], callback: (urls: string[]) => void]
    'uploadImgFromUrl': [urls: string[], callback: (urls: string[]) => void]
}>()

const plainTextRef = ref<HTMLTextAreaElement | null>(null)
const contentScrollRef = ref<HTMLDivElement | null>(null)
const hasUserInteracted = ref(false)
const normalizeMathMarkdown = (markdown: string) => markdown.replace(
    /(^|\n)[ \t]*\$\s*\n([\s\S]*?)\n[ \t]*\$(?=\s*(\n|$))/g,
    (_m, leading, body) => `${leading}$$\n${String(body ?? '').trim()}\n$$`
)
const lowlight = createLowlight(common)
lowlight.register('html', html)
lowlight.register('css', css)
lowlight.register('javascript', js)
lowlight.register('js', js)
lowlight.register('typescript', ts)
lowlight.register('ts', ts)

const editor = useEditor({
    extensions: [
        StarterKit.configure({
            text: false,
            paragraph: false,
            heading: false,
            codeBlock: false,
        }),
        TabIndentExtension,
        MermaidBlock,
        CodeBlockLowlight.configure({
            lowlight,
            enableTabIndentation: true,
            tabSize: 4,
        }),
        PlainTextMarkdownText,
        PlainTextMarkdownHardBreak,
        MarkdownParagraph,
        MarkdownHeading.configure({
            levels: [1, 2, 3, 4, 5, 6],
        }),
        Markdown.configure({
            html: true,
            tightLists: true,
            tightListClass: 'tight',
            bulletListMarker: '-',
            linkify: true,
            breaks: true,
            transformPastedText: true,
            transformCopiedText: false,
        }),
        MarkdownInlineMath.configure({
            katexOptions: {
                throwOnError: false,
            },
        }),
        MarkdownBlockMath.configure({
            katexOptions: {
                throwOnError: false,
            },
        }),
        MarkdownVideo,
        Subscript,
        Superscript,
        MarkdownHighlight.configure({
            multicolor: false,
        }),
        TaskList,
        TaskItem.configure({
            nested: true,
        }),
        ResizableImage,
        SmartMarkdownTable.configure({
            resizable: true,
        }),
        TableRow,
        TableHeader,
        TableCell,
        TextAlign.configure({
            types: ['heading', 'paragraph'],
        }),
        Placeholder.configure({
            placeholder: props.placeholder || '开始创作...',
        }),
    ],
    content: normalizeMathMarkdown(props.modelValue),
    editable: !props.isReadOnly,
    editorProps: {
        handlePaste: (_view, event) => {
            const clipboardData = event.clipboardData
            if (!clipboardData) return false

            const files: File[] = []
            const seen = new Set<string>()

            if (clipboardData.files) {
                Array.from(clipboardData.files).forEach(f => {
                    const key = `${f.name}-${f.size}`
                    if (!seen.has(key)) {
                        files.push(f)
                        seen.add(key)
                    }
                })
            }
            Array.from(clipboardData.items).forEach(item => {
                if (item.kind === 'file') {
                    const f = item.getAsFile()
                    if (f) {
                        const key = `${f.name}-${f.size}`
                        if (!seen.has(key)) {
                            files.push(f)
                            seen.add(key)
                        }
                    }
                }
            })

            const textData = clipboardData.getData('text/plain')
            if (textData) {
                const isMarkdownTable = /^\s*\|.*\|\s*\n\s*\|[\s\-\|:]+\|\s*\n/.test(textData)
                if (isMarkdownTable) {
                    const lines = textData.trim().split('\n').map(l => l.trim()).filter(l => l.startsWith('|') && l.endsWith('|'))
                    if (lines.length >= 2 && /^\|[\s\-\|:]+\|$/.test(lines[1] || '')) {
                        event.preventDefault()
                        let html = '<table>'
                        lines.forEach((line, index) => {
                            if (index === 1) return
                            html += '<tr>'
                            const cells = line.split('|').slice(1, -1).map(c => c.trim())
                            cells.forEach(cell => {
                                if (index === 0) html += `<th><p>${cell}</p></th>`
                                else html += `<td><p>${cell}</p></td>`
                            })
                            html += '</tr>'
                        })
                        html += '</table>'
                        editor.value?.chain().focus().insertContent(html).run()
                        return true
                    }
                }
            }

            if (files.length > 0) {
                event.preventDefault()
                emit('uploadImg', files, (urls) => {
                    if (urls && urls.length > 0 && editor.value) {
                        let htmlContent = ''
                        urls.forEach((url, index) => {
                            const file = files[index]
                            const isImage = file?.type?.startsWith('image/') || 
                                            /\.(jpg|jpeg|png|gif|webp|svg|bmp)(\?.*)?$/i.test(url)
                            
                            if (isImage) {
                                htmlContent += `<img src="${url}" />`
                            } else {
                                const fileName = file?.name || url.split('/').pop()?.split('?')[0] || '文件'
                                htmlContent += `<p><a href="${url}" target="_blank" class="file-link">${fileName}</a></p>`
                            }
                        })
                        editor.value.chain().focus().insertContent(htmlContent).run()
                    }
                })
                return true
            }

            // 拦截富文本粘贴中的外部图片并转存
            const htmlData = clipboardData.getData('text/html')
            if (htmlData && /<img\s/i.test(htmlData)) {
                const parser = new DOMParser()
                // 提取 StartFragment 内容（如果存在）
                const fragmentMatch = htmlData.match(/<!--StartFragment-->([\s\S]*?)<!--EndFragment-->/)
                const htmlToParse = fragmentMatch ? fragmentMatch[1] || htmlData : htmlData
                const doc = parser.parseFromString(htmlToParse, 'text/html')
                const imgs = Array.from(doc.querySelectorAll('img'))
                const currentHost = window.location.host

                // 筛选外部图片：http(s) 开头且非本站域名
                const externalImgs = imgs.filter(img => {
                    const src = img.getAttribute('src') || ''
                    if (!src.startsWith('http://') && !src.startsWith('https://')) return false
                    try {
                        const imgHost = new URL(src).host
                        return imgHost !== currentHost
                    } catch {
                        return false
                    }
                })

                if (externalImgs.length > 0) {
                    event.preventDefault()
                    const externalUrls = externalImgs.map(img => img.getAttribute('src')!)

                    emit('uploadImgFromUrl', externalUrls, (newUrls) => {
                        // 将转存成功的 URL 替换回 DOM
                        externalImgs.forEach((img, index) => {
                            if (newUrls[index]) {
                                img.setAttribute('src', newUrls[index])
                            }
                        })
                        // 将处理后的 HTML 插入编辑器
                        if (editor.value) {
                            editor.value.chain().focus().insertContent(doc.body.innerHTML).run()
                        }
                    })
                    return true
                }
            }

            return false
        },
        handleDrop: (_view, event) => {
            const files = Array.from(event.dataTransfer?.files || [])
            if (files.length > 0) {
                event.preventDefault()
                emit('uploadImg', files, (urls) => {
                    if (urls && urls.length > 0 && editor.value) {
                        let htmlContent = ''
                        urls.forEach((url, index) => {
                            const file = files[index]
                            const isImage = file?.type?.startsWith('image/') || 
                                            /\.(jpg|jpeg|png|gif|webp|svg|bmp)(\?.*)?$/i.test(url)

                            if (isImage) {
                                htmlContent += `<img src="${url}" />`
                            } else {
                                const fileName = file?.name || url.split('/').pop()?.split('?')[0] || '文件'
                                htmlContent += `<p><a href="${url}" target="_blank" class="file-link">${fileName}</a></p>`
                            }
                        })
                        editor.value.chain().focus().insertContent(htmlContent).run()
                    }
                })
                return true
            }
            return false
        },
        handleKeyDown: (view, event) => {
            if (event.key !== 'Tab') return false
            if (!editor.value) return false
            if (editor.value.isActive('table')) return false

            event.preventDefault()
            const { from, to } = view.state.selection
            const tr = view.state.tr.insertText('    ', from, to)
            view.dispatch(tr.scrollIntoView())
            return true
        }
    },
    onUpdate: ({ editor }) => {
        const markdown = ((editor.storage as any).markdown as any).getMarkdown()
        if (!hasUserInteracted.value && markdown !== normalizeMathMarkdown(props.modelValue)) {
            return
        }
        emit('update:modelValue', markdown)
    },
    onFocus: () => {
        hasUserInteracted.value = true
    },
})

// 处理外部内容变化 (由协同或其他逻辑引起)
watch(() => props.modelValue, (newVal) => {
    const normalizedValue = normalizeMathMarkdown(newVal)
    if (editor.value && normalizedValue !== ((editor.value.storage as any).markdown as any).getMarkdown()) {
        editor.value.commands.setContent(normalizedValue, { emitUpdate: false } as any)
        hasUserInteracted.value = false
    }
    if (newVal !== plainTextValue.value) {
        plainTextValue.value = newVal
        // 如果在纯文本模式下，外部内容变化也需要同步高度
        if (isPlainTextMode.value) {
            nextTick(() => autoResizePlainTextArea())
        }
    }
})

watch(() => props.isReadOnly, (newVal) => {
    editor.value?.setEditable(!newVal && !isPreviewMode.value)
})

onBeforeUnmount(() => {
    editor.value?.destroy()
})

const { isFormatBrushActive, toggleFormatBrush } = useFormatBrush(editor)

const pageMode = ref(localStorage.getItem('hellodoc_editor_page_mode') || 'portrait')
const isPlainTextMode = ref(false)
const isPreviewMode = ref(false)
const isExportingWord = ref(false)
const plainTextValue = ref(props.modelValue)
const toolbarRef = ref<InstanceType<typeof VisualEditorToolbar> | null>(null)
const editorBgClass = computed(() => {
    if (props.pureMode) return '!bg-transparent'
    return pageMode.value !== 'full' ? 'bg-[#f4f5f7] dark:bg-[#0d1117]' : 'bg-white dark:bg-[#161b22]'
})

const getPaperStyle = () => {
    const style: Record<string, string> = {}
    if (props.paperBgColor) {
        style.backgroundColor = props.paperBgColor
    }
    return style
}

onMounted(() => {
    // 如果是移动端（宽度小于 768px），强制开启全宽模式
    if (window.innerWidth < 768) {
        pageMode.value = 'full'
    }
})

watch(pageMode, (newVal) => {
    localStorage.setItem('hellodoc_editor_page_mode', newVal)
})

// Image Preview State
const showImagePreview = ref(false)
const previewImageSrc = ref('')

const openImagePreview = (target: HTMLElement) => {
    previewImageSrc.value = (target as HTMLImageElement).src
    showImagePreview.value = true
}

const handleImageClick = (e: MouseEvent) => {
    const target = e.target as HTMLElement
    if ((isPreviewMode.value || props.isReadOnly) && target.tagName === 'IMG') {
        openImagePreview(target)
    }
}

const handleImageDblClick = (e: MouseEvent) => {
    const target = e.target as HTMLElement
    if (!isPreviewMode.value && !props.isReadOnly && target.tagName === 'IMG') {
        openImagePreview(target)
    }
}

const getCursorIndexForPlainTextMode = (markdown: string) => {
    if (!editor.value) return 0

    const docSize = Math.max(editor.value.state.doc.content.size, 1)
    const selectionPos = Math.max(0, Math.min(editor.value.state.selection.from, docSize))
    const markdownStorage = (editor.value.storage as any).markdown as any
    const serializer = markdownStorage?.serializer

    if (serializer?.serialize) {
        try {
            const beforeSlice = editor.value.state.doc.cut(0, selectionPos)
            const beforeMarkdown = serializer.serialize(beforeSlice) || ''
            return Math.max(0, Math.min(beforeMarkdown.length, markdown.length))
        } catch {
            // fallback to ratio mode below
        }
    }

    const ratio = selectionPos / docSize
    return Math.max(0, Math.min(Math.round(markdown.length * ratio), markdown.length))
}

const getCursorViewportAnchor = () => {
    const container = contentScrollRef.value
    if (!editor.value || !container) return container ? container.clientHeight * 0.35 : 0
    try {
        const coords = editor.value.view.coordsAtPos(editor.value.state.selection.from)
        const containerTop = container.getBoundingClientRect().top
        return Math.max(0, coords.top - containerTop)
    } catch {
        return container.clientHeight * 0.35
    }
}

const getPlainTextCursorIndex = () => {
    const textarea = plainTextRef.value
    if (!textarea) return plainTextValue.value.length
    return Math.max(0, Math.min(textarea.selectionStart ?? 0, plainTextValue.value.length))
}

const getTextAreaCaretOffset = (textarea: HTMLTextAreaElement, cursorIndex: number) => {
    const computed = window.getComputedStyle(textarea)
    const mirror = document.createElement('div')
    mirror.style.position = 'absolute'
    mirror.style.visibility = 'hidden'
    mirror.style.pointerEvents = 'none'
    mirror.style.whiteSpace = 'pre-wrap'
    mirror.style.wordBreak = 'break-word'
    mirror.style.overflowWrap = 'anywhere'
    const paddingLeft = Number.parseFloat(computed.paddingLeft) || 0
    const paddingRight = Number.parseFloat(computed.paddingRight) || 0
    const contentWidth = Math.max(0, textarea.clientWidth - paddingLeft - paddingRight)
    mirror.style.width = `${contentWidth}px`
    mirror.style.fontFamily = computed.fontFamily
    mirror.style.fontSize = computed.fontSize
    mirror.style.fontWeight = computed.fontWeight
    mirror.style.lineHeight = computed.lineHeight
    mirror.style.letterSpacing = computed.letterSpacing
    mirror.style.wordSpacing = computed.wordSpacing
    mirror.style.textIndent = computed.textIndent
    mirror.style.textTransform = computed.textTransform
    mirror.style.direction = computed.direction
    mirror.style.tabSize = computed.tabSize

    const before = textarea.value.slice(0, cursorIndex)
    const after = textarea.value.slice(cursorIndex)
    mirror.textContent = before
    const marker = document.createElement('span')
    marker.textContent = after.length > 0 ? after.charAt(0) : '\u200b'
    mirror.appendChild(marker)
    document.body.appendChild(mirror)

    const markerRect = marker.getBoundingClientRect()
    const mirrorRect = mirror.getBoundingClientRect()
    const caretOffset = Math.max(0, markerRect.top - mirrorRect.top)
    document.body.removeChild(mirror)
    return caretOffset
}

const getPlainTextViewportAnchor = (cursorIndex: number) => {
    const textarea = plainTextRef.value
    const container = contentScrollRef.value
    if (!textarea || !container) return container ? container.clientHeight * 0.35 : 0
    const caretOffset = getTextAreaCaretOffset(textarea, cursorIndex)
    return Math.max(0, textarea.offsetTop + caretOffset - container.scrollTop)
}

const getProseMirrorPosForMarkdownIndex = (markdown: string, cursorIndex: number) => {
    if (!editor.value) return 1
    const maxPos = Math.max(1, editor.value.state.doc.content.size)
    const markdownStorage = (editor.value.storage as any).markdown as any
    const parser = markdownStorage?.parser

    if (parser?.parse) {
        try {
            const prefix = markdown.slice(0, cursorIndex)
            const prefixHtml = parser.parse(prefix) || ''
            const wrapper = document.createElement('div')
            wrapper.innerHTML = prefixHtml || '<p></p>'
            const doc = ProseMirrorDOMParser.fromSchema(editor.value.state.schema).parse(wrapper)
            return Math.max(1, Math.min(doc.content.size, maxPos))
        } catch {
            // fallback to ratio mode below
        }
    }

    const ratio = markdown.length > 0 ? cursorIndex / markdown.length : 0
    return Math.max(1, Math.min(Math.round(maxPos * ratio), maxPos))
}

const focusVisualEditorAtPos = (pos: number) => {
    if (!editor.value) return
    const maxPos = Math.max(1, editor.value.state.doc.content.size)
    const safePos = Math.max(1, Math.min(pos, maxPos))
    const state = editor.value.state
    const resolved = state.doc.resolve(safePos)
    const selection = TextSelection.near(resolved, -1)
    const tr = state.tr.setSelection(selection).scrollIntoView()
    editor.value.view.dispatch(tr)
    editor.value.view.focus()
}

const togglePlainTextMode = () => {
    if (!isPlainTextMode.value) {
        const markdown = ((editor.value?.storage as any)?.markdown as any)?.getMarkdown?.() ?? plainTextValue.value
        plainTextValue.value = markdown
        const cursorIndex = getCursorIndexForPlainTextMode(markdown)
        const viewportAnchor = getCursorViewportAnchor()
        isPlainTextMode.value = true
        nextTick(() => {
            autoResizePlainTextArea()
            const el = plainTextRef.value
            if (!el) return
            const pos = Math.max(0, Math.min(cursorIndex, el.value.length))
            el.focus()
            el.setSelectionRange(pos, pos)
            requestAnimationFrame(() => {
                syncPlainTextScrollToCursor(pos, viewportAnchor)
            })
        })
        return
    }

    const markdown = plainTextValue.value
    const cursorIndex = getPlainTextCursorIndex()
    const viewportAnchor = getPlainTextViewportAnchor(cursorIndex)
    isPlainTextMode.value = false
    nextTick(() => {
        if (!editor.value) return
        const pos = getProseMirrorPosForMarkdownIndex(markdown, cursorIndex)
        requestAnimationFrame(() => {
            focusVisualEditorAtPos(pos)
            const container = contentScrollRef.value
            if (!container) return
            try {
                const coords = editor.value?.view.coordsAtPos(pos)
                if (!coords) return
                const containerTop = container.getBoundingClientRect().top
                const currentAnchor = Math.max(0, coords.top - containerTop)
                container.scrollTop = Math.max(0, container.scrollTop + currentAnchor - viewportAnchor)
            } catch {
                // ignore scroll sync failure
            }
            requestAnimationFrame(() => {
                focusVisualEditorAtPos(pos)
                setTimeout(() => {
                    focusVisualEditorAtPos(pos)
                }, 0)
            })
        })
    })
}

const exportWord = async () => {
    if (isExportingWord.value) return

    isExportingWord.value = true
    try {
        const rawHtml = isPlainTextMode.value
            ? `<p>${(plainTextValue.value || '')
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/\n/g, '<br/>')}</p>`
            : (editor.value?.getHTML() || '<p></p>')
        await exportHtmlToWord(rawHtml)
    } finally {
        isExportingWord.value = false
    }
}

const onPlainTextInput = (event: Event) => {
    const value = (event.target as HTMLTextAreaElement).value
    plainTextValue.value = value
    emit('update:modelValue', value)
    autoResizePlainTextArea()
}

const autoResizePlainTextArea = () => {
    const el = plainTextRef.value
    if (!el) return
    
    const container = contentScrollRef.value
    // 记录当前的滚动位置，防止高度塌陷导致的滚动条跳动
    const scrollTop = container ? container.scrollTop : 0
    
    el.style.height = 'auto'
    const newHeight = Math.max(el.scrollHeight, 860)
    el.style.height = `${newHeight}px`
    
    // 同步恢复滚动位置
    if (container) {
        container.scrollTop = scrollTop
    }
}

const syncPlainTextScrollToCursor = (cursorIndex: number, viewportAnchor?: number) => {
    const textarea = plainTextRef.value
    const container = contentScrollRef.value
    if (!textarea || !container) return

    const caretOffset = getTextAreaCaretOffset(textarea, cursorIndex)
    const anchor = typeof viewportAnchor === 'number' ? viewportAnchor : container.clientHeight * 0.35
    const targetScrollTop = textarea.offsetTop + caretOffset - anchor
    container.scrollTop = Math.max(0, targetScrollTop)
}

const onEditorClick = () => {
    toolbarRef.value?.closeDropdowns()
}

const togglePreviewMode = () => {
    isPreviewMode.value = !isPreviewMode.value
}

const onPreviewEscape = (event: KeyboardEvent) => {
    if (event.key === 'Escape' && isPreviewMode.value) {
        isPreviewMode.value = false
    }
}

watch(isPlainTextMode, (val) => {
    if (val) {
        nextTick(() => {
            autoResizePlainTextArea()
        })
    }
})

watch(isPreviewMode, (val) => {
    emit('update:previewMode', val)
    editor.value?.setEditable(!props.isReadOnly && !val)
})

onMounted(() => {
    window.addEventListener('keydown', onPreviewEscape)
})

onBeforeUnmount(() => {
    window.removeEventListener('keydown', onPreviewEscape)
})

defineExpose({
    focus: () => {
        editor.value?.commands.focus()
    },
    editor
})
</script>

<template>
    <div
        class="visual-editor w-full flex flex-col h-full"
        :class="[
            { 'is-preview-mode': isPreviewMode },
            editorBgClass
        ]"
        @click="onEditorClick"
    >
        <!-- Toolbar -->
        <VisualEditorToolbar
            v-if="!isPreviewMode && !props.hideToolbar"
            ref="toolbarRef"
            :editor="editor"
            :is-plain-text-mode="isPlainTextMode"
            :is-preview-mode="isPreviewMode"
            v-model:page-mode="pageMode"
            :paper-bg-color="props.paperBgColor"
            :show-paper-color-button="props.showPaperColorButton"
            :is-exporting-word="isExportingWord"
            :is-format-brush-active="isFormatBrushActive"
            @update:paper-bg-color="(val) => emit('update:paperBgColor', val)"
            @toggle-plain-text-mode="togglePlainTextMode"
            @toggle-preview-mode="togglePreviewMode"
            @toggle-format-brush="toggleFormatBrush"
            @export-word="exportWord"
            @upload-img="(files, callback) => emit('uploadImg', files, callback)"
        />

        <!-- 外部图片转存进度条 -->
        <div v-if="!isPreviewMode && isUploadingAssets" class="upload-progress-bar">
            <div class="upload-progress-bar-inner"></div>
        </div>

        <!-- Editor Content -->
        <div ref="contentScrollRef"
            :class="[
                'flex-1 w-full overflow-y-auto',
                isFormatBrushActive && !isPlainTextMode && !isPreviewMode ? 'format-brush-cursor' : '',
                editorBgClass
            ]"
            @click="handleImageClick"
            @dblclick="handleImageDblClick"
        >
            <div :class="[
                props.pureMode ? 'w-full !p-0 !bg-transparent !shadow-none !m-0 !min-h-0' :
                (pageMode === 'portrait' ? 'page-container portrait-container' :
                pageMode === 'landscape' ? 'page-container landscape-container' :
                'w-full px-8 pt-8 pb-10 bg-white dark:bg-[#161b22]')
            ]" :style="getPaperStyle()">
                <textarea
                    v-if="isPlainTextMode"
                    ref="plainTextRef"
                    :value="plainTextValue"
                    :placeholder="placeholder || '开始创作...'"
                    :readonly="isReadOnly || isPreviewMode"
                    class="w-full min-h-[860px] bg-transparent text-base leading-relaxed text-gray-900 dark:text-gray-100 outline-none resize-none overflow-hidden"
                    @input="onPlainTextInput"
                />
                <editor-content
                    v-else
                    :editor="editor"
                />
            </div>
        </div>

        <!-- Image Preview Overlay -->
        <ImagePreview :show="showImagePreview" :src="previewImageSrc" @close="showImagePreview = false" />
    </div>
</template>

<style scoped>
.page-container {
    @apply mx-auto my-8 bg-white dark:bg-[#161b22] shadow-[0_2px_12px_rgba(0,0,0,0.06)] dark:shadow-[0_2px_8px_rgba(0,0,0,0.2)] 
           min-h-[1050px] w-full p-[60px_80px] border-none transition-all duration-300;
}

@media (max-width: 768px) {
    .page-container {
        @apply my-0 p-5 shadow-none min-h-0 !max-w-full;
    }
}

.portrait-container {
    @apply max-w-[850px];
}

.landscape-container {
    @apply max-w-[1200px];
}

.upload-progress-bar {
    width: 100%;
    height: 3px;
    background: rgba(79, 70, 229, 0.12);
    overflow: hidden;
    flex-shrink: 0;
    position: relative;
}

.dark .upload-progress-bar {
    background: rgba(129, 140, 248, 0.12);
}

.upload-progress-bar-inner {
    width: 100%;
    height: 100%;
    background: linear-gradient(90deg, rgba(79, 70, 229, 0.25), rgba(79, 70, 229, 0.9), rgba(79, 70, 229, 0.25)) no-repeat;
    background-size: 35% 100%;
    background-position: -35% 0;
    animation: upload-slide-bg 1.1s linear infinite;
}

.dark .upload-progress-bar-inner {
    background: linear-gradient(90deg, rgba(129, 140, 248, 0.2), rgba(129, 140, 248, 0.85), rgba(129, 140, 248, 0.2)) no-repeat;
    background-size: 35% 100%;
}

@keyframes upload-slide-bg {
    0% {
        background-position: -35% 0;
    }
    100% {
        background-position: 100% 0;
    }
}

/* 预览态兜底：即使 nodeview 未正确感知 readonly，也强制禁用拉伸手柄 */
.visual-editor.is-preview-mode :deep(.resize-handle) {
    display: none !important;
    pointer-events: none !important;
}

.visual-editor.is-preview-mode :deep(.resizable-image-wrapper.is-selected img),
.visual-editor.is-preview-mode :deep(.resizable-image-wrapper.is-resizing img) {
    box-shadow: none !important;
}
</style>
