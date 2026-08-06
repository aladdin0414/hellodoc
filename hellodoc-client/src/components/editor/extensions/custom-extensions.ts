import { Extension, Node, getHTMLFromFragment, mergeAttributes } from '@tiptap/core'
import { Fragment } from '@tiptap/pm/model'
import { Text } from '@tiptap/extension-text'
import HardBreak from '@tiptap/extension-hard-break'
import { Paragraph } from '@tiptap/extension-paragraph'
import { Heading } from '@tiptap/extension-heading'
import Highlight from '@tiptap/extension-highlight'
import { Table } from '@tiptap/extension-table'
import { InlineMath, BlockMath } from '@tiptap/extension-mathematics'
import { defaultMarkdownSerializer } from 'prosemirror-markdown'
import markdownItMark from 'markdown-it-mark'
import markdownItMath from 'markdown-it-math'

// 覆盖 text 节点的 markdown 序列化，避免自动追加反斜杠转义
export const PlainTextMarkdownText = Text.extend({
    addStorage() {
        return {
            markdown: {
                serialize(state: any, node: any) {
                    const rawText = (node.text || '')
                        .replace(/</g, '&lt;')
                        .replace(/>/g, '&gt;')
                    state.text(rawText, false)
                },
            },
        }
    },
})

// 覆盖 hardBreak 节点的 markdown 序列化，避免输出 "\\\n"
export const PlainTextMarkdownHardBreak = HardBreak.extend({
    addStorage() {
        return {
            markdown: {
                serialize(state: any) {
                    state.write('\n')
                },
            },
        }
    },
})

const serializeAlignedBlock = (
    fallbackSerialize: (state: any, node: any, parent?: any, index?: any) => void
) => (state: any, node: any, parent?: any, index?: any) => {
    const textAlign = node?.attrs?.textAlign
    if (!textAlign) {
        fallbackSerialize(state, node, parent, index)
        return
    }

    const html = getHTMLFromFragment(Fragment.from(node), node.type.schema)
    state.write(html)
    state.closeBlock(node)
}

const fallbackParagraphSerialize = defaultMarkdownSerializer.nodes.paragraph
    ?? ((state: any, node: any) => {
        state.renderInline(node)
        state.closeBlock(node)
    })

const fallbackHeadingSerialize = defaultMarkdownSerializer.nodes.heading
    ?? ((state: any, node: any) => {
        state.write(`${'#'.repeat(node.attrs?.level || 1)} `)
        state.renderInline(node)
        state.closeBlock(node)
    })

export const MarkdownParagraph = Paragraph.extend({
    addStorage() {
        return {
            markdown: {
                serialize: serializeAlignedBlock(fallbackParagraphSerialize),
            },
        }
    },
})

export const MarkdownHeading = Heading.extend({
    addStorage() {
        return {
            markdown: {
                serialize: serializeAlignedBlock(fallbackHeadingSerialize),
            },
        }
    },
})

export const MarkdownHighlight = Highlight.extend({
    addStorage() {
        return {
            markdown: {
                serialize: { open: '==', close: '==', expelEnclosingWhitespace: true },
                parse: {
                    setup(markdownit: any) {
                        markdownit.use(markdownItMark)
                    },
                },
            },
        }
    },
})

const MATH_MARKDOWN_SETUP_KEY = '__hellodoc_math_markdown_setup__'

const escapeHtmlAttribute = (value: string) => value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

const setupMathMarkdownParser = (markdownit: any) => {
    if (markdownit[MATH_MARKDOWN_SETUP_KEY]) return

    markdownit.use(markdownItMath, {
        inlineOpen: '$',
        inlineClose: '$',
        blockOpen: '$$',
        blockClose: '$$',
        inlineRenderer: (latex: string) => `<span data-type="inline-math" data-latex="${escapeHtmlAttribute(latex)}"></span>`,
        blockRenderer: (latex: string) => `<div data-type="block-math" data-latex="${escapeHtmlAttribute(latex)}"></div>`,
    })

    markdownit[MATH_MARKDOWN_SETUP_KEY] = true
}

const parseBooleanAttribute = (value: string | null) => {
    if (value === null) return false
    const normalized = value.trim().toLowerCase()
    return normalized === '' || normalized === 'true' || normalized === '1' || normalized === 'controls'
}

export const MarkdownInlineMath = InlineMath.extend({
    addStorage() {
        return {
            markdown: {
                serialize(state: any, node: any) {
                    const latex = String(node?.attrs?.latex || '')
                    state.write(`$${latex}$`)
                },
                parse: {
                    setup(markdownit: any) {
                        setupMathMarkdownParser(markdownit)
                    },
                },
            },
        }
    },
})

export const MarkdownBlockMath = BlockMath.extend({
    addStorage() {
        return {
            markdown: {
                serialize(state: any, node: any) {
                    const latex = String(node?.attrs?.latex || '')
                    state.write('$$')
                    state.ensureNewLine()
                    state.write(latex)
                    state.ensureNewLine()
                    state.write('$$')
                    state.closeBlock(node)
                },
                parse: {
                    setup(markdownit: any) {
                        setupMathMarkdownParser(markdownit)
                    },
                },
            },
        }
    },
})

