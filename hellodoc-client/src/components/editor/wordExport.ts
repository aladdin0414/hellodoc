import htmlToDocxBrowserUrl from '@turbodocx/html-to-docx/dist/html-to-docx.browser.js?url'
import { buildCommonRequestHeaders } from '../../utils/requestHeaders'

type HtmlToDocxFn = (
    html: string,
    header?: string | null,
    options?: any,
    footer?: string | null
) => Promise<Blob | ArrayBuffer>

let htmlToDocxLoader: Promise<HtmlToDocxFn> | null = null

const getHtmlToDocx = async () => {
    if (!(window as any).Buffer) {
        const bufferModule = await import('buffer')
        ;(window as any).Buffer = bufferModule.Buffer
    }

    ;(window as any).global = (window as any).global || window
    ;(window as any).global.Buffer = (window as any).global.Buffer || (window as any).Buffer
    ;(globalThis as any).Buffer = (globalThis as any).Buffer || (window as any).Buffer
    ;(window as any).self = (window as any).self || window

    if (!htmlToDocxLoader) {
        htmlToDocxLoader = new Promise((resolve, reject) => {
            const fromWindow = (window as any)?.HTMLToDOCX
            if (typeof fromWindow === 'function' && (window as any).__hellodocHtmlToDocxReady) {
                resolve(fromWindow)
                return
            }

            const existingScripts = Array.from(document.querySelectorAll('script[data-hellodoc-html-to-docx="1"]'))
            existingScripts.forEach(s => s.remove())
            delete (window as any).HTMLToDOCX

            const script = document.createElement('script')
            script.src = htmlToDocxBrowserUrl
            script.async = true
            script.dataset.hellodocHtmlToDocx = '1'
            script.onload = () => {
                const fn = (window as any)?.HTMLToDOCX
                if (typeof fn === 'function') {
                    ;(window as any).__hellodocHtmlToDocxReady = true
                    resolve(fn)
                }
                else reject(new Error('html-to-docx script loaded but function not found'))
            }
            script.onerror = () => reject(new Error('failed to load html-to-docx script'))
            document.head.appendChild(script)
        })
    }

    return htmlToDocxLoader
}

const blobToDataUrl = (blob: Blob) => new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('failed to convert blob to data url'))
    reader.readAsDataURL(blob)
})

