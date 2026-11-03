import assert from 'node:assert/strict'
import { test } from 'node:test'
import { build } from 'esbuild'
import { JSDOM } from 'jsdom'
import { rm } from 'node:fs/promises'
const dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' })
for (const key of ['window', 'document', 'navigator', 'Node', 'Element', 'HTMLElement', 'SVGElement']) {
  Object.defineProperty(globalThis, key, { value: dom.window[key], configurable: true })
}
const output = new URL('../node_modules/.tmp/lifecycle-version.mjs', import.meta.url)
await build({ entryPoints: ['src/composables/editor/useDocumentEditorLifecycle.ts'], bundle: true, platform: 'node', format: 'esm', outfile: output.pathname, external: ['vue'], plugins: [{ name: 'mock', setup(b) {
  b.onLoad({ filter: /\/utils\/requestHeaders\.ts$/ }, () => ({ contents: 'export const buildCommonRequestHeaders = () => ({})' }))
} }] })
const { useDocumentEditorLifecycle } = await import(output.href)
const { createApp, ref } = await import('vue')
test('beforeunload does not save a conflicted draft against the new server version', () => {
  let fetchCalls = 0, draftSaves = 0
  globalThis.fetch = async () => { fetchCalls++; return {} }
  const app = createApp({ setup() {
    useDocumentEditorLifecycle({ kbId: ref(1), currentDoc: ref({ id: 2, name: 'x', content: 'old draft', type: 'file', version: 5 }),
      hasUnsavedChanges: ref(true), versionConflict: ref(true), saveDraftToLocal: () => { draftSaves++ },
      flushSaveAndDraft() {}, closeCollab() {}, updateIsMobile() {}, closeAllMenus() {}, handleSave() {}, cleanupUiState() {}, clearSaveTimers() {} })
    return () => null
  } })
  const host = document.createElement('div'); app.mount(host)
  window.dispatchEvent(new dom.window.Event('beforeunload'))
  assert.equal(draftSaves, 1)
  assert.equal(fetchCalls, 0)
  app.unmount()
})
await rm(output, { force: true })