export const MarkdownVideo = Node.create({
    name: 'markdownVideo',
    group: 'block',
    atom: true,
    selectable: true,
    draggable: true,

    addAttributes() {
        return {
            src: {
                default: null,
                parseHTML: (element: HTMLElement) => {
                    const src = element.getAttribute('src')
                    if (src) return src
                    return element.querySelector('source')?.getAttribute('src') || null
                },
            },
            controls: {
                default: true,
                parseHTML: (element: HTMLElement) => parseBooleanAttribute(element.getAttribute('controls')),
            },
            autoplay: {
                default: false,
                parseHTML: (element: HTMLElement) => parseBooleanAttribute(element.getAttribute('autoplay')),
            },
            loop: {
                default: false,
                parseHTML: (element: HTMLElement) => parseBooleanAttribute(element.getAttribute('loop')),
            },
            muted: {
                default: false,
                parseHTML: (element: HTMLElement) => parseBooleanAttribute(element.getAttribute('muted')),
            },
            poster: {
                default: null,
                parseHTML: (element: HTMLElement) => element.getAttribute('poster'),
            },
            width: {
                default: null,
                parseHTML: (element: HTMLElement) => element.getAttribute('width'),
            },
            height: {
                default: null,
                parseHTML: (element: HTMLElement) => element.getAttribute('height'),
            },
        }
    },

    parseHTML() {
        return [
            {
                tag: 'video',
            },
        ]
    },

    renderHTML({ HTMLAttributes }) {
        const normalizedAttrs: Record<string, string> = {}

        Object.entries(HTMLAttributes).forEach(([key, value]) => {
            if (value === null || value === undefined || value === false || value === '') return
            if (value === true) {
                normalizedAttrs[key] = ''
                return
            }
            normalizedAttrs[key] = String(value)
        })

        return ['video', mergeAttributes(normalizedAttrs)]
    },

    addStorage() {
        return {
            markdown: {
                serialize(state: any, node: any) {
                    const attrs = node?.attrs || {}
                    const attrMap: Record<string, string | boolean | null | undefined> = {
                        src: attrs.src,
                        controls: attrs.controls,
                        autoplay: attrs.autoplay,
                        loop: attrs.loop,
                        muted: attrs.muted,
                        poster: attrs.poster,
                        width: attrs.width,
                        height: attrs.height,
                    }

                    const attrText = Object.entries(attrMap)
                        .filter(([, value]) => value !== null && value !== undefined && value !== false && value !== '')
                        .map(([key, value]) => {
                            if (value === true) return key
                            return `${key}="${escapeHtmlAttribute(String(value))}"`
                        })
                        .join(' ')

                    state.write(`<video${attrText ? ` ${attrText}` : ''}></video>`)
                    state.closeBlock(node)
                },
            },
        }
    },
})

function hasSpan(node: any) {
    return node.attrs.colspan > 1 || node.attrs.rowspan > 1;
}

function isMarkdownSerializable(node: any) {
    const rows: any[] = [];
    node.forEach((row: any) => rows.push(row));
    if (rows.length === 0) return true;

    const firstRow = rows[0];
    const bodyRows = rows.slice(1);

    const isComplex = (n: any) => {
        if (n.childCount > 1) return true;
        if (n.firstChild && n.firstChild.type.name !== 'paragraph') return true;
        return false;
    };

    const hasCustomWidth = (n: any) => n.attrs.colwidth && n.attrs.colwidth.some((w: number) => w > 0);

    // 第一行必须全是 header，无 span，无复杂内容，无自定义宽度
    for (let i = 0; i < firstRow.childCount; i++) {
        const cell = firstRow.child(i);
        if (cell.type.name !== 'tableHeader' || hasSpan(cell) || isComplex(cell) || hasCustomWidth(cell)) {
            return false;
        }
    }

    // 后续行必须全是 cell，无 span，无复杂内容，无自定义宽度
    for (const row of bodyRows) {
        for (let i = 0; i < row.childCount; i++) {
            const cell = row.child(i);
            if (cell.type.name !== 'tableCell' || hasSpan(cell) || isComplex(cell) || hasCustomWidth(cell)) {
                return false;
            }
        }
    }

    return true;
}

export const SmartMarkdownTable = Table.extend({
    addStorage() {
        return {
            markdown: {
                serialize(state: any, node: any) {
                    if (!isMarkdownSerializable(node)) {
                        const html = getHTMLFromFragment(Fragment.from(node), node.type.schema);
                        state.write(html);
                        state.ensureNewLine();
                        state.closeBlock(node);
                        return;
                    }

                    state.inTable = true;
                    node.forEach((row: any, _p: any, i: number) => {
                        state.write('| ');
                        row.forEach((col: any, _p: any, j: number) => {
                            if (j) {
                                state.write(' | ');
                            }
                            const cellContent = col.firstChild;
                            if (cellContent && cellContent.textContent.trim()) {
                                state.renderInline(cellContent);
                            }
                        });
                        state.write(' |');
                        state.ensureNewLine();
                        if (i === 0) {
                            const delimiterRow = Array.from({ length: row.childCount })
                                .map(() => '---')
                                .join(' | ');
                            state.write(`| ${delimiterRow} |`);
                            state.ensureNewLine();
                        }
                    });
                    state.closeBlock(node);
                    state.inTable = false;
                },
            },
        };
    },
});

export const TabIndentExtension = Extension.create({
    name: 'tabIndent',
    addKeyboardShortcuts() {
        return {
            Tab: () => {
                if (this.editor.isActive('table')) return false
                const { from, to } = this.editor.state.selection
                const tr = this.editor.state.tr.insertText('    ', from, to)
                this.editor.view.dispatch(tr.scrollIntoView())
                return true
            },
        }
    },
})
