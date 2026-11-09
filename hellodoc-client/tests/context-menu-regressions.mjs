import assert from 'node:assert/strict'
import { test } from 'node:test'
import { build } from 'esbuild'
import { parse, compileScript } from '@vue/compiler-sfc'
import { readFile, rm } from 'node:fs/promises'
import { JSDOM } from 'jsdom'

const dom = new JSDOM('<!doctype html><body></body>', { url: 'http://localhost' })
for (const key of ['window', 'document', 'navigator', 'Node', 'Element', 'HTMLElement', 'SVGElement', 'localStorage']) Object.defineProperty(globalThis, key, { value: dom.window[key], configurable: true })
const output = new URL('../node_modules/.tmp/context-menu-regressions.mjs', import.meta.url)
const { dependencies } = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'))
await build({
  stdin: { contents: "export {default as Overlays} from './src/components/editor/DocumentEditorOverlays.vue'; export {i18n} from './src/i18n/index.ts'", resolveDir: new URL('..', import.meta.url).pathname },
  bundle: true, format: 'esm', platform: 'node', outfile: output.pathname, external: Object.keys(dependencies),
  plugins: [{ name: 'vue', setup(b) {
    b.onLoad({ filter: /\.vue$/ }, async ({ path }) => {
      if (!/\/(DocumentEditorOverlays|DocumentContextMenu)\.vue$/.test(path)) return { contents: 'export default { render() { return null } }' }
      const { descriptor } = parse(await readFile(path, 'utf8'), { filename: path })
      return { contents: compileScript(descriptor, { id: 'context-menu-test', inlineTemplate: true }).content, loader: 'ts' }
    })
  } }],
})
const { Overlays, i18n } = await import(output.href)
const { createApp, h, nextTick } = await import('vue')
test('right click on menu or its backdrop never opens the browser menu', async () => {
  const root = document.createElement('div'); document.body.append(root)
  const changes = []
  const doc = { id: 1, name: 'Doc', type: 'file', parentId: null, orderNum: 0 }
  const app = createApp({ render: () => h(Overlays, { showCreateModal: false, createType: 'file', newName: '', creating: false, showMoveModal: false, selectedTargetFolderId: null, showDeleteModal: false, deletingDoc: null, isDeleting: false, showKbCopyDialog: false, kbSearchQuery: '', selectedKbForCopy: null, copyLoading: false, showTrashModal: false, showContextMenu: true, menuPosition: { x: 10, y: 10 }, contextTargetDoc: doc, documents: [doc], availableFolders: [], filteredCopyableKbs: [], kbId: 1, setCreateInputRef() {}, onUpdateShowContextMenu: value => changes.push(value) }) })
  app.use(i18n).mount(root); await nextTick()
  try {
    const backdrop = root.querySelector('.inset-0')
    const menu = root.querySelector('.z-\\[100\\]')
    assert.ok(backdrop); assert.ok(menu)
    const menuEvent = new window.MouseEvent('contextmenu', { bubbles: true, cancelable: true })
    menu.dispatchEvent(menuEvent)
    assert.equal(menuEvent.defaultPrevented, true)
    assert.deepEqual(changes, [])
    const backdropEvent = new window.MouseEvent('contextmenu', { bubbles: true, cancelable: true })
    backdrop.dispatchEvent(backdropEvent)
    assert.equal(backdropEvent.defaultPrevented, true)
    assert.deepEqual(changes, [false])
  } finally { app.unmount(); root.remove() }
})
await rm(output, { force: true })
