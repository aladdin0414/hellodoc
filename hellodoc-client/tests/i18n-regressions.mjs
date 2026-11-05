import assert from 'node:assert/strict'
import { test } from 'node:test'
import { build } from 'esbuild'
import { readFile, readdir, rm } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const output = join(root, 'node_modules/.tmp/i18n-regressions.mjs')
await build({ stdin: { contents: "export { default as en } from './src/i18n/locales/en-US/common.ts'; export { default as zh } from './src/i18n/locales/zh-CN/common.ts'", resolveDir: root }, bundle: true, format: 'esm', platform: 'node', outfile: output })
const { en, zh } = await import(pathToFileURL(output).href)
const keys = (obj, prefix = '') => Object.entries(obj).flatMap(([key, value]) => value && typeof value === 'object' && !Array.isArray(value) ? keys(value, `${prefix}${key}.`) : [`${prefix}${key}`])
const exists = (obj, key) => key.split('.').reduce((value, part) => value?.[part], obj) !== undefined
async function files(dir) { return (await Promise.all((await readdir(dir, { withFileTypes: true })).map(async entry => entry.isDirectory() ? files(join(dir, entry.name)) : /\.(vue|ts)$/.test(entry.name) ? [join(dir, entry.name)] : []))).flat() }

test('both locales have identical translation keys', () => { assert.deepEqual(keys(en).sort(), keys(zh).sort()) })
test('translation placeholders match between locales', () => {
 const mismatches = []
 for (const key of keys(en)) {
  const value = (obj) => key.split('.').reduce((current, part) => current?.[part], obj)
  if (typeof value(en) !== 'string' || typeof value(zh) !== 'string') continue
  const placeholders = (text) => [...text.matchAll(/\{([\w]+)\}/g)].map(match => match[1]).sort()
  if (JSON.stringify(placeholders(value(en))) !== JSON.stringify(placeholders(value(zh)))) mismatches.push(key)
 }
 assert.deepEqual(mismatches, [])
})
test('literal translation references resolve in both locales', async () => {
 const missing = []
 for (const file of await files(join(root, 'src'))) {
  if (file.includes('/i18n/locales/')) continue
  const text = await readFile(file, 'utf8')
  const regex = /(?:\$t|\bt|\.t)\(\s*['"]([\w.-]+)['"]/g
  for (const match of text.matchAll(regex)) for (const [language, locale] of [['en-US', en], ['zh-CN', zh]]) if (!exists(locale, match[1])) missing.push(`${file.slice(root.length + 1)}:${text.slice(0, match.index).split('\n').length} ${language} ${match[1]}`)
 }
 assert.deepEqual(missing, [])
})
await rm(output, { force: true })
