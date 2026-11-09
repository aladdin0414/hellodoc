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

test('ordered list format survives Markdown serialization and reload', async () => {
    const context = await mount('1. first\n2. second')
    try {
        context.editor.commands.setTextSelection(3)
        for (const format of ['mixed', 'circle']) {
            context.editor.commands.updateAttributes('orderedList', { listFormat: format })
            const saved = context.editor.storage.markdown.getMarkdown()
            assert.ok(saved.includes(`data-list-format="${format}"`))
            context.editor.commands.setContent(saved)
            assert.equal(context.editor.state.doc.firstChild.attrs.listFormat, format)
            assert.equal(context.editor.state.doc.textContent, 'firstsecond')
        }
    } finally { context.close() }
})

test('formatted ordered list inside a bullet survives serialization', async () => {
    const context = await mount('- parent\n  1. child\n  2. another\n- next')
    try {
        let position
        context.editor.state.doc.descendants((node, pos) => { if (node.type.name === 'orderedList') position = pos })
        assert.notEqual(position, undefined)
        context.editor.commands.setTextSelection(position + 3)
        context.editor.commands.updateAttributes('orderedList', { listFormat: 'circle' })
        const before = context.editor.getJSON()
        const saved = context.editor.storage.markdown.getMarkdown()
        context.editor.commands.setContent(saved)
        assert.deepEqual(context.editor.getJSON(), before, saved)
    } finally { context.close() }
})

test('mobile layout does not overwrite the saved desktop page width', async () => {
    const oldWidth = window.innerWidth
    localStorage.setItem('hellodoc_editor_page_mode', 'landscape')
    Object.defineProperty(window, 'innerWidth', { value: 600, configurable: true })
    const context = await mount('test')
    try {
        await nextTick()
        assert.equal(localStorage.getItem('hellodoc_editor_page_mode'), 'landscape')
        assert.equal(context.root.querySelector('.landscape-container'), null)
    } finally {
        context.close()
        Object.defineProperty(window, 'innerWidth', { value: oldWidth, configurable: true })
        localStorage.removeItem('hellodoc_editor_page_mode')
    }
})

test('HTML tables with line breaks and code pipes survive saving and reopening', async () => {
    const html = '<table><tr><th>A</th><th>B</th></tr><tr><td>x<br>y</td><td><code>p | q</code></td></tr></table>'
    const context = await mount()
    let reopened
    try {
        context.editor.view.pasteHTML(html, { clipboardData: { files: [], items: [], getData: () => '' }, preventDefault() {} })
        const before = context.editor.getJSON().content[0].toJSON?.() ?? context.editor.getJSON().content[0]
        reopened = await mount(context.editor.storage.markdown.getMarkdown())
        assert.deepEqual(reopened.editor.getJSON().content[0], before)
    } finally { context.close(); reopened?.close() }
})

test('HTML with embedded base64 image retains surrounding text and uploads image', async () => {
    let uploaded
    const context = await mount('', { onUploadImg: (files, cb) => { uploaded = files; cb(['/api/test/image.png']) } })
    try {
        const html = '<p>before</p><img src="data:image/png;base64,eA=="><p>after</p>'
        const handled = context.editor.options.editorProps.handlePaste(context.editor.view, { clipboardData: { files: [], items: [], getData: type => type === 'text/html' ? html : '' }, preventDefault() {} })
        assert.equal(handled, true)
        await nextTick()
        assert.equal(uploaded?.length, 1)
        assert.match(context.editor.getHTML(), /before/)
        assert.match(context.editor.getHTML(), /after/)
        assert.match(context.editor.getHTML(), /\/api\/test\/image.png/)
    } finally { context.close() }
})

test('HTML and an image file preserve surrounding text on paste', async () => {
    const context = await mount('', { onUploadImg: (_files, cb) => cb(['/api/test/image.png']) })
    try {
        const html = '<p>before<img src="file:///clipboard.png">after</p>'
        const handled = context.editor.options.editorProps.handlePaste(context.editor.view, { clipboardData: { files: [new window.File(['x'], 'clipboard.png', { type: 'image/png' })], items: [], getData: type => type === 'text/html' ? html : '' }, preventDefault() {} })
        assert.equal(handled, true)
        await nextTick()
        assert.match(context.editor.getHTML(), /before/)
        assert.match(context.editor.getHTML(), /after/)
        assert.match(context.editor.getHTML(), /\/api\/test\/image.png/)
    } finally { context.close() }
})

test('base64 image with matching clipboard file is inserted once', async () => {
    const context = await mount('', { onUploadImg: (_files, cb) => cb(['/api/test/image.png']) })
    try {
        const html = '<p>before</p><img src="data:image/png;base64,eA=="><p>after</p>'
        context.editor.options.editorProps.handlePaste(context.editor.view, { clipboardData: { files: [new window.File(['x'], 'image.png', { type: 'image/png' })], items: [], getData: type => type === 'text/html' ? html : '' }, preventDefault() {} })
        await nextTick()
        assert.equal((context.editor.getHTML().match(/<img/g) || []).length, 1)
    } finally { context.close() }
})

test('HTML text without an image survives paste with a clipboard file', async () => {
    const context = await mount('', { onUploadImg: (_files, cb) => cb(['/api/test/image.png']) })
    try {
        context.editor.options.editorProps.handlePaste(context.editor.view, { clipboardData: { files: [new window.File(['x'], 'image.png', { type: 'image/png' })], items: [], getData: type => type === 'text/html' ? '<p><strong>Important</strong> text</p>' : '' }, preventDefault() {} })
        await nextTick()
        assert.match(context.editor.getHTML(), /Important/)
        assert.match(context.editor.getHTML(), /image.png/)
    } finally { context.close() }
})

