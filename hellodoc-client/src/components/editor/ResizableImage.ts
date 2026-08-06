import { Image } from '@tiptap/extension-image'
import { VueNodeViewRenderer } from '@tiptap/vue-3'
import ResizableImageView from './ResizableImageView.vue'

// 扩展默认 Image 扩展，增加 width 属性和自定义 NodeView
export const ResizableImage = Image.extend({
    addAttributes() {
        return {
            ...this.parent?.(),
            width: {
                default: null,
                parseHTML: (element: HTMLElement) => {
                    const width = element.getAttribute('width') || element.style.width
                    return width ? parseInt(width, 10) || null : null
                },
                renderHTML: (attributes: Record<string, any>) => {
                    if (!attributes.width) return {}
                    return { width: attributes.width }
                },
            },
        }
    },

    addNodeView() {
        return VueNodeViewRenderer(ResizableImageView as any)
    },

    addStorage() {
        return {
            markdown: {
                serialize(state: any, node: any) {
                    const attrs = node?.attrs || {}
                    const src = attrs.src || ''
                    const alt = attrs.alt || ''
                    const title = attrs.title || ''
                    const width = attrs.width

                    const escapeAttr = (value: string) =>
                        String(value)
                            .replace(/&/g, '&amp;')
                            .replace(/"/g, '&quot;')
                            .replace(/</g, '&lt;')
                            .replace(/>/g, '&gt;')

                    // 存在 width 时使用 HTML，确保宽度信息可持久化。
                    if (width) {
                        const html = `<img src="${escapeAttr(src)}" alt="${escapeAttr(alt)}"${title ? ` title="${escapeAttr(title)}"` : ''} width="${Number(width)}" />`
                        state.write(html)
                        return
                    }

                    // 无 width 时保持标准 Markdown 图片格式，避免无关格式变化。
                    const escapedAlt = String(alt).replace(/\]/g, '\\]')
                    const escapedSrc = String(src).replace(/\)/g, '\\)')
                    const escapedTitle = String(title).replace(/"/g, '\\"')
                    state.write(`![${escapedAlt}](${escapedSrc}${title ? ` "${escapedTitle}"` : ''})`)
                },
            },
        }
    },
})
