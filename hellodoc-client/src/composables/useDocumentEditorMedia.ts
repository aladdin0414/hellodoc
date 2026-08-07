import { computed, nextTick, onUnmounted, ref, watch, type Ref } from 'vue'
import type { ExposeParam } from 'md-editor-v3'
import { uploadAsset, uploadAssetFromUrl } from '../api/asset'
import { i18n } from '../i18n'
import { message } from '../utils/message'
import { enhanceVideoLinksInElement } from '../utils/mediaEmbed'

interface EditorDocLike {
    id?: number
    type?: string
}

interface UseDocumentEditorMediaOptions {
    getKbId: () => number
    currentDoc: Ref<EditorDocLike | null>
    isMobile: Ref<boolean>
    mdEditorRef: Ref<ExposeParam | undefined>
}

export const useDocumentEditorMedia = (options: UseDocumentEditorMediaOptions) => {
    const uploadingCount = ref(0)
    const isUploadingAssets = computed(() => uploadingCount.value > 0)

    const withUploadIndicator = async <T>(runner: () => Promise<T>) => {
        uploadingCount.value += 1
        try {
            return await runner()
        } finally {
            uploadingCount.value = Math.max(0, uploadingCount.value - 1)
        }
    }

    const getAuthorizedAssetUrl = (rawUrl: string) => rawUrl

    const isVideoFile = (file: File) => {
        const mime = (file.type || '').toLowerCase()
        if (mime.startsWith('video/')) return true
        const name = (file.name || '').toLowerCase()
        return /\.(mp4|webm|ogg|mov|m4v)$/i.test(name)
    }

    const formatAssetMarkdown = (file: File, url: string) => {
        const fileName = (file.name || '').replace(/\]/g, '\\]')
        if (file.type?.startsWith('image/')) {
            return `![${fileName}](${url})`
        }
        if (isVideoFile(file)) {
            const typeAttr = file.type ? ` type="${file.type}"` : ''
            return `<video controls preload="metadata" playsinline style="max-width: 100%; width: 100%; display: block;"><source src="${url}"${typeAttr}></video>`
        }
        return `[${fileName}](${url})`
    }

    const onUploadImg = async (files: File[], callback: (urls: string[]) => void) => {
        try {
            const urls = await withUploadIndicator(() =>
                Promise.all(
                    files.map((file) => {
                        return new Promise<string>(async (resolve, reject) => {
                            const formData = new FormData()
                            formData.append('file', file)
                            try {
                                const res: any = await uploadAsset(options.getKbId(), options.currentDoc.value?.id || 0, formData)
                                resolve(res.rawUrl)
                            } catch (error) {
                                reject(error)
                            }
                        })
                    })
                )
            )
            callback(urls)
        } catch (error) {
            console.error('Upload image failed:', error)
            message.error(i18n.global.t('editor.uploadFailed'))
            callback([])
        }
    }

    const onUploadImgFromUrl = async (urlsToUpload: string[], callback: (urls: string[]) => void) => {
        try {
            const urls = await withUploadIndicator(async () => {
                const results: string[] = []
                for (const url of urlsToUpload) {
                    try {
                        const res: any = await uploadAssetFromUrl(options.getKbId(), options.currentDoc.value?.id || 0, url)
                        results.push(res.rawUrl)
                    } catch (error) {
                        console.warn('转存单张图片失败:', url, error)
                        // 转存失败时保留原始 URL，不影响其他图片
                        results.push(url)
                    }
                }
                return results
            })
            callback(urls)
        } catch (error) {
            console.error('Upload image from URL failed:', error)
            message.error(i18n.global.t('editor.externalImageSaveFailed'))
            callback([])
        }
    }

    const uploadFilesAsAssets = async (files: File[]) => {
        const docId = options.currentDoc.value?.id
        if (!docId) throw new Error('NO_DOC')

        const urls = await withUploadIndicator(() =>
            Promise.all(
                files.map((file) => {
                    return new Promise<string>(async (resolve, reject) => {
                        const formData = new FormData()
                        formData.append('file', file)
                        try {
                            const res: any = await uploadAsset(options.getKbId(), docId, formData)
                            resolve(getAuthorizedAssetUrl(res.rawUrl))
                        } catch (error) {
                            reject(error)
                        }
                    })
                })
            )
        )

        return urls
    }

    const insertToEditor = (text: string) => {
        options.mdEditorRef.value?.insert(() => ({ targetValue: text }))
    }

    const extractFilesFromDataTransfer = (dt: DataTransfer | null) => {
        if (!dt) return []
        const files = Array.from(dt.files || [])
        if (files.length > 0) return files

        const filesFromItems: File[] = []
        for (const item of Array.from(dt.items || [])) {
            if (item.kind === 'file') {
                const file = item.getAsFile()
                if (file) filesFromItems.push(file)
            }
        }
        return filesFromItems
    }

    let lastPasteTime = 0
    const handlePasteFilesToUpload = async (files: File[]) => {
        const now = Date.now()
        if (now - lastPasteTime < 500) return
        lastPasteTime = now

        if (!options.currentDoc.value?.id || options.currentDoc.value.type !== 'file') {
            message.warning(i18n.global.t('editor.selectDocBeforePaste'))
            return
        }

        try {
            const urls = await uploadFilesAsAssets(files)
            const markdown = files.map((file, idx) => formatAssetMarkdown(file, urls[idx]!)).join('\n') + '\n'
            insertToEditor(markdown)
            message.success(i18n.global.t('editor.pasteUploadSuccess'))
        } catch (error) {
            console.error('Paste upload failed:', error)
            message.error(i18n.global.t('editor.pasteUploadFailed'))
        }
    }

    const RICH_HTML_TAGS = /(<(table|thead|tbody|tr|th|td|h[1-6]|ul|ol|li|blockquote|pre|code|img|hr|strong|b|em|i|del|s|a\s)[\s>/])/i
    const isRichHtml = (html: string): boolean => RICH_HTML_TAGS.test(html)

    const parseHtmlToMarkdown = (html: string): string | null => {
        try {
            const parser = new DOMParser()
            const doc = parser.parseFromString(html, 'text/html')
            let rootNodes: Node[]
            const fragmentMatch = html.match(/<!--StartFragment-->([\s\S]*?)<!--EndFragment-->/)
            if (fragmentMatch) {
                const fragmentDoc = parser.parseFromString(fragmentMatch[1] || '', 'text/html')
                rootNodes = Array.from(fragmentDoc.body.childNodes)
            } else {
                rootNodes = Array.from(doc.body.childNodes)
            }

            let markdown = ''
            let listDepth = 0
            const olCounters: number[] = []

            const processNode = (node: Node) => {
                if (node.nodeType === Node.TEXT_NODE) {
                    const text = node.textContent?.replace(/[\n\r]+/g, ' ') || ''
                    if (text.trim() || text === ' ') markdown += text
                } else if (node.nodeType === Node.ELEMENT_NODE) {
                    const el = node as HTMLElement
                    const tag = el.tagName.toLowerCase()
                    if (tag === 'table') {
                        markdown += '\n\n'
                        const rows = Array.from(el.querySelectorAll('tr'))
                        let colCount = 0
                        rows.forEach(row => {
                            const cells = row.querySelectorAll('td, th')
                            if (cells.length > colCount) colCount = cells.length
                        })

                        rows.forEach((row, rowIndex) => {
                            const cells = Array.from(row.querySelectorAll('td, th'))
                            const rowData = []
                            for (let i = 0; i < colCount; i++) {
                                const cell = cells[i]
                                let text = cell ? (cell.textContent || '') : ''
                                text = text.replace(/[\n\r]+/g, ' ').replace(/\|/g, '\\|').trim()
                                rowData.push(text)
                            }
                            markdown += '| ' + rowData.join(' | ') + ' |\n'

                            if (rowIndex === 0) {
                                markdown += '| ' + rowData.map(() => '---').join(' | ') + ' |\n'
                            }
                        })
                        markdown += '\n\n'
                    } else if (tag === 'thead' || tag === 'tbody' || tag === 'tfoot' || tag === 'tr' || tag === 'td' || tag === 'th') {
                    } else if (tag.match(/^h[1-6]$/)) {
                        markdown += '\n\n'
                        const level = parseInt(tag[1] || '1')
                        markdown += '#'.repeat(level) + ' '
                        Array.from(node.childNodes).forEach(processNode)
                        markdown += '\n\n'
                    } else if (tag === 'p' || tag === 'div') {
                        markdown += '\n\n'
                        Array.from(node.childNodes).forEach(processNode)
                        markdown += '\n\n'
                    } else if (tag === 'ul') {
                        if (listDepth === 0) markdown += '\n'
                        listDepth++
                        Array.from(node.childNodes).forEach(processNode)
                        listDepth--
                        if (listDepth === 0) markdown += '\n'
                    } else if (tag === 'ol') {
                        if (listDepth === 0) markdown += '\n'
                        listDepth++
                        olCounters.push(0)
                        Array.from(node.childNodes).forEach(processNode)
                        olCounters.pop()
                        listDepth--
                        if (listDepth === 0) markdown += '\n'
                    } else if (tag === 'li') {
                        const indent = '  '.repeat(Math.max(0, listDepth - 1))
                        const parentTag = (el.parentElement?.tagName || '').toLowerCase()
                        if (parentTag === 'ol' && olCounters.length > 0) {
                            olCounters[olCounters.length - 1]!++
                            markdown += '\n' + indent + olCounters[olCounters.length - 1] + '. '
                        } else {
                            markdown += '\n' + indent + '- '
                        }
                        Array.from(node.childNodes).forEach(processNode)
                    } else if (tag === 'blockquote') {
                        markdown += '\n\n'
                        const savedMarkdown = markdown
                        markdown = ''
                        Array.from(node.childNodes).forEach(processNode)
                        const inner = markdown.trim()
                        markdown = savedMarkdown
                        const quoted = inner.split('\n').map(line => '> ' + line).join('\n')
                        markdown += quoted + '\n\n'
                    } else if (tag === 'pre') {
                        markdown += '\n\n'
                        const codeEl = el.querySelector('code')
                        let lang = ''
                        if (codeEl) {
                            const cls = codeEl.className || ''
                            const langMatch = cls.match(/(?:language|lang)-([\w+-]+)/)
                            if (langMatch) lang = langMatch[1]!
                        }
                        const codeContent = (codeEl || el).textContent || ''
                        markdown += '```' + lang + '\n' + codeContent.replace(/\n$/, '') + '\n```\n\n'
                    } else if (tag === 'code') {
                        if (el.parentElement?.tagName.toLowerCase() === 'pre') {
                            return
                        }
                        const code = el.textContent || ''
                        if (code.includes('`')) {
                            markdown += '`` ' + code + ' ``'
                        } else {
                            markdown += '`' + code + '`'
                        }
                    } else if (tag === 'hr') {
                        markdown += '\n\n---\n\n'
                    } else if (tag === 'br') {
                        markdown += '\n'
                    } else if (tag === 'b' || tag === 'strong') {
                        markdown += '**'
                        Array.from(node.childNodes).forEach(processNode)
                        markdown += '**'
                    } else if (tag === 'i' || tag === 'em') {
                        markdown += '*'
                        Array.from(node.childNodes).forEach(processNode)
                        markdown += '*'
                    } else if (tag === 'del' || tag === 's' || tag === 'strike') {
                        markdown += '~~'
                        Array.from(node.childNodes).forEach(processNode)
                        markdown += '~~'
                    } else if (tag === 'a') {
                        const href = el.getAttribute('href') || ''
                        markdown += '['
                        Array.from(node.childNodes).forEach(processNode)
                        markdown += '](' + href + ')'
                    } else if (tag === 'img') {
                        const src = el.getAttribute('src') || ''
                        const alt = el.getAttribute('alt') || ''
                        if (src) {
                            markdown += '![' + alt + '](' + src + ')'
                        }
                    } else if (tag === 'script' || tag === 'style' || tag === 'meta' || tag === 'link') {
                    } else {
                        Array.from(node.childNodes).forEach(processNode)
                    }
                }
            }

            rootNodes.forEach(processNode)
            markdown = markdown.replace(/\n{3,}/g, '\n\n').trim()
            return markdown || null
        } catch (err) {
            console.error('Failed to parse HTML to Markdown:', err)
            return null
        }
    }

    let unbindEditorPasteHandler: null | (() => void) = null

    const bindEditorPasteHandler = async () => {
        unbindEditorPasteHandler?.()
        unbindEditorPasteHandler = null

        if (options.currentDoc.value?.type !== 'file') return

        await nextTick()
        const editor = options.mdEditorRef.value
        if (!editor) return

        editor.domEventHandlers?.({
            paste: (event: Event) => {
                const e = event as ClipboardEvent
                const files = extractFilesFromDataTransfer(e.clipboardData)
                if (files.length > 0) {
                    e.preventDefault()
                    void handlePasteFilesToUpload(files)
                    return true
                }

                const htmlData = e.clipboardData?.getData('text/html')
                if (htmlData && isRichHtml(htmlData)) {
                    const markdown = parseHtmlToMarkdown(htmlData)
                    if (markdown) {
                        e.preventDefault()
                        insertToEditor(markdown)
                        return true
                    }
                }

                return false
            }
        })
    }

    watch(
        () => [options.currentDoc.value?.id, options.currentDoc.value?.type],
        async () => {
            if (options.currentDoc.value?.type === 'file') {
                await bindEditorPasteHandler()
            } else {
                unbindEditorPasteHandler?.()
                unbindEditorPasteHandler = null
            }
        }
    )

    watch(
        () => options.mdEditorRef.value,
        async (val) => {
            if (val && options.currentDoc.value?.type === 'file') {
                await bindEditorPasteHandler()
            }
        },
        { flush: 'post' }
    )

    let enhancePreviewRafId: number | null = null
    const scheduleEnhancePreview = () => {
        if (enhancePreviewRafId !== null) cancelAnimationFrame(enhancePreviewRafId)
        enhancePreviewRafId = requestAnimationFrame(() => {
            enhancePreviewRafId = null
            const host = (options.mdEditorRef.value as any)?.$el as HTMLElement | undefined
            const preview = host?.querySelector('.md-editor-preview') as HTMLElement | null
            if (!preview) return
            enhanceVideoLinksInElement(preview)
        })
    }

    watch(
        () => [options.currentDoc.value?.id, options.currentDoc.value?.type, options.mdEditorRef.value, options.isMobile.value] as const,
        () => {
            if (options.currentDoc.value?.type !== 'file') return
            scheduleEnhancePreview()
        },
        { flush: 'post' }
    )

    let previewScrollGuardCleanup: (() => void) | null = null

    const setupPreviewScrollGuard = () => {
        previewScrollGuardCleanup?.()
        previewScrollGuardCleanup = null

        const host = (options.mdEditorRef.value as any)?.$el as HTMLElement | undefined
        if (!host) return

        const previewWrapper = host.querySelector('[id$="-preview-wrapper"]') as HTMLElement | null
        const cmScroller = host.querySelector('.cm-scroller') as HTMLElement | null
        if (!previewWrapper || !cmScroller) return

        let userScrollingPreview = false
        const originalScrollTo = previewWrapper.scrollTo.bind(previewWrapper)

        const blockScrollTo = () => {
            if (userScrollingPreview) return
            userScrollingPreview = true
            previewWrapper.scrollTo = function () { } as typeof previewWrapper.scrollTo
        }

        const restoreScrollTo = () => {
            if (!userScrollingPreview) return
            userScrollingPreview = false
            previewWrapper.scrollTo = originalScrollTo
        }

        const onPreviewWheel = () => {
            blockScrollTo()
        }

        const onEditorWheel = () => {
            restoreScrollTo()
        }

        previewWrapper.addEventListener('wheel', onPreviewWheel, { passive: true })
        cmScroller.addEventListener('wheel', onEditorWheel, { passive: true })

        previewScrollGuardCleanup = () => {
            previewWrapper.removeEventListener('wheel', onPreviewWheel)
            cmScroller.removeEventListener('wheel', onEditorWheel)
            restoreScrollTo()
        }
    }

    watch(
        () => [options.currentDoc.value?.id, options.currentDoc.value?.type, options.mdEditorRef.value] as const,
        () => {
            if (options.currentDoc.value?.type === 'file' && options.mdEditorRef.value) {
                nextTick(() => setupPreviewScrollGuard())
            } else {
                previewScrollGuardCleanup?.()
                previewScrollGuardCleanup = null
            }
        },
        { flush: 'post' }
    )

    onUnmounted(() => {
        if (enhancePreviewRafId !== null) cancelAnimationFrame(enhancePreviewRafId)
        previewScrollGuardCleanup?.()
        previewScrollGuardCleanup = null
        unbindEditorPasteHandler?.()
        unbindEditorPasteHandler = null
    })

    return {
        isUploadingAssets,
        onUploadImg,
        onUploadImgFromUrl,
        bindEditorPasteHandler
    }
}
