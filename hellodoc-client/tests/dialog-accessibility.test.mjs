import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { build } from 'esbuild'
import { parse, compileScript } from '@vue/compiler-sfc'
import { JSDOM } from 'jsdom'
const dom = new JSDOM('<!doctype html><body><button id="opener">open</button></body>', { url: 'http://localhost' })
for (const key of ['window', 'document', 'navigator', 'Node', 'Element', 'HTMLElement', 'SVGElement']) Object.defineProperty(globalThis, key, { value: dom.window[key], configurable: true })
const output = new URL('../node_modules/.tmp/dialog-test.mjs', import.meta.url)
await build({ entryPoints: [new URL('../src/components/shared/BaseDialog.vue', import.meta.url).pathname], bundle: true, format: 'esm', platform: 'node', outfile: output.pathname, external: ['vue'], plugins: [{ name: 'vue', setup(b) { b.onLoad({ filter: /\.vue$/ }, async ({ path }) => ({ contents: compileScript(parse(await readFile(path, 'utf8'), { filename: path }).descriptor, { id: 'test', inlineTemplate: true }).content, loader: 'ts' })) } }] })
const { default: Dialog } = await import(output.href)
const { createApp, h, nextTick, ref } = await import('vue')
test('dialog contains focus, closes with Escape and restores focus', async () => {
 const opener = document.querySelector('#opener'); opener.focus()
 const show = ref(false); const root = document.createElement('div'); document.body.append(root)
 const app = createApp({ render: () => h(Dialog, { show: show.value, onClose: () => { show.value = false } }, { default: () => [h('button', 'first'), h('button', 'last')] }) }); app.mount(root)
 show.value = true; await nextTick(); await nextTick()
 assert.equal(document.querySelector('[role="dialog"]')?.getAttribute('aria-modal'), 'true')
 assert.equal(document.activeElement?.textContent, 'first')
 document.activeElement.dispatchEvent(new dom.window.KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true }))
 assert.equal(document.activeElement?.textContent, 'last')
 document.activeElement.dispatchEvent(new dom.window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
 await nextTick(); await nextTick()
 assert.equal(show.value, false); assert.equal(document.activeElement, opener)
 app.unmount(); root.remove()
})
