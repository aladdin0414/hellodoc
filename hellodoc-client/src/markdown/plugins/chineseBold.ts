import type MarkdownIt from 'markdown-it'

const installed = new WeakSet<MarkdownIt>()
const chinese = /[\u3400-\u9fff\uf900-\ufaff]/u

/** Accept common malformed Chinese bold boundaries without changing the Markdown source. */
export function chineseBoldPlugin(md: MarkdownIt) {
    if (installed.has(md)) return
    installed.add(md)
    md.inline.ruler.before('emphasis', 'chinese_bold', (state, silent) => {
        const { src, pos, posMax } = state
        if (src.slice(pos, pos + 2) !== '**' || src[pos - 1] === '*' || src[pos + 2] === '*') return false
        const end = src.indexOf('**', pos + 2)
        if (end < 0 || end + 2 > posMax || src[end + 2] === '*') return false
        const content = src.slice(pos + 2, end)
        if (!content.trim() || /[\n\r*`\\<>\[\]_$]/u.test(content)) return false
        if (!chinese.test(src.slice(Math.max(0, pos - 1), Math.min(posMax, end + 3)))) return false
        const opening = state.scanDelims(pos, true)
        const closing = state.scanDelims(end, true)
        if (opening.can_open && closing.can_close) return false

        if (!silent) {
            const leading = content.match(/^\s*/u)?.[0] || ''
            const trailing = content.match(/\s*$/u)?.[0] || ''
            if (leading) state.push('text', '', 0).content = leading
            state.push('strong_open', 'strong', 1).markup = '**'
            state.push('text', '', 0).content = content.trim()
            state.push('strong_close', 'strong', -1).markup = '**'
            if (trailing) state.push('text', '', 0).content = trailing
        }
        state.pos = end + 2
        return true
    })
}
