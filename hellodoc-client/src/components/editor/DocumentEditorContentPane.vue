<script setup lang="ts">
import './setupMdEditor'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { useI18n } from 'vue-i18n'
import EditorAiContextMenu from './EditorAiContextMenu.vue'
import VisualEditor from './VisualEditor.vue'
import { useEditorPreference } from '../../composables/useEditorPreference'
import { ref, watch, nextTick } from 'vue'

const { editorType } = useEditorPreference()

interface DocLike {
    id: number
    name?: string
    type: string
    content: string
    paperBgColor?: string
    paperBgImage?: string
}

const props = defineProps<{
    currentDoc: DocLike
    docLoading: boolean
    isMobile: boolean
    isUploadingAssets: boolean
    isDark: boolean
    isReadOnlyByCollab: boolean
    showPaperColorButton?: boolean
    placeholder?: string
    codeFoldable?: boolean
    previewEnabled?: boolean
    setEditorRef: (instance: any) => void
}>()

const emit = defineEmits<{
    save: []
    uploadImg: [files: File[], callback: (urls: string[]) => void]
    uploadImgFromUrl: [urls: string[], callback: (urls: string[]) => void]
    editorRemount: []
    previewImage: [src: string]
    updatePreviewEnabled: [enabled: boolean]
    aiGeneratingChange: [generating: boolean]
    visualPreviewModeChange: [enabled: boolean]
}>()

const syncPreviewStateFromToolbar = (toolbarButton: HTMLButtonElement) => {
    // 等 md-editor 内部状态切换完成后再读取，避免拿到点击前状态
    requestAnimationFrame(() => {
        const isPreviewActive = toolbarButton.classList.contains('md-editor-toolbar-active')
        emit('updatePreviewEnabled', isPreviewActive)
    })
}

const handleEditorClick = (event: MouseEvent) => {
    const target = event.target as HTMLElement | null
    if (!target) return

    const previewToggleBtn = target.closest('.md-editor-toolbar-item') as HTMLButtonElement | null
    if (previewToggleBtn?.querySelector('.lucide-eye')) {
        syncPreviewStateFromToolbar(previewToggleBtn)
    }

    if (target.tagName !== 'IMG') return
    if (!target.closest('.md-editor-preview')) return
    const src = (target as HTMLImageElement).src
    if (!src) return
    emit('previewImage', src)
}

const { t } = useI18n()

const editorContainerRef = ref<HTMLElement | null>(null)

watch(() => props.currentDoc.id, () => {
    nextTick(() => {
        if (!editorContainerRef.value) return
        // 查找可能产生滚动的内部容器并重置滚动位置到顶部
        const scrollContainers = editorContainerRef.value.querySelectorAll(
            '.overflow-y-auto, .md-editor-preview, .md-editor-content, .md-editor-preview-wrapper'
        )
        scrollContainers.forEach(el => {
            el.scrollTop = 0
        })
        if (localEditorRef.value && typeof localEditorRef.value.focus === 'function') {
            localEditorRef.value.focus()
        }
    })
})

watch(() => editorType.value, (type) => {
    if (type !== 'visual') {
        emit('visualPreviewModeChange', false)
    }
})

const localEditorRef = ref<any>()
const handleSetEditorRef = (instance: any) => {
    localEditorRef.value = instance
    props.setEditorRef(instance)
}

const aiMenuVisible = ref(false)
const aiMenuPosition = ref({ x: 0, y: 0 })
const aiSelectedText = ref('')
const aiIsFullTextMode = ref(false)
const aiStreamMarker = ref('▍')
const aiStreamActive = ref(false)
const aiRawStreamText = ref('')
const aiLastOutputText = ref('')
const aiStreamStartPos = ref(0)

