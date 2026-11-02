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