const sanitizeHtmlForDocx = async (rawHtml: string) => {
    const parser = new DOMParser()
    const doc = parser.parseFromString(`<div id="docx-root">${rawHtml}</div>`, 'text/html')
    const root = doc.getElementById('docx-root')
    if (!root) return rawHtml

    // tiptap 任务列表结构(li > label + div)在 html-to-docx 中容易导致正文节点丢失，先降级为普通列表文本。
    const taskLists = Array.from(root.querySelectorAll<HTMLUListElement>('ul[data-type="taskList"]'))
    for (const taskList of taskLists) {
        taskList.style.listStyle = 'none'
        taskList.style.paddingLeft = '0'

        const items = Array.from(taskList.querySelectorAll<HTMLLIElement>(':scope > li'))
        for (const item of items) {
            const checkbox = item.querySelector<HTMLInputElement>(':scope > label input[type="checkbox"]')
            const checked = checkbox?.checked || item.getAttribute('data-checked') === 'true'
            const marker = checked ? '☑' : '☐'
            const content = item.querySelector<HTMLElement>(':scope > div')

            if (!content) {
                const existingText = (item.textContent || '').trim()
                item.textContent = existingText ? `${marker} ${existingText}` : marker
                item.style.listStyle = 'none'
                item.style.margin = '4px 0'
                continue
            }

            const fragment = doc.createDocumentFragment()
            while (content.firstChild) {
                fragment.appendChild(content.firstChild)
            }
            item.replaceChildren(fragment)

            const firstElement = item.firstElementChild as HTMLElement | null
            if (firstElement && /^(p|div|h[1-6])$/i.test(firstElement.tagName)) {
                firstElement.insertBefore(doc.createTextNode(`${marker} `), firstElement.firstChild)
            } else if (firstElement) {
                item.insertBefore(doc.createTextNode(`${marker} `), firstElement)
            } else {
                const text = (item.textContent || '').trim()
                item.textContent = text ? `${marker} ${text}` : marker
            }

            item.style.listStyle = 'none'
            item.style.margin = '4px 0'
        }
    }

    const replaceImageWithLink = (img: HTMLImageElement, url: string) => {
        const link = doc.createElement('a')
        link.href = url
        link.textContent = (img.getAttribute('alt') || '').trim() || url
        link.style.color = '#2563eb'
        link.style.textDecoration = 'underline'
        img.replaceWith(link)
    }

    const images = Array.from(root.querySelectorAll('img'))
    for (const img of images) {
        const src = (img.getAttribute('src') || '').trim()
        if (!src) {
            img.remove()
            continue
        }

        if (src.startsWith('data:')) {
            const base64Marker = ';base64,'
            const markerIndex = src.indexOf(base64Marker)
            if (markerIndex < 0) {
                img.remove()
                continue
            }

            const prefix = src.slice(0, markerIndex + base64Marker.length)
            const payload = src.slice(markerIndex + base64Marker.length).replace(/\s+/g, '')
            const isValidBase64 = /^[A-Za-z0-9+/]+={0,2}$/.test(payload) && payload.length > 0
            if (!isValidBase64) {
                img.remove()
                continue
            }

            img.setAttribute('src', `${prefix}${payload}`)
            continue
        }

        const isRemoteLike = /^(https?:|blob:|\/|\.\/|\.\.\/)/.test(src)
        if (!isRemoteLike) {
            img.remove()
            continue
        }

        try {
            const url = new URL(src, window.location.origin).toString()
            const isRelativeInternal = /^(\/|\.\/|\.\.\/)/.test(src)
            const isCrossOrigin = new URL(url).origin !== window.location.origin
            const useProxy = !isRelativeInternal && isCrossOrigin
            const fetchUrl = useProxy
                ? `/api/files/public/proxy-image?url=${encodeURIComponent(url)}`
                : url
            const headers = buildCommonRequestHeaders({ requireAuth: useProxy })
            const resp = await fetch(fetchUrl, {
                credentials: 'include',
                headers,
            })
            if (!resp.ok) {
                replaceImageWithLink(img, url)
                continue
            }
            const blob = await resp.blob()
            const dataUrl = await blobToDataUrl(blob)
            const base64Marker = ';base64,'
            if (!dataUrl.includes(base64Marker)) {
                replaceImageWithLink(img, url)
                continue
            }
            img.setAttribute('src', dataUrl)
        } catch {
            const fallbackUrl = (img.getAttribute('src') || '').trim()
            if (fallbackUrl) replaceImageWithLink(img, fallbackUrl)
            else img.remove()
        }
    }

    const tables = Array.from(root.querySelectorAll('table'))
    for (const table of tables) {
        table.removeAttribute('align')
        table.removeAttribute('width')
        table.setAttribute('width', '100%')
        table.style.width = '100%'
        table.style.maxWidth = '100%'
        table.style.marginLeft = '0'
        table.style.marginRight = '0'
        table.style.tableLayout = 'fixed'
        table.style.border = '1px solid #d5e3f5'

        const cols = Array.from(table.querySelectorAll<HTMLTableColElement>('col'))
        for (const col of cols) {
            col.removeAttribute('width')
            col.style.width = ''
        }

        const cells = Array.from(table.querySelectorAll<HTMLElement>('th, td'))
        for (const cell of cells) {
            cell.removeAttribute('width')
            cell.removeAttribute('data-colwidth')
            cell.style.width = ''
            cell.style.maxWidth = 'none'
            cell.style.padding = '4px 8px'
            cell.style.whiteSpace = 'normal'
            cell.style.wordBreak = 'break-word'
            cell.style.overflowWrap = 'anywhere'
            cell.style.fontSize = '11pt'
            cell.style.border = '1px solid #d5e3f5'
            cell.style.verticalAlign = 'middle'

            const innerBlocks = Array.from(cell.querySelectorAll<HTMLElement>('p, ul, ol, div'))
            for (const block of innerBlocks) {
                block.style.marginTop = '0'
                block.style.marginBottom = '0'
            }

            if (cell.tagName.toLowerCase() === 'th') {
                cell.style.backgroundColor = '#f8fafc'
                cell.style.fontWeight = '600'
                cell.style.textAlign = 'left'
            }
        }
    }

    const quotes = Array.from(root.querySelectorAll<HTMLElement>('blockquote')).reverse()
    for (const quote of quotes) {
        let level = 1
        let parent = quote.parentElement
        while (parent) {
            if (parent.tagName.toLowerCase() === 'blockquote') level += 1
            parent = parent.parentElement
        }

        const wrapper = doc.createElement('table')
        wrapper.setAttribute('data-hellodoc-quote-wrapper', '1')
        wrapper.setAttribute('width', '100%')
        wrapper.style.width = '100%'
        wrapper.style.borderCollapse = 'collapse'
        wrapper.style.margin = `8px 0 8px ${8 + (level - 1) * 10}px`

        const row = doc.createElement('tr')
        const barCell = doc.createElement('td')
        barCell.style.width = '4px'
        barCell.style.backgroundColor = '#94a3b8'
        barCell.style.padding = '0'
        barCell.style.verticalAlign = 'top'

        const contentCell = doc.createElement('td')
        contentCell.style.backgroundColor = '#f8fafc'
        contentCell.style.padding = `${10 + (level - 1) * 2}px 12px`
        contentCell.style.verticalAlign = 'top'

        while (quote.firstChild) {
            contentCell.appendChild(quote.firstChild)
        }

        row.appendChild(barCell)
        row.appendChild(contentCell)
        wrapper.appendChild(row)
        quote.replaceWith(wrapper)
    }

    const preBlocks = Array.from(root.querySelectorAll<HTMLPreElement>('pre'))
    for (const pre of preBlocks) {
        const codeText = (pre.textContent || '')
            .replace(/\r\n?/g, '\n')
            .replace(/\t/g, '    ')
        const lines = codeText.split('\n')

        const wrapper = doc.createElement('table')
        wrapper.setAttribute('data-hellodoc-code-wrapper', '1')
        wrapper.setAttribute('width', '100%')
        wrapper.style.width = '100%'
        wrapper.style.borderCollapse = 'collapse'
        wrapper.style.margin = '8px 0'

        const row = doc.createElement('tr')
        const cell = doc.createElement('td')
        cell.style.border = '1px solid #d0d7de'
        cell.style.backgroundColor = '#f6f8fa'
        cell.style.padding = '10px'
        cell.style.verticalAlign = 'top'
        cell.style.lineHeight = '1.5'

        const codeBody = doc.createElement('div')
        codeBody.style.fontSize = '11pt'
        codeBody.style.lineHeight = '1.5'
        codeBody.style.wordBreak = 'break-word'
        codeBody.style.overflowWrap = 'anywhere'
        codeBody.style.margin = '0'
        codeBody.style.padding = '0'

        lines.forEach((line, index) => {
            const lineSpan = doc.createElement('span')
            const escapedLine = line
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/ /g, '&nbsp;')
            lineSpan.innerHTML = escapedLine || '&nbsp;'
            codeBody.appendChild(lineSpan)
            if (index < lines.length - 1) {
                codeBody.appendChild(doc.createElement('br'))
            }
        })
        cell.appendChild(codeBody)

        const parent = pre.parentElement
        if (!parent) continue
        parent.insertBefore(wrapper, pre)
        wrapper.appendChild(row)
        row.appendChild(cell)
        pre.remove()
    }

    const inlineCodes = Array.from(root.querySelectorAll<HTMLElement>('code'))
    for (const code of inlineCodes) {
        if (code.closest('pre')) {
            code.style.background = 'transparent'
            code.style.border = 'none'
            code.style.padding = '0'
            continue
        }
        const span = doc.createElement('span')
        span.style.backgroundColor = '#f6f8fa'
        span.style.border = '1px solid #d0d7de'
        span.style.padding = '1px 4px'
        span.style.fontSize = '12pt'
        span.style.display = 'inline-block'
        span.style.lineHeight = '1.2'
        span.style.verticalAlign = 'baseline'
        span.textContent = code.textContent || ''
        code.replaceWith(span)
    }

    return root.innerHTML
}

