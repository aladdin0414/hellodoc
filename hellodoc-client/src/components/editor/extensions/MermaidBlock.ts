/**
 * Mermaid 代码块扩展
 * 
 * 将 mermaid / flowchart / sequenceDiagram 等代码块在编辑器中渲染为 SVG 图表。
 * 编辑模式下点击图表可展开代码编辑区域。
 */
import { Node, mergeAttributes } from '@tiptap/core'
import { VueNodeViewRenderer } from '@tiptap/vue-3'
import MermaidBlockView from './MermaidBlockView.vue'

// 支持的 mermaid 图表语言标识
export const MERMAID_LANGUAGES = new Set([
    'mermaid',
    'flowchart',
    'sequencediagram',
    'classdiagram',
    'statediagram',
    'erdiagram',
    'gantt',
    'pie',
    'journey',
    'gitgraph',
    'mindmap',
    'timeline',
    'sankey',
    'xy-chart',
    'block-beta',
])

export function isMermaidLanguage(lang: string | null | undefined): boolean {
    if (!lang) return false
    return MERMAID_LANGUAGES.has(lang.toLowerCase().replace(/[\s-_]/g, ''))
}

export const MermaidBlock = Node.create({
    name: 'mermaidBlock',
    group: 'block',
    content: 'text*',
    marks: '',
    code: true,
    defining: true,

    addAttributes() {
        return {
            language: {
                default: 'mermaid',
                parseHTML: (element: HTMLElement) => {
                    const classAttr = element.firstElementChild?.getAttribute('class') || ''
                    const match = classAttr.match(/language-(\S+)/)
                    return match?.[1] || 'mermaid'
                },
            },
        }
    },

    parseHTML() {
        return [
            {
                tag: 'pre',
                preserveWhitespace: 'full' as const,
                getAttrs: (node: HTMLElement) => {
                    const code = node.querySelector('code')
                    const classAttr = code?.getAttribute('class') || ''
                    const match = classAttr.match(/language-(\S+)/)
                    const lang = match?.[1] || ''
                    if (!isMermaidLanguage(lang)) return false
                    return { language: lang }
                },
            },
        ]
    },

    renderHTML({ HTMLAttributes }) {
        return [
            'pre',
            mergeAttributes(HTMLAttributes),
            ['code', { class: `language-${HTMLAttributes.language || 'mermaid'}` }, 0],
        ]
    },

    addNodeView() {
        return VueNodeViewRenderer(MermaidBlockView)
    },

    addStorage() {
        return {
            markdown: {
                serialize(state: any, node: any) {
                    const lang = node.attrs?.language || 'mermaid'
                    state.write(`\`\`\`${lang}\n`)
                    state.text(node.textContent, false)
                    state.ensureNewLine()
                    state.write('```')
                    state.closeBlock(node)
                },
                parse: {
                    // tiptap-markdown 解析时使用的 updateDOM / match 信息
                },
            },
        }
    },
})
