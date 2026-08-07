<script setup lang="ts">
/**
 * Mermaid 代码块的 NodeView 组件
 * 
 * - 默认渲染 SVG 图表
 * - 通过右上角按钮切换显示代码编辑器
 * - 代码变化时自动重新渲染图表
 */
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { NodeViewWrapper, NodeViewContent } from '@tiptap/vue-3'
import type { NodeViewProps } from '@tiptap/vue-3'
import mermaid from 'mermaid/dist/mermaid.core.mjs'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<NodeViewProps>()

const svgOutput = ref('')
const renderError = ref('')
const isEditing = ref(false)

// 生成全局唯一 ID，避免频繁切换文档时发生 renderId 冲突
const createMermaidInstanceId = () => {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
        return `mermaid-block-${crypto.randomUUID()}`
    }
    const key = '__hellodoc_mermaid_render_counter__'
    const g = globalThis as any
    g[key] = (Number(g[key]) || 0) + 1
    return `mermaid-block-${Date.now()}-${g[key]}`
}
const instanceId = createMermaidInstanceId()
let localRenderCounter = 0
let latestRenderRequest = 0

const language = computed(() => props.node.attrs.language || 'mermaid')

const codeText = computed(() => props.node.textContent || '')

const isEditable = computed(() => props.editor.isEditable)

// 初始化 mermaid（仅设置一次）
let mermaidInitialized = false
const initMermaid = () => {
    if (mermaidInitialized) return
    try {
        // 检测暗色模式
        const isDark = document.documentElement.classList.contains('dark')
        mermaid.initialize({
            startOnLoad: false,
            theme: isDark ? 'dark' : 'default',
            securityLevel: 'loose',
            suppressErrorRendering: true,
            flowchart: { useMaxWidth: true, htmlLabels: true },
        })
        mermaidInitialized = true
    } catch {
        // 忽略重复初始化错误
    }
}

const renderMermaid = async () => {
    const requestId = ++latestRenderRequest
    const code = codeText.value.trim()
    if (!code) {
        if (requestId !== latestRenderRequest) return
        svgOutput.value = ''
        renderError.value = ''
        return
    }

    initMermaid()

    // 对于非 "mermaid" 语言标识，需要补上图表类型声明
    let diagramCode = code
    const lang = language.value.toLowerCase()
    if (lang !== 'mermaid') {
        // 如果代码第一行不是以图表类型开头，自动补上
        const firstLine = code.split('\n')[0]?.trim().toLowerCase() || ''
        const knownStarters = [
            'flowchart', 'graph', 'sequencediagram', 'classDiagram',
            'statediagram', 'erdiagram', 'gantt', 'pie', 'journey',
            'gitgraph', 'mindmap', 'timeline', 'sankey', 'xychart',
            'block-beta', 'sequence', 'class', 'state', 'er',
        ]
        const hasStarter = knownStarters.some(s => firstLine.startsWith(s.toLowerCase()))
        if (!hasStarter) {
            // 将语言标识映射为 mermaid 图表类型声明
            const langMap: Record<string, string> = {
                'flowchart': 'flowchart TD',
                'sequencediagram': 'sequenceDiagram',
                'classdiagram': 'classDiagram',
                'statediagram': 'stateDiagram-v2',
                'erdiagram': 'erDiagram',
                'gantt': 'gantt',
                'pie': 'pie',
                'journey': 'journey',
                'gitgraph': 'gitGraph',
                'mindmap': 'mindmap',
                'timeline': 'timeline',
                'sankey': 'sankey-beta',
            }
            const prefix = langMap[lang]
            if (prefix) {
                diagramCode = `${prefix}\n${code}`
            }
        }
    }

    try {
        const renderId = `${instanceId}-${++localRenderCounter}`
        const { svg } = await mermaid.render(renderId, diagramCode)
        if (requestId !== latestRenderRequest) return
        svgOutput.value = svg
        renderError.value = ''
    } catch (err: any) {
        if (requestId !== latestRenderRequest) return
        renderError.value = err?.message || t('editor.diagramRenderFailed')
        svgOutput.value = ''
    }
}

const toggleEdit = () => {
    if (!isEditable.value) return
    isEditing.value = !isEditing.value
    if (!isEditing.value) {
        // 关闭编辑时重新渲染
        nextTick(() => renderMermaid())
    }
}

// 监听代码内容变化（编辑模式下实时更新预览）
let renderTimer: ReturnType<typeof setTimeout> | null = null
watch(
    [codeText, language],
    () => {
        if (renderTimer) clearTimeout(renderTimer)
        renderTimer = setTimeout(() => {
            renderMermaid()
        }, 500)
    },
    { immediate: true },
)

// 监听暗色模式变化
const observer = new MutationObserver(() => {
    const isDark = document.documentElement.classList.contains('dark')
    mermaidInitialized = false
    try {
        mermaid.initialize({
            startOnLoad: false,
            theme: isDark ? 'dark' : 'default',
            securityLevel: 'loose',
            suppressErrorRendering: true,
            flowchart: { useMaxWidth: true, htmlLabels: true },
        })
        mermaidInitialized = true
    } catch {
        // 忽略
    }
    renderMermaid()
})

onMounted(() => {
    observer.observe(document.documentElement, {
        attributes: true,
        attributeFilter: ['class'],
    })
})

import { onBeforeUnmount } from 'vue'
onBeforeUnmount(() => {
    observer.disconnect()
    if (renderTimer) clearTimeout(renderTimer)
})
</script>

