import assert from 'node:assert/strict'
import { test } from 'node:test'
import { build } from 'esbuild'
import { parse, compileScript } from '@vue/compiler-sfc'
import { readFile, rm } from 'node:fs/promises'
import { JSDOM } from 'jsdom'

const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost' })
for (const key of ['window', 'document', 'navigator', 'Node', 'Element', 'HTMLElement', 'SVGElement', 'MutationObserver', 'DOMParser', 'localStorage', 'sessionStorage']) {
  Object.defineProperty(globalThis, key, { value: dom.window[key], configurable: true })
}
globalThis.requestAnimationFrame = callback => setTimeout(callback, 0)
globalThis.cancelAnimationFrame = clearTimeout
const output = new URL('../node_modules/.tmp/mobile-version-regressions.mjs', import.meta.url)
await build({
  stdin: { contents: "export { default } from './src/mobile/views/DocEditView.vue'", resolveDir: new URL('..', import.meta.url).pathname },
  bundle: true, platform: 'node', format: 'esm', outfile: output.pathname, external: ['vue'],
  plugins: [{ name: 'mocks', setup(b) {
    b.onResolve({ filter: /^vue-router$/ }, () => ({ path: 'router', namespace: 'mock' }))
    b.onResolve({ filter: /^vue-i18n$/ }, () => ({ path: 'i18n', namespace: 'mock' }))
    b.onResolve({ filter: /^lucide-vue-next$/ }, () => ({ path: 'icons', namespace: 'mock' }))
    b.onLoad({ filter: /.*/, namespace: 'mock' }, ({ path }) => {
      if (path === 'router') return { contents: "export const useRoute=()=>({params:{kbId:1,docId:8},query:{mode:'edit'}}); export const useRouter=()=>({back(){}})" }
      if (path === 'i18n') return { contents: "export const useI18n=()=>({t:k=>k})" }
      return { contents: "const C={render(){return null}};export {C as Eye,C as Edit3,C as Loader2,C as Check,C as Sun,C as Moon,C as Image,C as Undo2,C as Redo2}" }
    })
    b.onLoad({ filter: /\/api\/kb\.ts$/ }, () => ({ contents: "export const getKbDetail=async()=>({role:'EDITOR'});export const getAuthDocumentDetail=()=>globalThis.mobileDetail()" }))
    b.onLoad({ filter: /\/api\/document\.ts$/ }, () => ({ contents: "export const updateDocument=(...a)=>globalThis.mobileUpdate(...a)" }))
    b.onLoad({ filter: /\/api\/asset\.ts$/ }, () => ({ contents: "export const uploadAsset=async()=>({})" }))
    b.onLoad({ filter: /\/utils\/message\.ts$/ }, () => ({ contents: "export const message={error(){},warning(){},success(){}}" }))
    b.onLoad({ filter: /\/mobile\/composables\/useTheme\.ts$/ }, () => ({ contents: "import {ref} from 'vue';export const useTheme=()=>({isDark:ref(false),toggleTheme(){}})" }))
    b.onLoad({ filter: /\/setupMdEditor\.[tj]s$/ }, () => ({ contents: '' }))
    b.onLoad({ filter: /\.vue$/ }, async ({ path }) => {
      if (!path.endsWith('/DocEditView.vue') && !path.endsWith('/mobile/components/ConfirmDialog.vue')) return { contents: "export default { render(){return null} }" }
      const { descriptor } = parse(await readFile(path, 'utf8'), { filename: path })
      return { contents: compileScript(descriptor, { id: 'mobile-test', inlineTemplate: true }).content, loader: 'ts' }
    })
  } }]
})
const { default: DocEditView } = await import(output.href)
const { createApp, nextTick } = await import('vue')
const settle = async () => { await new Promise(resolve => setTimeout(resolve, 20)); await nextTick() }

