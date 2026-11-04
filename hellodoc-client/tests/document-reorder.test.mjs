import { test } from 'node:test'
import assert from 'node:assert/strict'
import { build } from 'esbuild'
import { rm } from 'node:fs/promises'
import { ref } from 'vue'

globalThis.sessionStorage = { getItem: () => null, setItem: () => {} }
globalThis.reorderCalls = []
const output = new URL('../node_modules/.tmp/document-reorder-test.mjs', import.meta.url)
await build({ entryPoints: ['src/composables/editor/useDocumentTreeActions.ts'], bundle: true, format: 'esm', platform: 'node', outfile: output.pathname, external: ['vue'], plugins: [{ name: 'stub-api', setup(builder) {
    builder.onResolve({ filter: /api\/document$/ }, () => ({ path: 'api', namespace: 'stub' }))
    builder.onResolve({ filter: /utils\/message$/ }, () => ({ path: 'message', namespace: 'stub' }))
    builder.onResolve({ filter: /\/i18n$/ }, () => ({ path: 'i18n', namespace: 'stub' }))
    builder.onLoad({ filter: /.*/, namespace: 'stub' }, args => ({ contents: args.path === 'api' ? 'export const updateDocument = async (...a) => { globalThis.reorderCalls.push(["update",...a]) }; export const reorderDocuments = async (...a) => { globalThis.reorderCalls.push(["reorder",...a]); if (globalThis.failReorder) throw new Error("ORDER_CONFLICT") }' : args.path === 'message' ? 'export const message = {error(){}}' : 'export const i18n = {global:{t: x => x}}' }))
} }] })
const { useDocumentTreeActions } = await import(output.href)

test('moving a document within a directory submits one atomic order with the observed sibling order', async () => {
    globalThis.reorderCalls = []
    const documents = ref([{ id: 1, name: 'A', type: 'file', parentId: null, orderNum: 1 }, { id: 2, name: 'B', type: 'folder', parentId: null, orderNum: 2 }])
    const actions = useDocumentTreeActions({ kbId: ref(9), documents, sidebarSearchQuery: ref(''), isMobile: ref(false), activeDocId: ref(null), selectDocument: () => true, fetchDocuments: async () => {} })
    await actions.handleMove(documents.value[1], 'up')
    assert.deepEqual(globalThis.reorderCalls, [['reorder', 9, { parentId: 0, expectedIds: [1, 2], orderedIds: [2, 1] }]])
})

test('dragging siblings also submits one atomic order', async () => {
    globalThis.reorderCalls = []
    const documents = ref([{ id: 1, name: 'A', type: 'file', parentId: 8, orderNum: 1 }, { id: 2, name: 'B', type: 'folder', parentId: 8, orderNum: 2 }])
    const actions = useDocumentTreeActions({ kbId: ref(9), documents, sidebarSearchQuery: ref(''), isMobile: ref(false), activeDocId: ref(null), selectDocument: () => true, fetchDocuments: async () => {} })
    actions.dragState.value = { draggingDoc: documents.value[1], overDocId: 1, dropPosition: 'before' }
    await actions.handleDrop({ preventDefault() {}, dataTransfer: { getData: () => '' } }, documents.value[0])
    assert.deepEqual(globalThis.reorderCalls, [['reorder', 9, { parentId: 8, expectedIds: [1, 2], orderedIds: [2, 1] }]])
})

test('dropping an existing child into its own folder reorders atomically', async () => {
    globalThis.reorderCalls = []
    const documents = ref([{ id: 8, name: 'Folder', type: 'folder', parentId: null, orderNum: 1 }, { id: 1, name: 'A', type: 'file', parentId: 8, orderNum: 1 }, { id: 2, name: 'B', type: 'file', parentId: 8, orderNum: 2 }])
    const actions = useDocumentTreeActions({ kbId: ref(9), documents, sidebarSearchQuery: ref(''), isMobile: ref(false), activeDocId: ref(null), selectDocument: () => true, fetchDocuments: async () => {} })
    actions.dragState.value = { draggingDoc: documents.value[1], overDocId: 8, dropPosition: 'inside' }
    await actions.handleDrop({ preventDefault() {}, dataTransfer: { getData: () => '' } }, documents.value[0])
    assert.deepEqual(globalThis.reorderCalls, [['reorder', 9, { parentId: 8, expectedIds: [1, 2], orderedIds: [2, 1] }]])
})

test('a conflicting reorder refreshes the directory without retrying a stale order', async () => {
    globalThis.reorderCalls = []; globalThis.failReorder = true
    const documents = ref([{ id: 1, name: 'A', type: 'file', parentId: null, orderNum: 1 }, { id: 2, name: 'B', type: 'file', parentId: null, orderNum: 2 }])
    let refreshes = 0
    const actions = useDocumentTreeActions({ kbId: ref(9), documents, sidebarSearchQuery: ref(''), isMobile: ref(false), activeDocId: ref(null), selectDocument: () => true, fetchDocuments: async () => { refreshes++ } })
    const oldError = console.error; console.error = () => {}
    try { await actions.handleMove(documents.value[1], 'up') }
    finally { console.error = oldError; globalThis.failReorder = false }
    assert.equal(refreshes, 1)
    assert.equal(globalThis.reorderCalls.length, 1)
    assert.deepEqual(documents.value.map(doc => doc.orderNum), [1, 2])
})

test.after(async () => { await rm(output, { force: true }) })