const getExportFileTitle = () => {
    const rawTitle = (document.title || '').trim()
    const firstPart = rawTitle.split(' - ')[0]?.trim() || rawTitle
    const title = (firstPart || 'document')
        .replace(/[\\/:*?"<>|]/g, '-')
        .replace(/\s+/g, ' ')
        .trim()

    const now = new Date()
    const yyyy = String(now.getFullYear())
    const mm = String(now.getMonth() + 1).padStart(2, '0')
    const dd = String(now.getDate()).padStart(2, '0')
    const hh = String(now.getHours()).padStart(2, '0')
    const mi = String(now.getMinutes()).padStart(2, '0')
    const ss = String(now.getSeconds()).padStart(2, '0')
    return `${title}-${yyyy}${mm}${dd}${hh}${mi}${ss}`
}

const heading = {
    heading1: { font: 'Times New Roman', fontSize: 48, bold: true, spacing: { before: 360, after: 180 }, keepLines: true, keepNext: true, outlineLevel: 0 },
    heading2: { font: 'Times New Roman', fontSize: 36, bold: true, spacing: { before: 300, after: 140 }, keepLines: true, keepNext: true, outlineLevel: 1 },
    heading3: { font: 'Times New Roman', fontSize: 28, bold: true, spacing: { before: 260, after: 120 }, keepLines: true, keepNext: true, outlineLevel: 2 },
    heading4: { font: 'Times New Roman', fontSize: 24, bold: true, spacing: { before: 220, after: 100 }, keepLines: true, keepNext: true, outlineLevel: 3 },
    heading5: { font: 'Times New Roman', fontSize: 22, bold: true, spacing: { before: 200, after: 80 }, keepLines: true, keepNext: true, outlineLevel: 4 },
    heading6: { font: 'Times New Roman', fontSize: 20, bold: true, spacing: { before: 180, after: 80 }, keepLines: true, keepNext: true, outlineLevel: 5 },
}

const buildWordHtml = (normalizedHtml: string) => `<html><head><style>
body{font-size:12pt;}
h1{margin:18pt 0 10pt;}
h2{margin:16pt 0 9pt;}
h3{margin:14pt 0 8pt;}
h4{margin:12pt 0 7pt;}
h5{margin:10pt 0 6pt;}
h6{margin:8pt 0 5pt;}
table{width:100% !important;table-layout:fixed;border-collapse:collapse;border:1px solid #d5e3f5;}
th,td{word-break:break-word;overflow-wrap:anywhere;font-size:11pt;border:1px solid #d5e3f5;vertical-align:middle;padding:4px 8px;}
th p,td p,th ul,td ul,th ol,td ol,th div,td div{margin-top:0;margin-bottom:0;}
th{background:#f8fafc;font-weight:600;text-align:left;}
pre,.hljs{background:#f6f8fa;border:1px solid #d0d7de;border-radius:6px;padding:12px;white-space:pre-wrap;word-break:break-word;overflow-wrap:anywhere;font-size:11pt;line-height:1.5;}
p code,li code,td code,th code{background:#f6f8fa;border:1px solid #d0d7de;border-radius:4px;padding:1px 4px;font-size:12pt;}
pre code{background:transparent;border:none;padding:0;font-size:inherit;}
</style></head><body>${normalizedHtml}</body></html>`

export const exportHtmlToWord = async (rawHtml: string) => {
    const normalizedHtml = await sanitizeHtmlForDocx(rawHtml)
    const html = buildWordHtml(normalizedHtml)
    const exportFileTitle = getExportFileTitle()

    const htmlToDocx = await getHtmlToDocx()
    const docx = await htmlToDocx(html, null, {
        title: exportFileTitle,
        fontSize: 24,
        heading,
    })

    const blob = docx instanceof Blob
        ? docx
        : new Blob([docx as ArrayBuffer], {
            type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${exportFileTitle}.docx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
}