test('mobile conflict preserves the local draft and blocks stale retries after reload', async () => {
  localStorage.clear()
  let serverVersion = 3
  let updates = []
  globalThis.mobileDetail = async () => ({ name: 'doc', content: 'server', version: serverVersion })
  globalThis.mobileUpdate = async (_kb, _id, payload) => { updates.push(payload); throw Object.assign(new Error('conflict'), { code: 1014 }) }
  const host = document.createElement('div'); document.body.appendChild(host)
  let app = createApp(DocEditView); app.mount(host); await settle()
  const textarea = host.querySelector('textarea')
  assert.ok(textarea)
  textarea.value = 'my unsaved text'; textarea.dispatchEvent(new dom.window.Event('input', { bubbles: true }))
  await nextTick(); await new Promise(resolve => setTimeout(resolve, 1400))
  assert.equal(updates.length, 1); assert.equal(updates[0].expectedVersion, 3)
  assert.equal(JSON.parse(localStorage.getItem('hellodoc:mobile-editor-draft:1:8')).content, 'my unsaved text')
  app.unmount(); host.replaceChildren()
  serverVersion = 4
  app = createApp(DocEditView); app.mount(host); await settle()
  assert.equal(host.querySelector('textarea').value, 'my unsaved text')
  host.querySelector('textarea').dispatchEvent(new dom.window.Event('input', { bubbles: true }))
  await nextTick(); await new Promise(resolve => setTimeout(resolve, 1400))
  assert.equal(updates.length, 1)
  assert.equal(JSON.parse(localStorage.getItem('hellodoc:mobile-editor-draft:1:8')).version, 3)
  const discardButton = host.querySelector('[role="alert"] button')
  assert.ok(discardButton)
  const confirmDiscard = async () => { discardButton.click(); await nextTick(); const confirm = [...host.querySelectorAll('button')].find(button => button.textContent.includes('common.confirm')); assert.ok(confirm); confirm.click(); await nextTick(); assert.equal([...host.querySelectorAll('button')].some(button => button.textContent.includes('common.confirm')), false) }
  globalThis.mobileDetail = async () => { throw new Error('offline') }
  await confirmDiscard(); await settle()
  assert.equal(JSON.parse(localStorage.getItem('hellodoc:mobile-editor-draft:1:8')).content, 'my unsaved text')
  let resolveReload
  globalThis.mobileDetail = () => new Promise(resolve => { resolveReload = resolve })
  await confirmDiscard()
  const editing = host.querySelector('textarea')
  editing.value = 'typed during reload'; editing.dispatchEvent(new dom.window.Event('input', { bubbles: true }))
  await nextTick(); resolveReload({ name: 'doc', content: 'new server', version: 4 }); await settle()
  assert.equal(host.querySelector('textarea').value, 'typed during reload')
  assert.equal(JSON.parse(localStorage.getItem('hellodoc:mobile-editor-draft:1:8')).content, 'typed during reload')
  globalThis.mobileDetail = async () => ({ name: 'doc', content: 'new server', version: 4 })
  await confirmDiscard(); await settle()
  assert.equal(host.querySelector('textarea').value, 'new server')
  assert.equal(localStorage.getItem('hellodoc:mobile-editor-draft:1:8'), null)
  app.unmount(); host.remove()
})
test('mobile save carries forward the returned version when typing continues', async () => {
  localStorage.clear()
  let updates = []
  let resolveFirst
  globalThis.mobileDetail = async () => ({ name: 'doc', content: 'server', version: 3 })
  globalThis.mobileUpdate = async (_kb, _id, payload) => {
    updates.push(payload)
    if (updates.length === 1) return new Promise(resolve => { resolveFirst = resolve })
    return { version: 5 }
  }
  const host = document.createElement('div'); document.body.appendChild(host)
  const app = createApp(DocEditView); app.mount(host); await settle()
  const textarea = host.querySelector('textarea')
  textarea.value = 'first'; textarea.dispatchEvent(new dom.window.Event('input', { bubbles: true }))
  await nextTick(); await new Promise(resolve => setTimeout(resolve, 1300))
  assert.equal(updates[0].expectedVersion, 3)
  textarea.value = 'second'; textarea.dispatchEvent(new dom.window.Event('input', { bubbles: true }))
  await nextTick(); resolveFirst({ version: 4 }); await settle()
  await new Promise(resolve => setTimeout(resolve, 1400))
  assert.equal(updates.length, 2); assert.equal(updates[1].expectedVersion, 4)
  assert.equal(updates[1].content, 'second')
  assert.equal(localStorage.getItem('hellodoc:mobile-editor-draft:1:8'), null)
  app.unmount(); host.remove()
})
await rm(output, { force: true })
