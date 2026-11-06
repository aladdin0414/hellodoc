import assert from 'node:assert/strict'
import { test } from 'node:test'
import { build } from 'esbuild'
import { rm } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'
const output = 'node_modules/.tmp/batch-document-regressions.mjs'
await build({ entryPoints: ['src/utils/batchDocuments.ts'], bundle: true, platform: 'node', format: 'esm', outfile: output })
const { topLevelSelection, summarizeBatch } = await import(pathToFileURL(process.cwd() + '/' + output).href)
test('selected descendants are covered by selected ancestor even through unselected folders', () => {
 const docs = [{id:1,parentId:null},{id:2,parentId:1},{id:3,parentId:2},{id:4,parentId:null}]
 assert.deepEqual(topLevelSelection(docs, new Set([1,3,4])).map(x=>x.id), [1,4])
})
test('batch summary identifies successful and failed items', () => {
 const items = [{name:'A'}, {name:'B'}]
 assert.deepEqual(summarizeBatch(items, [{status:'fulfilled',value:1},{status:'rejected',reason:'error'}]), {success:1,failed:['B']})
})
await rm(output, {force:true})
