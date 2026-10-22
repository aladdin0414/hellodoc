import assert from 'node:assert/strict'
import { test } from 'node:test'
import { build } from 'esbuild'
import { ref } from 'vue'
import { rm } from 'node:fs/promises'
const output = new URL('../node_modules/.tmp/autosave-regressions.mjs', import.meta.url)
await build({ entryPoints: ['src/composables/editor/useDocumentEditorCore.ts'], bundle: true, platform: 'node', format: 'esm', outfile: output.pathname, external: ['vue'], plugins: [{ name: 'mocks', setup(b) {
 b.onLoad({ filter: /\/api\/document\.ts$/ }, () => ({ contents: 'export const updateDocument = (...args) => globalThis.updateDocument(...args); export const getDocumentDetail = async () => ({})' }))
 b.onLoad({ filter: /\/utils\/message\.ts$/ }, () => ({ contents: 'export const message = { error() {}, warning() {} }' }))
 b.onLoad({ filter: /\/utils\/recentDocs\.ts$/ }, () => ({ contents: 'export const recordRecentDoc = () => {}' }))
 b.onLoad({ filter: /\/i18n\/index\.ts$/ }, () => ({ contents: 'export const i18n = { global: { t: key => key } }' }))
} }] })
const { useDocumentEditorCore } = await import(output.href)
const drafts = new Map()
globalThis.localStorage = { setItem: (k,v) => drafts.set(k,v), getItem: k => drafts.get(k), removeItem: k => drafts.delete(k) }
for (const failure of [false, true]) test(`newer draft survives ${failure ? 'failed' : 'successful'} older save`, async () => {
 drafts.clear()
 const currentDoc = ref({ id: 1, name: 'doc', content: 'A', type: 'file' })
 const core = useDocumentEditorCore({ currentDoc, kbId: ref(1), documents: ref([]) })
 let resolve, reject
 globalThis.updateDocument = () => new Promise((a,b) => { resolve=a; reject=b })
 const saving = core.saveDocument({ silent: true })
 currentDoc.value.content = 'B'
 core.saveDraftToLocal()
 if (failure) reject(new Error('offline')); else resolve()
 await saving
 assert.equal(JSON.parse(drafts.get('hellodoc:editor-draft:1:1')).content, 'B')
 assert.equal(core.hasUnsavedChanges.value, true)
 core.clearSaveTimers()
})
test('failed save schedules a retry without another edit', async () => {
 const currentDoc = ref({ id: 1, name: 'doc', content: 'A', type: 'file' })
 const core = useDocumentEditorCore({ currentDoc, kbId: ref(1), documents: ref([]) })
 let calls = 0
 globalThis.updateDocument = async () => { if (++calls === 1) throw new Error('offline') }
 const originalSetTimeout = globalThis.setTimeout
 let retry
 globalThis.setTimeout = callback => { retry = callback; return 1 }
 try {
  await core.saveDocument({ silent: true })
  assert.equal(typeof retry, 'function')
  retry()
  await Promise.resolve()
  await Promise.resolve()
  assert.equal(calls, 2)
  assert.equal(core.hasUnsavedChanges.value, false)
 } finally { globalThis.setTimeout = originalSetTimeout; core.clearSaveTimers() }
})
await rm(output, { force: true })
