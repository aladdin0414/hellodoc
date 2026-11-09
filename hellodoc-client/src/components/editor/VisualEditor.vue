<script setup lang="ts">
import { watch, onBeforeUnmount, ref, nextTick, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { i18n } from '../../i18n'

const { t } = useI18n()
import { useEditor, EditorContent } from '@tiptap/vue-3'
import { DOMParser as ProseMirrorDOMParser } from '@tiptap/pm/model'
import { TextSelection } from '@tiptap/pm/state'
import type { Transaction } from '@tiptap/pm/state'
import type { Editor, JSONContent } from '@tiptap/core'
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
import { downloadDocumentZip } from '../../utils/documentExport'
import { message } from '../../utils/message'
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
    MarkdownOrderedList,
    MarkdownBulletList,
    MarkdownHighlight,
    MarkdownInlineMath,
    MarkdownBlockMath,
    MarkdownVideo,
    TabIndentExtension,
    SmartMarkdownTable
} from './extensions/custom-extensions'
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
    kbId?: number
    docId?: number
    docName?: string
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
// User-authored Markdown must not pass through AI output repair rules.
const pendingInsertions = new Set<() => void>()
const cancelPendingInsertions = () => {
    pendingInsertions.forEach(cancel => cancel())
    pendingInsertions.clear()
}

// Track the original selection through edits while an asset uploads.
const createUploadInsertion = (target: Editor, position?: number) => {
    const docId = props.docId
    let from = position ?? target.state.selection.from
    let to = position ?? target.state.selection.to
    let active = true
    const cancel = () => {
        active = false
        target.off('transaction', mapPosition)
        pendingInsertions.delete(cancel)
    }
    const mapPosition = ({ transaction }: { transaction: Transaction }) => {
        const start = transaction.mapping.mapResult(from, 1)
        const end = transaction.mapping.mapResult(to, -1)
        if (start.deletedAcross && end.deletedAcross) {
            cancel()
            return
        }
        from = start.pos
        to = Math.max(from, end.pos)
    }
    target.on('transaction', mapPosition)
    pendingInsertions.add(cancel)
    return (content: string | JSONContent[]) => {
        if (!active) return
        cancel()
        if (target.isDestroyed || props.docId !== docId || props.isReadOnly || isPreviewMode.value) return
        if (Array.isArray(content) && content.length === 0) return
        hasUserInteracted.value = true
        target.commands.insertContentAt({ from, to }, content, { updateSelection: false })
    }
}

