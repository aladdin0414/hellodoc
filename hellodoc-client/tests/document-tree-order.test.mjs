import { test } from 'node:test'
import assert from 'node:assert/strict'
import { build } from 'esbuild'

const result = await build({ entryPoints: ['src/utils/documentTree.ts'], bundle: true, format: 'esm', platform: 'node', write: false })
const { sortDocuments } = await import('data:text/javascript;base64,' + Buffer.from(result.outputFiles[0].contents).toString('base64'))

test('tree order resolves equal values by ID and puts null last', () => {
    const entries = [
        { id: 9, orderNum: null, type: 'folder', name: 'a' },
        { id: 3, orderNum: 10, type: 'folder', name: 'a' },
        { id: 2, orderNum: 10, type: 'file', name: 'z' },
        { id: 1, orderNum: -1, type: 'file', name: 'x' },
    ]
    assert.deepEqual(entries.sort(sortDocuments).map(item => item.id), [1, 2, 3, 9])
})
