import assert from 'node:assert/strict'
import { test } from 'node:test'
import { build } from 'esbuild'
import { ref } from 'vue'
import { rm } from 'node:fs/promises'
const output = new URL('../node_modules/.tmp/autosave-regressions.mjs', import.meta.url)
await build({ entryPoints: ['src/composables/editor/useDocumentEditorCore.ts'], bundle: true, platform: 'node', format: 'esm', outfile: output.pathname, external: ['vue'], plugins: [{ name: 'mocks', setup(b) {
 b.onLoad({ filter: /\/api\/document\.ts$/ }, () => ({ contents: 'export const updateDocument = (...args) => globalThis.updateDocument(...args); export const getDocumentDetail = (...args) => globalThis.getDocumentDetail(...args)' }))
 b.onLoad({ filter: /\/utils\/message\.ts$/ }, () => ({ contents: 'export const message = { error() {}, warning() {} }' }))
 b.onLoad({ filter: /\/utils\/recentDocs\.ts$/ }, () => ({ contents: 'export const recordRecentDoc = () => {}' }))
 b.onLoad({ filter: /\/i18n\/index\.ts$/ }, () => ({ contents: 'export const i18n = { global: { t: key => key } }' }))
} }] })
const { useDocumentEditorCore } = await import(output.href)
const drafts = new Map()
globalThis.localStorage = { setItem: (k,v) => drafts.set(k,v), getItem: k => drafts.get(k), removeItem: k => drafts.delete(k) }
for (const failure of [false, true]) test(`newer draft survives ${failure ? 'failed' : 'successful'} older save`, async () => {
 drafts.clear()
 const currentDoc = ref({ id: 1, name: 'doc', content: 'A', type: 'file', version: 0 })
 const core = useDocumentEditorCore({ currentDoc, kbId: ref(1), documents: ref([]) })
 let resolve, reject
 globalThis.updateDocument = () => new Promise((a,b) => { resolve=a; reject=b })
 const saving = core.saveDocument({ silent: true })
 currentDoc.value.content = 'B'
 core.saveDraftToLocal()
 if (failure) reject(new Error('offline')); else resolve({ version: 1 })
 await saving
 assert.equal(JSON.parse(drafts.get('hellodoc:editor-draft:1:1')).content, 'B')
 assert.equal(core.hasUnsavedChanges.value, true)
 core.clearSaveTimers()
})
test('failed save schedules a retry without another edit', async () => {
 const currentDoc = ref({ id: 1, name: 'doc', content: 'A', type: 'file', version: 0 })
 const core = useDocumentEditorCore({ currentDoc, kbId: ref(1), documents: ref([]) })
 let calls = 0
 globalThis.updateDocument = async () => { if (++calls === 1) throw new Error('offline'); return { version: 1 } }
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
test('save submits the current version and updates it from the server', async () => {
 drafts.clear()
 const currentDoc = ref({ id: 7, name: 'doc', content: 'edited', type: 'file', version: 3 })
 const core = useDocumentEditorCore({ currentDoc, kbId: ref(1), documents: ref([]) })
 let payload
 globalThis.updateDocument = async (_kb, _id, body) => { payload = body; return { version: 4 } }
 assert.equal(await core.saveDocument({ silent: true }), true)
 assert.equal(payload.expectedVersion, 3)
 assert.equal(currentDoc.value.version, 4)
 core.clearSaveTimers()
})
test('version conflict keeps the draft and does not schedule a retry', async () => {
 drafts.clear()
 const currentDoc = ref({ id: 8, name: 'doc', content: 'unsaved', type: 'file', version: 2 })
 const core = useDocumentEditorCore({ currentDoc, kbId: ref(1), documents: ref([]) })
 globalThis.updateDocument = async () => { throw Object.assign(new Error('conflict'), { code: 1014 }) }
 const original = globalThis.setTimeout
 let scheduled = 0
 globalThis.setTimeout = () => { scheduled++; return 1 }
 try {
  assert.equal(await core.saveDocument({ silent: true }), false)
  assert.equal(JSON.parse(drafts.get('hellodoc:editor-draft:1:8')).content, 'unsaved')
  assert.equal(scheduled, 0)
 } finally { globalThis.setTimeout = original; core.clearSaveTimers() }
})
test('reopening a stale local draft does not rebase it onto the newer version', async () => {
 drafts.clear()
 drafts.set('hellodoc:editor-draft:1:9', JSON.stringify({ name: 'doc', content: 'local', version: 2, savedAt: 1 }))
 const currentDoc = ref(null)
 const core = useDocumentEditorCore({ currentDoc, kbId: ref(1), documents: ref([]), activeDocId: ref(null), selectedDocIds: ref(new Set()), lastClickedId: ref(null), kbTitle: ref(''), isSidebarVisible: ref(true), isMobile: ref(false), navigateToDoc: () => {} })
 globalThis.getDocumentDetail = async () => ({ id: 9, name: 'doc', content: 'remote', type: 'file', version: 3 })
 let calls = 0
 globalThis.updateDocument = async () => { calls++; return { version: 4 } }
 await core.loadDocumentById(9, { navigate: false })
 assert.equal(currentDoc.value.content, 'local')
 assert.equal(await core.saveDocument({ silent: true }), false)
 assert.equal(calls, 0)
 assert.equal(JSON.parse(drafts.get('hellodoc:editor-draft:1:9')).version, 2)
 core.clearSaveTimers()
})
test('forced server sync does not discard unsaved edits', async () => {
 drafts.clear()
 const currentDoc = ref({ id: 10, name: 'doc', content: 'unsaved', type: 'file', version: 2 })
 const core = useDocumentEditorCore({ currentDoc, kbId: ref(1), documents: ref([]) })
 let calls = 0
 globalThis.getDocumentDetail = async () => { calls++; return { id: 10, name: 'doc', content: 'server', type: 'file', version: 3 } }
 assert.equal(await core.syncCurrentDocFromServer({ force: true, silent: true }), false)
 assert.equal(currentDoc.value.content, 'unsaved')
 assert.equal(calls, 0)
 assert.equal(JSON.parse(drafts.get('hellodoc:editor-draft:1:10')).content, 'unsaved')
 core.clearSaveTimers()
})
test('discarding a conflicted draft waits for a successful server reload', async () => {
 drafts.clear()
 const currentDoc = ref({ id: 11, name: 'doc', content: 'draft', type: 'file', version: 2 })
 const core = useDocumentEditorCore({ currentDoc, kbId: ref(1), documents: ref([]) })
 globalThis.updateDocument = async () => { throw Object.assign(new Error('conflict'), { code: 1014 }) }
 await core.saveDocument({ silent: true })
 globalThis.getDocumentDetail = async () => { throw new Error('offline') }
 assert.equal(await core.discardConflictingDraftAndReload(), false)
 assert.equal(JSON.parse(drafts.get('hellodoc:editor-draft:1:11')).content, 'draft')
 globalThis.getDocumentDetail = async () => ({ id: 11, name: 'doc', content: 'new server', type: 'file', version: 3 })
 assert.equal(await core.discardConflictingDraftAndReload(), true)
 assert.equal(currentDoc.value.content, 'new server')
 assert.equal(drafts.has('hellodoc:editor-draft:1:11'), false)
 core.clearSaveTimers()
})
test('typing during conflict reload preserves the newer draft', async () => {
 drafts.clear()
 const currentDoc = ref({ id: 12, name: 'doc', content: 'draft', type: 'file', version: 2 })
 const core = useDocumentEditorCore({ currentDoc, kbId: ref(1), documents: ref([]) })
 globalThis.updateDocument = async () => { throw Object.assign(new Error('conflict'), { code: 1014 }) }
 await core.saveDocument({ silent: true })
 let resolve
 globalThis.getDocumentDetail = () => new Promise(r => { resolve = r })
 const reload = core.discardConflictingDraftAndReload()
 currentDoc.value.content = 'typed while loading'
 resolve({ id: 12, name: 'doc', content: 'remote', type: 'file', version: 3 })
 assert.equal(await reload, false)
 assert.equal(currentDoc.value.content, 'typed while loading')
 assert.equal(JSON.parse(drafts.get('hellodoc:editor-draft:1:12')).content, 'typed while loading')
 core.clearSaveTimers()
})
await rm(output, { force: true })
test('a lost collaboration lock preserves the draft without retrying', async () => {
 drafts.clear()
 const currentDoc = ref({ id: 17, name: 'doc', content: 'unsaved', type: 'file', version: 2 })
 const core = useDocumentEditorCore({ currentDoc, kbId: ref(1), documents: ref([]), getCollabSessionId: () => 'session' })
 let submitted
 globalThis.updateDocument = async (_kb, _id, body) => { submitted = body; throw Object.assign(new Error('locked'), { code: 1016 }) }
 const oldTimeout = globalThis.setTimeout; let retries = 0
 globalThis.setTimeout = () => { retries++; return 1 }
 try {
  assert.equal(await core.saveDocument({ silent: true }), false)
  assert.equal(submitted.collabSessionId, 'session')
  assert.equal(JSON.parse(drafts.get('hellodoc:editor-draft:1:17')).content, 'unsaved')
  assert.equal(retries, 0)
 } finally { globalThis.setTimeout = oldTimeout; core.clearSaveTimers() }
})
test('a disconnected collaborator retains the draft without sending a save', async () => {
 drafts.clear()
 const currentDoc = ref({ id: 18, name: 'doc', content: 'unsaved', type: 'file', version: 2 })
 const core = useDocumentEditorCore({ currentDoc, kbId: ref(1), documents: ref([]), canSave: () => false })
 let calls = 0
 globalThis.updateDocument = async () => { calls++; return { version: 3 } }
 assert.equal(await core.saveDocument({ silent: true }), false)
 assert.equal(calls, 0)
 assert.equal(JSON.parse(drafts.get('hellodoc:editor-draft:1:18')).content, 'unsaved')
 core.clearSaveTimers()
})
