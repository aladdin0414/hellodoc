const chinese = /[\u3400-\u9fff\uf900-\ufaff]/u

/** Recognize simple Markdown pasted as unformatted HTML, preserving rich text and code. */
export function convertPastedMarkdown(doc: Document): boolean {
    let changed = false
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

export function normalizePastedMarkdown(html: string): string {
    if (!/#[\t #]|\*\*/u.test(html)) return html
    const doc = new DOMParser().parseFromString(html, 'text/html')
    return convertPastedMarkdown(doc) ? doc.body.innerHTML : html
}
