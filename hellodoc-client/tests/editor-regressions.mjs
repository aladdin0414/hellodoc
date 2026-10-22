import assert from 'node:assert/strict'
import { test } from 'node:test'
import { build } from 'esbuild'
import { parse, compileScript } from '@vue/compiler-sfc'
import { readFile, rm } from 'node:fs/promises'
import { JSDOM } from 'jsdom'

const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost' })
for (const key of ['window', 'document', 'navigator', 'Node', 'Element', 'HTMLElement', 'SVGElement', 'MutationObserver', 'DOMParser', 'localStorage', 'getComputedStyle']) {
    Object.defineProperty(globalThis, key, { value: key === 'getComputedStyle' ? dom.window.getComputedStyle.bind(dom.window) : dom.window[key], configurable: true })
}
dom.window.document.elementFromPoint = () => null
globalThis.requestAnimationFrame = callback => setTimeout(callback, 0)
globalThis.cancelAnimationFrame = clearTimeout
const output = new URL('../node_modules/.tmp/editor-regressions.mjs', import.meta.url)
const { dependencies } = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'))
await build({
    stdin: { contents: `export { default as VisualEditor } from './src/components/editor/VisualEditor.vue'; export { i18n } from './src/i18n/index.ts'`, resolveDir: new URL('..', import.meta.url).pathname },
    bundle: true, format: 'esm', platform: 'node', outfile: output.pathname,
    external: [...Object.keys(dependencies).filter(name => name !== 'tiptap-markdown'), '@tiptap/*', 'prosemirror-*', 'markdown-it', 'markdown-it-*'],
    loader: { '.css': 'empty' },
    plugins: [{ name: 'vue', setup(builder) {
        builder.onLoad({ filter: /\/wordExport\.ts$/ }, () => ({ contents: 'export const exportHtmlToWord = async () => {}' }))
        builder.onResolve({ filter: /\.css$/ }, () => ({ path: 'empty', namespace: 'empty-css' }))
        builder.onLoad({ filter: /.*/, namespace: 'empty-css' }, () => ({ contents: '' }))
        builder.onLoad({ filter: /\.vue$/ }, async ({ path }) => {
            if (/\/(VisualEditorToolbar|ImagePreview)\.vue$/.test(path)) return { contents: 'export default { render() { return null } }' }
            if (!path.endsWith('/VisualEditor.vue')) return { contents: `import { h } from 'vue'; import { NodeViewWrapper } from '@tiptap/vue-3'; export default { render() { return h(NodeViewWrapper) } }` }
            const { descriptor } = parse(await readFile(path, 'utf8'), { filename: path })
            return { contents: compileScript(descriptor, { id: 'editor-test', inlineTemplate: true }).content, loader: 'ts' }
        })
    } }],
})
const { VisualEditor, i18n } = await import(output.href)
const { createApp, h, reactive, nextTick } = await import('vue')

// Only presentation node views and Word export are stubbed; editor state and commands are real.
async function mount(content = '', extra = {}) {
    const props = reactive({ modelValue: content, docId: 1, hideToolbar: true, ...extra })
    let instance
    const root = document.createElement('div')
    document.body.append(root)
    const app = createApp({ render: () => h(VisualEditor, { ...props, ref: value => { instance = value }, 'onUpdate:modelValue': value => { props.modelValue = value } }) })
    app.use(i18n).mount(root)
    await new Promise(resolve => setTimeout(resolve, 10))
    return { props, editor: instance.editor, root, close() { app.unmount(); root.remove() } }
}
function paste(editor, text) {
    return editor.options.editorProps.handlePaste(editor.view, { clipboardData: { files: [], items: [], getData: type => type === 'text/plain' ? text : '' }, preventDefault() {} })
}
function key(editor, shiftKey = false) {
    return editor.view.someProp('handleKeyDown', handler => handler(editor.view, new window.KeyboardEvent('keydown', { key: 'Tab', code: 'Tab', keyCode: 9, shiftKey, bubbles: true })))
}