const uploadedAssets = (files: File[], urls: string[]): JSONContent[] => urls.map((url, index) => {
    const file = files[index]
    if (file?.type?.startsWith('image/') || /\.(jpg|jpeg|png|gif|webp|svg|bmp)(\?.*)?$/i.test(url)) {
        return { type: 'image', attrs: { src: url } }
    }
    const fileName = file?.name || url.split('/').pop()?.split('?')[0] || i18n.global.t('toolbar.file')
    return {
        type: 'paragraph',
        content: [{ type: 'text', text: fileName, marks: [{ type: 'link', attrs: { href: url, target: '_blank' } }] }],
    }
})
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
            hardBreak: false,
            orderedList: false,
            bulletList: false,
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
        MarkdownOrderedList,
        MarkdownBulletList,
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
            placeholder: props.placeholder || i18n.global.t('editor.startWriting') || 'Start writing...',
        }),
    ],
    content: props.modelValue,
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

            const htmlData = clipboardData.getData('text/html')
            if (htmlData && (files.length > 0 || /<img\b/i.test(htmlData))) {
                const fragmentMatch = htmlData.match(/<!--StartFragment-->([\s\S]*?)<!--EndFragment-->/)
                const doc = new DOMParser().parseFromString(fragmentMatch?.[1] || htmlData, 'text/html')
                const images = Array.from(doc.querySelectorAll('img'))
                const localImages: HTMLImageElement[] = []
                const localFiles: File[] = []
                let fileIndex = 0
                for (const img of images) {
                    const src = img.getAttribute('src') || ''
                    if (/^data:image\/[a-z0-9.+-]+;base64,/i.test(src)) {
                        if (files[fileIndex]?.type.startsWith('image/')) {
                            localImages.push(img)
                            localFiles.push(files[fileIndex++]!)
                            continue
                        }
                        try {
                            const match = src.match(/^data:(image\/[a-z0-9.+-]+);base64,([\s\S]*)$/i)!
                            const binary = atob(match[2]!.replace(/\s/g, ''))
                            const bytes = Uint8Array.from(binary, c => c.charCodeAt(0))
                            localImages.push(img)
                            localFiles.push(new File([bytes], `pasted-image-${localFiles.length + 1}`, { type: match[1] }))
                            continue
                        } catch { /* Leave invalid data to the editor's normal parser. */ }
                    }
                    if (/^(?:file:|cid:|blob:)/i.test(src) && files[fileIndex]) {
                        localImages.push(img)
                        localFiles.push(files[fileIndex++]!)
                    }
                }
                // Some applications provide a file and an HTML image with an unrelated URL.
                // Use the supplied file for a single image when no other image was matched.
                if (files.length === 1 && files[0]?.type.startsWith('image/') && images.length === 1 && localFiles.length === 0) {
                    localImages.push(images[0]!)
                    localFiles.push(files[0]!)
                    fileIndex = 1
                }
                const externalImgs = images.filter(img => {
                    if (localImages.includes(img)) return false
                    const src = img.getAttribute('src') || ''
                    if (!/^https?:\/\//i.test(src)) return false
                    try { return new URL(src).host !== window.location.host } catch { return false }
                })
                if (localFiles.length || externalImgs.length || files.length) {
                    event.preventDefault()
                    if (!editor.value) return true
                    const insert = createUploadInsertion(editor.value)
                    const pasteDocId = props.docId
                    const finish = () => {
                        if (props.docId !== pasteDocId) return
                        const remainingFiles = files.slice(fileIndex)
                        if (remainingFiles.length) {
                            emit('uploadImg', remainingFiles, urls => {
                                for (const [index, url] of urls.entries()) {
                                    const file = remainingFiles[index]
                                    if (file?.type?.startsWith('image/')) {
                                        const image = doc.createElement('img')
                                        image.setAttribute('src', url)
                                        image.setAttribute('alt', file.name)
                                        doc.body.appendChild(image)
                                    } else {
                                        const paragraph = doc.createElement('p')
                                        const link = doc.createElement('a')
                                        link.setAttribute('href', url)
                                        link.textContent = file?.name || url
                                        paragraph.appendChild(link)
                                        doc.body.appendChild(paragraph)
                                    }
                                }
                                insert(doc.body.innerHTML)
                            })
                        } else insert(doc.body.innerHTML)
                    }
                    const uploadExternal = () => {
                        if (props.docId !== pasteDocId) return
                        if (!externalImgs.length) return finish()
                        emit('uploadImgFromUrl', externalImgs.map(img => img.getAttribute('src')!), urls => {
                            externalImgs.forEach((img, index) => { if (urls[index]) img.setAttribute('src', urls[index]!) })
                            finish()
                        })
                    }
                    if (localFiles.length) {
                        emit('uploadImg', localFiles, urls => {
                            localImages.forEach((img, index) => { if (urls[index]) img.setAttribute('src', urls[index]!) })
                            uploadExternal()
                        })
                    } else uploadExternal()
                    return true
                }
            }
            if (files.length > 0) {
                event.preventDefault()
                if (!editor.value) return true
                const insert = createUploadInsertion(editor.value)
                const text = clipboardData.getData('text/plain')
                emit('uploadImg', files, urls => {
                    const assets = uploadedAssets(files, urls)
                    if (text) assets.unshift({ type: 'paragraph', content: [{ type: 'text', text }] })
                    insert(assets)
                })
                return true
            }

            return false
        },
        handleDrop: (view, event) => {
            const files = Array.from(event.dataTransfer?.files || [])
            if (files.length > 0) {
                event.preventDefault()
                if (!editor.value) return true
                const dropPos = view.posAtCoords({ left: event.clientX, top: event.clientY })?.pos
                const insert = createUploadInsertion(editor.value, dropPos)
                emit('uploadImg', files, urls => insert(uploadedAssets(files, urls)))
                return true
            }
            return false
        },
    },
    onUpdate: ({ editor }) => {
        const markdown = ((editor.storage as any).markdown as any).getMarkdown()
        if (!hasUserInteracted.value && markdown !== props.modelValue) {
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
    if (editor.value && newVal !== ((editor.value.storage as any).markdown as any).getMarkdown()) {
        editor.value.commands.setContent(newVal, { emitUpdate: false } as any)
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

watch(() => props.docId, cancelPendingInsertions, { flush: 'sync' })

watch(() => props.isReadOnly, (newVal) => {
    editor.value?.setEditable(!newVal && !isPreviewMode.value)
})

onBeforeUnmount(() => {
    cancelPendingInsertions()
    editor.value?.destroy()
})

const savedPageMode = () => localStorage.getItem('hellodoc_editor_page_mode') || 'portrait'
const isNarrowPage = ref(window.innerWidth < 768)
const pageMode = ref(isNarrowPage.value ? 'full' : savedPageMode())
const isPlainTextMode = ref(false)
const isPreviewMode = ref(false)
const isExportingWord = ref(false)
const isExportingMarkdown = ref(false)
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

const updatePageViewport = () => {
    const narrow = window.innerWidth < 768
    if (narrow === isNarrowPage.value) return
    isNarrowPage.value = narrow
    pageMode.value = narrow ? 'full' : savedPageMode()
}
onMounted(() => window.addEventListener('resize', updatePageViewport))
onBeforeUnmount(() => window.removeEventListener('resize', updatePageViewport))
watch(pageMode, (newVal) => {
    if (!isNarrowPage.value) localStorage.setItem('hellodoc_editor_page_mode', newVal)
}, { flush: 'sync' })

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

const exportMarkdown = async () => {
    if (isExportingMarkdown.value || !props.kbId || !props.docId || !props.docName) return

    isExportingMarkdown.value = true
    try {
        message.info(t('kbView.export.preparing'))
        await downloadDocumentZip(props.kbId, props.docId, props.docName)
        message.success(t('kbView.export.started'))
    } catch (error) {
        console.error(error)
        message.error(t('kbView.export.failed'))
    } finally {
        isExportingMarkdown.value = false
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
            :is-exporting-markdown="isExportingMarkdown"
            :can-export-markdown="!!props.kbId && !!props.docId && !!props.docName"
            @update:paper-bg-color="(val) => emit('update:paperBgColor', val)"
            @toggle-plain-text-mode="togglePlainTextMode"
            @toggle-preview-mode="togglePreviewMode"
            @export-word="exportWord"
            @export-markdown="exportMarkdown"
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
                    :placeholder="placeholder || t('editor.startWriting')"
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
    @apply mx-auto my-6 bg-white dark:bg-[#161b22] shadow-[0_2px_10px_rgba(0,0,0,0.035)] dark:shadow-[0_2px_8px_rgba(0,0,0,0.15)]
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

/* 块级数学公式背景块（参考 AI 对话抽屉） */
:deep(.ProseMirror div[data-type="block-math"]),
:deep(.ProseMirror .tiptap-mathematics-render[data-type="block-math"]) {
    margin: 0.75rem 0;
    padding: 0.5rem 0.8rem;
    background-color: rgba(156, 163, 175, 0.08);
    border-radius: 0.5rem;
    text-align: center;
    overflow-x: auto;
    overflow-y: hidden;
    cursor: pointer;
    transition: background-color 0.2s ease, outline-color 0.2s ease;
}

.dark :deep(.ProseMirror div[data-type="block-math"]),
.dark :deep(.ProseMirror .tiptap-mathematics-render[data-type="block-math"]) {
    background-color: rgba(255, 255, 255, 0.05);
}

:deep(.ProseMirror div[data-type="block-math"]:hover),
:deep(.ProseMirror .tiptap-mathematics-render[data-type="block-math"]:hover) {
    background-color: rgba(156, 163, 175, 0.14);
}

.dark :deep(.ProseMirror div[data-type="block-math"]:hover),
.dark :deep(.ProseMirror .tiptap-mathematics-render[data-type="block-math"]:hover) {
    background-color: rgba(255, 255, 255, 0.08);
}

:deep(.ProseMirror div[data-type="block-math"] .block-math-inner),
:deep(.ProseMirror .tiptap-mathematics-render[data-type="block-math"] .block-math-inner) {
    display: inline-block;
    max-width: 100%;
}

:deep(.ProseMirror div[data-type="block-math"] .katex-display),
:deep(.ProseMirror .tiptap-mathematics-render[data-type="block-math"] .katex-display) {
    margin: 0.2rem 0;
    overflow-x: auto;
    overflow-y: hidden;
}

:deep(.ProseMirror div[data-type="block-math"] .katex),
:deep(.ProseMirror .tiptap-mathematics-render[data-type="block-math"] .katex) {
    font-size: 1.05em;
    text-rendering: auto;
}
</style>