<template>
    <NodeViewWrapper class="mermaid-block-wrapper" :data-language="language">
        <!-- 图表预览区域 -->
        <div
            v-if="svgOutput && !isEditing"
            class="mermaid-preview"
            v-html="svgOutput"
        />

        <!-- 渲染错误提示 -->
        <div
            v-if="renderError && !isEditing && !svgOutput"
            class="mermaid-error"
        >
            <div class="mermaid-error-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10" />
                    <line x1="12" y1="8" x2="12" y2="12" />
                    <line x1="12" y1="16" x2="12.01" y2="16" />
                </svg>
            </div>
            <span class="mermaid-error-text">{{ renderError }}</span>
        </div>

        <!-- 代码编辑区域 -->
        <div v-show="isEditing || (!svgOutput && !renderError)" class="mermaid-code-editor">
            <div class="mermaid-code-header">
                <span class="mermaid-code-lang">{{ language }}</span>
                <button
                    v-if="svgOutput || renderError"
                    class="mermaid-code-toggle"
                    @click.stop="toggleEdit"
                    contenteditable="false"
                >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                        <circle cx="12" cy="12" r="3" />
                    </svg>
                    {{ t('editor.preview') }}
                </button>
            </div>
            <pre class="mermaid-code-pre"><NodeViewContent as="code" /></pre>
        </div>

        <!-- 右上角编辑按钮 -->
        <button
            v-if="(svgOutput || renderError) && !isEditing && isEditable"
            class="mermaid-edit-btn"
            contenteditable="false"
            @click.stop="toggleEdit"
        >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="16 18 22 12 16 6" />
                <polyline points="8 6 2 12 8 18" />
            </svg>
        </button>
    </NodeViewWrapper>
</template>

<style scoped>
.mermaid-block-wrapper {
    position: relative;
    margin: 1em 0;
    border-radius: 10px;
    overflow: hidden;
    border: 1px solid #e2e8f0;
    transition: border-color 0.2s;
}

.dark .mermaid-block-wrapper {
    border-color: #1e293b;
}

.mermaid-block-wrapper:hover {
    border-color: #cbd5e1;
}

.dark .mermaid-block-wrapper:hover {
    border-color: #334155;
}

/* 图表预览 */
.mermaid-preview {
    padding: 16px;
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 80px;
    background: #fafbfc;
    overflow-x: auto;
}

.dark .mermaid-preview {
    background: #0d1117;
}



.mermaid-preview :deep(svg) {
    max-width: 100%;
    height: auto;
}

/* 错误提示 */
.mermaid-error {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 16px;
    background: #fef2f2;
    color: #b91c1c;
    cursor: pointer;
    font-size: 13px;
}

.dark .mermaid-error {
    background: #1c1017;
    color: #fca5a5;
}

.mermaid-error-icon {
    flex-shrink: 0;
    display: flex;
}

.mermaid-error-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

/* 代码编辑器 */
.mermaid-code-editor {
    background: #f8fafc;
}

.dark .mermaid-code-editor {
    background: #0f172a;
}

.mermaid-code-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 6px 14px;
    background: #f1f5f9;
    border-bottom: 1px solid #e2e8f0;
}

.dark .mermaid-code-header {
    background: #1e293b;
    border-bottom-color: #334155;
}

.mermaid-code-lang {
    font-size: 12px;
    font-weight: 500;
    color: #64748b;
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

.dark .mermaid-code-lang {
    color: #94a3b8;
}

.mermaid-code-toggle {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 2px 8px;
    font-size: 12px;
    color: #64748b;
    background: transparent;
    border: 1px solid #cbd5e1;
    border-radius: 4px;
    cursor: pointer;
    transition: all 0.15s;
}

.mermaid-code-toggle:hover {
    color: #334155;
    border-color: #94a3b8;
    background: #fff;
}

.dark .mermaid-code-toggle {
    color: #94a3b8;
    border-color: #475569;
}

.dark .mermaid-code-toggle:hover {
    color: #e2e8f0;
    border-color: #64748b;
    background: #1e293b;
}

.mermaid-code-pre {
    margin: 0 !important;
    padding: 12px 14px !important;
    background: transparent !important;
    border: none !important;
    border-radius: 0 !important;
    font-size: 0.8rem !important;
    line-height: 1.6;
    color: #334155;
    overflow-x: auto;
}

.dark .mermaid-code-pre {
    color: #e2e8f0;
}

.mermaid-code-pre code {
    background: none !important;
    border: none !important;
    padding: 0 !important;
    font-size: inherit !important;
    color: inherit !important;
}

/* 右上角编辑按钮 */
.mermaid-edit-btn {
    position: absolute;
    top: 8px;
    right: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border-radius: 6px;
    background: rgba(255, 255, 255, 0.85);
    border: 1px solid #e2e8f0;
    color: #64748b;
    cursor: pointer;
    opacity: 0;
    transition: opacity 0.2s, background 0.15s, color 0.15s;
    z-index: 2;
}

.mermaid-edit-btn:hover {
    background: #fff;
    color: #334155;
    border-color: #94a3b8;
}

.dark .mermaid-edit-btn {
    background: rgba(30, 41, 59, 0.85);
    border-color: #334155;
    color: #94a3b8;
}

.dark .mermaid-edit-btn:hover {
    background: #1e293b;
    color: #e2e8f0;
    border-color: #475569;
}

.mermaid-block-wrapper:hover .mermaid-edit-btn {
    opacity: 1;
}
</style>