test('renders Chinese bold with internal whitespace and punctuation boundaries', async () => {
    const original = '正全面向**AI 原生开发底座与智能体操作系统（AgentOS） **升级。'
    const context = await mount(original)
    try {
        const strong = context.root.querySelector('.ProseMirror strong')
        assert.equal(strong?.textContent, 'AI 原生开发底座与智能体操作系统（AgentOS）')
        assert.equal(context.props.modelValue, original)
        assert.equal(context.editor.state.doc.textContent, '正全面向AI 原生开发底座与智能体操作系统（AgentOS） 升级。')
    } finally { context.close() }
})
test('renders Chinese bold beside punctuation without adding visible spaces', async () => {
    const context = await mount('正全面向**AI 技术（AgentOS）**升级。')
    try {
        assert.equal(context.root.querySelector('.ProseMirror strong')?.textContent, 'AI 技术（AgentOS）')
        assert.equal(context.editor.state.doc.textContent, '正全面向AI 技术（AgentOS）升级。')
    } finally { context.close() }
})
test('leaves escaped and code-contained stars literal', async () => {
    const context = await mount('\\*\\*中文 ** 与 `**中文 **`\n\n```\n**中文 **\n```')
    try {
        assert.equal(context.root.querySelectorAll('.ProseMirror strong').length, 0)
        assert.match(context.editor.getText(), /\*\*中文 \*\*/)
    } finally { context.close() }
})
test('keeps unrelated malformed English markers and nested Markdown unchanged', async () => {
    const english = await mount('An example **literal ** remains.')
    const nested = await mount('**重点 _内容_**')
    try {
        assert.equal(english.root.querySelector('.ProseMirror strong'), null)
        assert.match(english.editor.getText(), /\*\*literal \*\*/)
        assert.equal(nested.root.querySelector('.ProseMirror strong em')?.textContent, '内容')
    } finally { english.close(); nested.close() }
})

test('pasting HTML plain Markdown headings preserves them as headings after reopening', async () => {
    const c = await mount()
    let reopened
    try {
        c.editor.view.pasteHTML('<p>### Qwen</p><p>说明</p><p>### doubao</p>', { clipboardData: { files: [], items: [], getData: () => '' }, preventDefault() {} })
        assert.deepEqual([...c.root.querySelectorAll('.ProseMirror h3')].map(el => el.textContent), ['Qwen', 'doubao'])
        reopened = await mount(c.editor.storage.markdown.getMarkdown())
        assert.deepEqual([...reopened.root.querySelectorAll('.ProseMirror h3')].map(el => el.textContent), ['Qwen', 'doubao'])
    } finally { c.close(); reopened?.close() }
})
test('pasting HTML preserves literal headings in code and already formatted text', async () => {
    const c = await mount()
    try {
        c.editor.view.pasteHTML('<pre><code>### code</code></pre><p><strong>### literal</strong></p>', { clipboardData: { files: [], items: [], getData: () => '' }, preventDefault() {} })
        assert.equal(c.root.querySelectorAll('.ProseMirror h3').length, 0)
        assert.match(c.editor.getText(), /### code/)
        assert.match(c.editor.getText(), /### literal/)
    } finally { c.close() }
})
test('pasting HTML Markdown heading with image preserves both after upload', async () => {
    const c = await mount('', { onUploadImg: (_files, cb) => cb(['/api/test/image.png']) })
    try {
        const html = '<p>### Qwen</p><img src="data:image/png;base64,eA==">'
        c.editor.options.editorProps.handlePaste(c.editor.view, { clipboardData: { files: [], items: [], getData: type => type === 'text/html' ? html : '' }, preventDefault() {} })
        await nextTick()
        assert.equal(c.root.querySelector('.ProseMirror h3')?.textContent, 'Qwen')
        assert.match(c.editor.getHTML(), /\/api\/test\/image.png/)
    } finally { c.close() }
})
test('pasting HTML Markdown bold in plain paragraphs renders and survives reload', async () => {
    const c = await mount()
    let reopened
    try {
        c.editor.view.pasteHTML('<p>正全面向**AI 技术（AgentOS） **升级。</p>', { clipboardData: { files: [], items: [], getData: () => '' }, preventDefault() {} })
        assert.equal(c.root.querySelector('.ProseMirror strong')?.textContent, 'AI 技术（AgentOS）')
        reopened = await mount(c.editor.storage.markdown.getMarkdown())
        assert.equal(reopened.root.querySelector('.ProseMirror strong')?.textContent, 'AI 技术（AgentOS）')
    } finally { c.close(); reopened?.close() }
})
test('HTML paste keeps escaped heading and bold syntax as literal text', async () => {
    const c = await mount()
    try {
        c.editor.view.pasteHTML('<p>\\### 字面标题</p><p>\\**中文\\**</p><p>###literal</p>', { clipboardData: { files: [], items: [], getData: () => '' }, preventDefault() {} })
        assert.equal(c.root.querySelectorAll('.ProseMirror h3, .ProseMirror strong').length, 0)
        assert.match(c.editor.getText(), /### 字面标题/)
    } finally { c.close() }
})
