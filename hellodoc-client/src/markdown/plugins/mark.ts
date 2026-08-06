import type MarkdownIt from 'markdown-it'

export const markPlugin = (md: MarkdownIt) => {
    md.inline.ruler.before('emphasis', 'mark', (state: any, silent: boolean) => {
        const src = state.src
        const start = state.pos

        if (src.charCodeAt(start) !== 0x3d || src.charCodeAt(start + 1) !== 0x3d) {
            return false
        }

        const end = src.indexOf('==', start + 2)
        if (end === -1 || end === start + 2) {
            return false
        }

        if (!silent) {
            state.push('mark_open', 'mark', 1)
            const text = state.push('text', '', 0)
            text.content = src.slice(start + 2, end)
            state.push('mark_close', 'mark', -1)
        }

        state.pos = end + 2
        return true
    })
}