test('loading preserves valid emphasis and tilde-fenced code', async () => {
    const context = await mount('***重点***\n\n~~~\n#example\n~~~')
    try {
        assert.equal(context.editor.state.doc.firstChild.type.name, 'paragraph')
        assert.equal(context.editor.state.doc.lastChild.textContent, '#example')
    } finally { context.close() }
})
test('pasting a table followed by prose retains the prose', async () => {
    const context = await mount()
    try {
        const text = '| A | B |\n| --- | --- |\n| 1 | 2 |\n\n正文应保留'
        const handled = paste(context.editor, text)
        if (!handled) context.editor.commands.insertContent(text)
        assert.match(context.editor.state.doc.textContent, /正文应保留/)
        assert.equal(context.editor.state.doc.firstChild.type.name, 'table')
    } finally { context.close() }
})
test('plain text survives Markdown serialization and reloading', async () => {
    const context = await mount()
    try {
        const text = '*literal* [label](https://example.com) &copy; <tag>'
        context.editor.commands.insertContent({ type: 'text', text })
        const saved = context.editor.storage.markdown.getMarkdown()
        context.editor.commands.setContent(saved)
        assert.equal(context.editor.state.doc.textContent, text)
        assert.equal(context.editor.state.doc.firstChild.firstChild.marks.length, 0)
    } finally { context.close() }
})
test('Tab preserves selected text and Shift+Tab does not insert spaces', async () => {
    const context = await mount('abcdef')
    try {
        context.editor.commands.setTextSelection({ from: 2, to: 5 })
        key(context.editor)
        assert.match(context.editor.state.doc.textContent, /abcdef/)
        const before = context.editor.state.doc.textContent
        key(context.editor, true)
        assert.equal(context.editor.state.doc.textContent, before)
    } finally { context.close() }
})
test('Tab nests a list item and Shift+Tab lifts it', async () => {
    const context = await mount('- first\n- second')
    try {
        context.editor.commands.setTextSelection(12)
        key(context.editor)
        assert.equal(context.editor.state.doc.firstChild.childCount, 1)
        key(context.editor, true)
        assert.equal(context.editor.state.doc.firstChild.childCount, 2)
    } finally { context.close() }
})
test('upload inserts at the original mapped position without replacing a later selection', async () => {
    let complete
    const context = await mount('abcdef', { onUploadImg: (_files, callback) => { complete = callback } })
    try {
        context.editor.commands.setTextSelection(2)
        context.editor.options.editorProps.handleDrop(context.editor.view, { dataTransfer: { files: [new window.File(['x'], 'photo.png', { type: 'image/png' })] }, preventDefault() {} })
        context.editor.commands.setTextSelection({ from: 4, to: 6 })
        complete(['https://example.com/photo.png'])
        assert.equal(context.editor.state.doc.textContent, 'abcdef')
        let imagePos
        context.editor.state.doc.descendants((node, pos) => { if (node.type.name === 'image') imagePos = pos })
        assert.equal(context.editor.state.doc.textBetween(0, imagePos), 'a')
    } finally { context.close() }
})
test('upload callback is ignored after switching documents', async () => {
    let complete
    const context = await mount('old', { onUploadImg: (_files, callback) => { complete = callback } })
    try {
        context.editor.options.editorProps.handleDrop(context.editor.view, { dataTransfer: { files: [new window.File(['x'], 'photo.png')] }, preventDefault() {} })
        context.props.docId = 2
        context.props.modelValue = 'new'
        await nextTick()
        complete(['https://example.com/photo.png'])
        assert.equal(context.editor.getHTML(), '<p>new</p>')
    } finally { context.close() }
})
test('pending uploads map their insertion point through intervening text edits', async () => {
    let complete
    const context = await mount('abcdef', { onUploadImg: (_files, callback) => { complete = callback } })
    try {
        context.editor.commands.setTextSelection(4)
        context.editor.options.editorProps.handlePaste(context.editor.view, {
            clipboardData: { files: [new window.File(['x'], 'photo.png')], items: [], getData: () => '' }, preventDefault() {},
        })
        context.editor.commands.insertContentAt(1, { type: 'text', text: 'prefix' })
        complete(['https://example.com/photo.png'])
        let imagePos
        context.editor.state.doc.descendants((node, pos) => { if (node.type.name === 'image') imagePos = pos })
        assert.equal(context.editor.state.doc.textBetween(0, imagePos), 'prefixabc')
        assert.equal(context.editor.state.doc.textContent, 'prefixabcdef')
    } finally { context.close() }
})
test('pending uploads do not modify a document that has become readonly', async () => {
    let complete
    const context = await mount('original', { onUploadImg: (_files, callback) => { complete = callback } })
    try {
        context.editor.options.editorProps.handlePaste(context.editor.view, {
            clipboardData: { files: [new window.File(['x'], 'photo.png')], items: [], getData: () => '' }, preventDefault() {},
        })
        context.props.isReadOnly = true
        await nextTick()
        complete(['https://example.com/photo.png'])
        assert.equal(context.editor.getHTML(), '<p>original</p>')
    } finally { context.close() }
})
test('Tab and Shift+Tab use code block indentation without deleting selected code', async () => {
    const context = await mount('```\nabc\ndef\n```')
    try {
        context.editor.commands.setTextSelection({ from: 1, to: 8 })
        key(context.editor)
        assert.equal(context.editor.state.doc.textContent, '    abc\n    def')
        key(context.editor, true)
        assert.equal(context.editor.state.doc.textContent, 'abc\ndef')
    } finally { context.close() }
})
test('heading and bullet input rules still trigger when typing a space', async () => {
    const context = await mount()
    try {
        for (const [prefix, type] of [['##', 'heading'], ['-', 'bulletList'], ['1.', 'orderedList']]) {
            context.editor.commands.setContent('<p></p>')
            context.editor.commands.insertContent({ type: 'text', text: prefix })
            const { from, to } = context.editor.state.selection
            const handled = context.editor.view.someProp('handleTextInput', handler => handler(context.editor.view, from, to, ' ', () => context.editor.state.tr.insertText(' ', from, to)))
            assert.ok(handled)
            assert.equal(context.editor.state.doc.firstChild.type.name, type)
        }
    } finally { context.close() }
})
await rm(output, { force: true })