const filterStreamingThinking = (text: string) => {
    let output = ''
    let i = 0
    let isThinking = false
    const len = text.length
    const startTags = ['<think>', '<thought>', '<thinking>']
    const endTags = ['</think>', '</thought>', '</thinking>']

    while (i < len) {
        let matchedStart = false
        let startTagLen = 0

        for (const tag of startTags) {
            if (text.startsWith(tag, i)) {
                matchedStart = true
                startTagLen = tag.length
                break
            }
        }

        if (matchedStart) {
            isThinking = true
            i += startTagLen
            continue
        }

        if (isThinking) {
            let matchedEnd = false
            let endTagLen = 0
            for (const tag of endTags) {
                if (text.startsWith(tag, i)) {
                    matchedEnd = true
                    endTagLen = tag.length
                    break
                }
            }

            if (matchedEnd) {
                isThinking = false
                i += endTagLen
            } else {
                i++
            }
        } else {
            // 处理流式半截开始标签的可能前缀
            const remaining = text.slice(i)
            const isPossibleStartTagPrefix = startTags.some(tag => tag.startsWith(remaining))
            if (isPossibleStartTagPrefix) {
                break
            }

            output += text[i]
            i++
        }
    }

    return { output, isThinking }
}

const handleAiStreamStart = (_model: string) => {
    if (aiStreamActive.value) return
    aiStreamActive.value = true
    aiRawStreamText.value = ''
    aiLastOutputText.value = ''
    emit('aiGeneratingChange', true)
    
    // 如果是富文本模式，使用 Tiptap 直接原地插入光标进行高精度控制，防止重刷全文档导致 Markdown 无法解析
    if (editorType.value !== 'markdown' && localEditorRef.value?.editor) {
        const editorInstance = localEditorRef.value.editor
        editorInstance.chain().focus().deleteSelection().run()
        aiStreamStartPos.value = editorInstance.state.selection.from
        editorInstance.chain().focus().insertContent(aiStreamMarker.value).run()
        return
    }

    if (aiIsFullTextMode.value) {
        const content = props.currentDoc.content || ''
        let wrappedText = ''
        if (content.length === 0) {
            wrappedText = aiStreamMarker.value
        } else if (content.endsWith('\n')) {
            wrappedText = aiStreamMarker.value
        } else {
            wrappedText = `\n\n${aiStreamMarker.value}`
        }
        props.currentDoc.content += wrappedText
        return
    }

    if (localEditorRef.value?.insert) {
        // 选择模式下，原文本不再保留，直接替换为打字机光标，实现原位流式替换
        localEditorRef.value.insert((_selectedText: string) => ({
            targetValue: aiStreamMarker.value,
            select: false,
            deviationStart: 0,
            deviationEnd: 0
        }))
        return
    }

    // 兜底追加
    const content = props.currentDoc.content || ''
    const wrappedText = content.length === 0 ? aiStreamMarker.value : (content.endsWith('\n') ? aiStreamMarker.value : `\n\n${aiStreamMarker.value}`)
    props.currentDoc.content += wrappedText
}

const handleAiStreamChunk = (chunk: string) => {
    if (!chunk) return
    if (!aiStreamActive.value) {
        handleAiStreamStart('ai')
    }

    aiRawStreamText.value += chunk
    const { output } = filterStreamingThinking(aiRawStreamText.value)
    const newIncrement = output.slice(aiLastOutputText.value.length)
    if (!newIncrement) return

    aiLastOutputText.value = output

    // 如果是富文本模式，通过 Tiptap 进行流式增量替换更新
    if (editorType.value !== 'markdown' && localEditorRef.value?.editor) {
        const editorInstance = localEditorRef.value.editor
        const from = aiStreamStartPos.value
        const to = editorInstance.state.selection.to
        editorInstance.chain().focus().insertContentAt({ from, to }, output + aiStreamMarker.value).run()
        return
    }

    if (props.currentDoc.content.includes(aiStreamMarker.value)) {
        props.currentDoc.content = props.currentDoc.content.replace(aiStreamMarker.value, `${newIncrement}${aiStreamMarker.value}`)
        return
    }
    props.currentDoc.content += newIncrement
}

const handleAiStreamEnd = () => {
    const { output } = filterStreamingThinking(aiRawStreamText.value)
    
    // 如果是富文本模式，通过 Tiptap 最终将累加完毕的 markdown 源码进行富文本的 insertContent 解析替换
    if (editorType.value !== 'markdown' && localEditorRef.value?.editor) {
        const editorInstance = localEditorRef.value.editor
        const from = aiStreamStartPos.value
        const to = editorInstance.state.selection.to
        editorInstance.chain().focus().insertContentAt({ from, to }, output).run()
        
        // 自动完成协同同步和更新
        emit('save')
        aiStreamActive.value = false
        emit('aiGeneratingChange', false)
        return
    }

    if (props.currentDoc.content.includes(aiStreamMarker.value)) {
        props.currentDoc.content = props.currentDoc.content.replace(aiStreamMarker.value, '')
    }
    aiStreamActive.value = false
    emit('aiGeneratingChange', false)
    emit('save')
}

