import test from 'node:test';
import assert from 'node:assert/strict';
import { scopeTool, scopeArguments } from '../scope.mjs';

test('hides the configured knowledge base from tools and prevents callers overriding it', () => {
  const original = { name: 'list_items', inputSchema: { type: 'object', properties: { knowledgeBaseId: {type: 'integer'}, name: {type: 'string'} }, required: ['knowledgeBaseId'] } };
  const scoped = scopeTool(original);
  assert.equal(scoped.inputSchema.properties.knowledgeBaseId, undefined);
  assert.deepEqual(scoped.inputSchema.required, []);
  assert.deepEqual(scopeArguments({ knowledgeBaseId: 900, name: 'hello' }, 7), { knowledgeBaseId: 7, name: 'hello' });
  assert.ok(original.inputSchema.properties.knowledgeBaseId);
});

test('global search and read do not receive configured KB and return source URLs', async () => {
  const { prepareToolArguments, addSourceUrls, exposedTools } = await import('../scope.mjs');
  assert.ok(exposedTools.has('search_documents'));
  assert.ok(exposedTools.has('read_document'));
  assert.deepEqual(prepareToolArguments('search_documents', { query: '检索' }, 7), { query: '检索' });
  assert.deepEqual(prepareToolArguments('read_document', { documentId: 4 }, 7), { documentId: 4 });
  assert.deepEqual(prepareToolArguments('create_document', { knowledgeBaseId: 900 }, 7), { knowledgeBaseId: 7 });
  const result = { content: [{ type: 'text', text: JSON.stringify({ results: [{ path: '/kb/9/4' }] }) }] };
  addSourceUrls(result, new URL('https://example.test/'));
  assert.equal(JSON.parse(result.content[0].text).results[0].url, 'https://example.test/kb/9/4');
  const error = { isError: true, content: [{ type: 'text', text: '{"path":"/kb/9/4"}' }] };
  addSourceUrls(error, new URL('https://example.test/'));
  assert.equal(JSON.parse(error.content[0].text).url, undefined);
});
