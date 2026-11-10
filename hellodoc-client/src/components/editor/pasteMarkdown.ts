const chinese = /[\u3400-\u9fff\uf900-\ufaff]/u

/** Recognize simple Markdown pasted as unformatted HTML, preserving rich text and code. */
export function convertPastedMarkdown(doc: Document, clipboardText = ''): boolean {
    let changed = false
    for (const pre of doc.querySelectorAll('pre')) {
        const code = pre.querySelector(':scope > code') || pre
        if (code !== pre && Array.from(pre.children).some(child => child !== code)) {
            pre.replaceChildren(code)
            changed = true
        }
        if (clipboardText.includes('\n')) {
            const compact = (code.textContent || '').replace(/\s/gu, '')
            const chars: string[] = []
            const offsets: number[] = []
            for (let i = 0; i < clipboardText.length; i++) {
                if (!/\s/u.test(clipboardText[i]!)) {
                    chars.push(clipboardText[i]!)
                    offsets.push(i)
                }
            }
            const text = chars.join('')
            const index = compact ? text.indexOf(compact) : -1
            if (index >= 0 && text.indexOf(compact, index + 1) < 0) {
                const first = offsets[index]!
                const last = offsets[index + compact.length - 1]! + 1
                const start = clipboardText.lastIndexOf('\n', first - 1) + 1
                const end = clipboardText.indexOf('\n', last)
                const prefix = clipboardText.slice(start, first)
                const suffix = clipboardText.slice(last, end < 0 ? undefined : end)
                const restored = clipboardText.slice(prefix.trim() ? first : start, suffix.trim() ? last : end < 0 ? undefined : end)
                if (restored.includes('\n')) {
                    code.textContent = restored.replace(/\r\n/g, '\n')
                    changed = true
                    continue
                }
            }
        }
        if (code !== pre && code.querySelector('span') && !code.querySelector('div')) {
            const copy = code.cloneNode(true) as Element
            for (const br of copy.querySelectorAll('br')) br.replaceWith(doc.createTextNode('\n'))
            code.textContent = copy.textContent
            changed = true
        }
        const children = Array.from(code.childNodes)
        const isLine = (node: ChildNode) => node.nodeType === Node.ELEMENT_NODE && (node as Element).tagName === 'DIV'
        if (!children.some(isLine) || children.some(node => !isLine(node) && (node.nodeType !== Node.TEXT_NODE || node.textContent?.trim()))) continue
        const lines = children.filter(isLine).map(node => {
            const line = node.cloneNode(true) as Element
            for (const br of line.querySelectorAll('br')) br.replaceWith(doc.createTextNode('\n'))
            return line.textContent || ''
        }).join('\n')
        code.textContent = lines
        changed = true
    }
    for (const formula of doc.querySelectorAll('.katex')) {
        if (formula.closest('pre, code')) continue
        const annotation = formula.querySelector('annotation[encoding="application/x-tex"]')
        if (!annotation?.textContent) continue
        const display = formula.closest('.katex-display')
        const source = display || formula
        const replacement = doc.createElement(display ? 'div' : 'span')
        replacement.setAttribute('data-type', display ? 'block-math' : 'inline-math')
        replacement.setAttribute('data-latex', annotation.textContent)
        source.replaceWith(replacement)
        changed = true
    }
    for (const paragraph of doc.querySelectorAll('p')) {
        if (paragraph.closest('pre, code') || Array.from(paragraph.childNodes).some(node => node.nodeType !== Node.TEXT_NODE)) continue
        const text = paragraph.textContent || ''
        const headingMatch = text.match(/^ {0,3}(#{1,6})[\t ]+(.+?)[\t ]*$/u)
        let target: HTMLElement = paragraph
        let body = text
        if (headingMatch) {
            target = doc.createElement(`h${headingMatch[1]!.length}`)
            for (const attr of Array.from(paragraph.attributes)) target.setAttribute(attr.name, attr.value)
            body = headingMatch[2]!.replace(/[\t ]+#+[\t ]*$/u, '')
            changed = true
        }
        // Do not interpret syntax inside code spans or escaped literals.
        const boldPattern = /(?<!\*)\*\*([^*\n\r]+?)\*\*(?!\*)/gu
        if (!/[`\\]/u.test(body)) {
            const nodes = doc.createDocumentFragment()
            let previous = 0
            let converted = false
            for (const match of body.matchAll(boldPattern)) {
                const index = match.index
                const inner = match[1]!
                const nearby = body.slice(Math.max(0, index - 1), Math.min(body.length, index + match[0].length + 1))
                if (!inner.trim() || /[\[\]<>_$]/u.test(inner) || !chinese.test(nearby)) continue
                nodes.appendChild(doc.createTextNode(body.slice(previous, index)))
                const leading = inner.match(/^\s*/u)?.[0] || ''
                const trailing = inner.match(/\s*$/u)?.[0] || ''
                if (leading) nodes.appendChild(doc.createTextNode(leading))
                const strong = doc.createElement('strong')
                strong.textContent = inner.trim()
                nodes.appendChild(strong)
                if (trailing) nodes.appendChild(doc.createTextNode(trailing))
                previous = index + match[0].length
                converted = true
            }
            if (converted) {
                nodes.appendChild(doc.createTextNode(body.slice(previous)))
                target.replaceChildren(nodes)
                changed = true
            } else if (headingMatch) target.textContent = body
        } else if (headingMatch) target.textContent = body
        if (headingMatch) paragraph.replaceWith(target)
    }
    return changed
}

export function normalizePastedMarkdown(html: string, clipboardText = ''): string {
    if (!/#[\t #]|\*\*|katex|<pre\b/iu.test(html)) return html
    const doc = new DOMParser().parseFromString(html, 'text/html')
    return convertPastedMarkdown(doc, clipboardText) ? doc.body.innerHTML : html
}
