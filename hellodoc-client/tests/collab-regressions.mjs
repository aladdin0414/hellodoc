import assert from 'node:assert/strict'
import { test } from 'node:test'
import { build } from 'esbuild'
import { ref, effectScope, nextTick } from 'vue'
const output = new URL('../node_modules/.tmp/collab-regressions.mjs', import.meta.url)
await build({ entryPoints: ['src/composables/useDocumentCollab.ts'], bundle: true, platform: 'node', format: 'esm', outfile: output.pathname, external: ['vue'], plugins: [{ name: 'mocks', setup(b) {
 b.onLoad({ filter: /\/utils\/message\.ts$/ }, () => ({ contents: 'export const message = { info(){}, warning(){}, error(){} }' }))
 b.onLoad({ filter: /\/i18n\/index\.ts$/ }, () => ({ contents: 'export const i18n = { global: { t: key => key } }' }))
} }] })
const { useDocumentCollab } = await import(output.href)
globalThis.localStorage = { getItem: () => 'token' }
globalThis.window = { location: { protocol: 'http:', host: 'localhost' } }
globalThis.document = { activeElement: null }
class Socket {
 static OPEN = 1; static CONNECTING = 0; static instances = []
 constructor() { this.readyState = 0; Socket.instances.push(this) }
 send() {} close() { this.readyState = 3 }
 open() { this.readyState = 1; this.onopen() }
 receive(data) { this.onmessage({ data: JSON.stringify(data) }) }
 fail() { this.readyState = 3; this.onclose({ code: 1006 }) }
}
globalThis.WebSocket = Socket
function setup(sync) {
 const scope = effectScope()
 const collab = scope.run(() => useDocumentCollab({ currentDoc: ref({ id: 1, type: 'file' }), isCollabFeatureEnabled: ref(true), onSaveBeforeUnlock: async () => true, onSyncFromServer: sync }))
 return { collab, scope }
}
test('failed sync after acquiring lock keeps editor read-only', async () => {
 const { collab, scope } = setup(async () => false)
 const ws = Socket.instances.at(-1); ws.open()
 ws.receive({ type: 'sync', data: { selfSessionId: 's', locks: [{ lockType: 'DOCUMENT', sessionId: 's' }] } })
 await nextTick(); await nextTick()
 assert.equal(collab.isReadOnlyByCollab.value, true)
 collab.closeCollab(); scope.stop()
})
test('repeated connection failures stop after five attempts', () => {
 const timers = []; const oldTimeout = globalThis.setTimeout; const oldClear = globalThis.clearTimeout
 globalThis.setTimeout = (fn, delay) => { timers.push({ fn, delay }); return timers.length }; globalThis.clearTimeout = () => {}
 try {
  const { collab, scope } = setup(async () => true)
  for (let i = 0; i < 5; i++) { Socket.instances.at(-1).fail(); if (i < 4) timers.at(-1).fn() }
  assert.equal(collab.collabState.value, 'error')
  assert.deepEqual(timers.map(t => t.delay), [2000, 4000, 8000, 10000])
  collab.closeCollab(); scope.stop()
 } finally { globalThis.setTimeout = oldTimeout; globalThis.clearTimeout = oldClear }
})