const triggerAiAssistant = () => {
    const selection = window.getSelection()
    const text = selection?.toString().trim()
    if (text) {
        aiSelectedText.value = text
        aiIsFullTextMode.value = false
    } else {
        const docTitle = (props.currentDoc.name ?? '').trim()
        const docContent = props.currentDoc.content ?? ''
        aiSelectedText.value = docTitle
            ? `# ${docTitle}\n\n${docContent}`
            : docContent
        aiIsFullTextMode.value = true
    }
    // 获取当前视口中心位置作为弹窗位置
    aiMenuPosition.value = {
        x: window.innerWidth / 2 - 170,
        y: window.innerHeight / 2 - 150
    }
    aiMenuVisible.value = true
}

defineExpose({
    triggerAiAssistant
})
</script>

<template>
    <div class="flex-1 flex overflow-hidden min-h-0 h-full">
        <div v-if="docLoading" class="flex-1 flex items-center justify-center">
            <div class="animate-spin rounded-full h-8 w-8 border-2 border-indigo-600 border-t-transparent">
            </div>
        </div>
        <div v-else-if="currentDoc.type === 'folder'"
            class="flex-1 flex flex-col items-center justify-center bg-slate-50 dark:bg-[#161b22] opacity-60">
            <svg class="h-16 w-16 text-slate-200 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                    d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
            </svg>
            <p class="text-sm font-medium text-slate-400">{{ t('editor.thisIsFolder') }}</p>
        </div>
        <div v-else ref="editorContainerRef" class="flex-1 flex overflow-hidden min-h-0 h-full relative" @click="handleEditorClick">
            <template v-if="editorType === 'markdown'">
                <md-editor :ref="handleSetEditorRef" v-model="currentDoc.content" :toolbars-exclude="['github']"
                    :preview="!isMobile && (previewEnabled ?? true)"
                    :placeholder="placeholder"
                    auto-mobile @on-upload-img="(files, callback) => emit('uploadImg', files, callback)"
                    @on-save="emit('save')" @on-remount="emit('editorRemount')"
                    :class="isUploadingAssets ? 'flex-1 !h-full border-none ow-uploading' : 'flex-1 !h-full border-none'"
                    :theme="isDark ? 'dark' : 'light'" :no-border="true" :noImgZoomIn="true"
                    :preview-theme="isDark ? 'github' : 'github'" code-theme="atom"
                    :codeFoldable="codeFoldable ?? true" :showCodeRowNumber="false" />
            </template>
            <template v-else>
                <VisualEditor :ref="handleSetEditorRef" v-model="currentDoc.content" :placeholder="placeholder" :is-read-only="isReadOnlyByCollab"
                    :paper-bg-color="currentDoc.paperBgColor"
                    :show-paper-color-button="showPaperColorButton"
                    :is-uploading-assets="isUploadingAssets"
                    @update:preview-mode="(val) => emit('visualPreviewModeChange', val)"
                    @update:paper-bg-color="(val) => { currentDoc.paperBgColor = val; emit('save') }"
                    @save="emit('save')" @upload-img="(files, callback) => emit('uploadImg', files, callback)"
                    @upload-img-from-url="(urls, callback) => emit('uploadImgFromUrl', urls, callback)" />
            </template>
            <div v-if="isReadOnlyByCollab" class="absolute inset-0 z-30 bg-transparent cursor-not-allowed"
                @mousedown.prevent @keydown.prevent @keyup.prevent @keypress.prevent @paste.prevent @copy.prevent
                @cut.prevent>
            </div>
            
            <EditorAiContextMenu 
                v-model:visible="aiMenuVisible" 
                :x="aiMenuPosition.x" 
                :y="aiMenuPosition.y" 
                :selected-text="aiSelectedText"
                :is-full-text-mode="aiIsFullTextMode"
                @stream-start="handleAiStreamStart"
                @stream-chunk="handleAiStreamChunk"
                @stream-end="handleAiStreamEnd"
            />
        </div>
    </div>
</template>
