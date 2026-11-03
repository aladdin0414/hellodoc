import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { fileURLToPath } from 'node:url';
const bridge = fileURLToPath(new URL('../index.mjs', import.meta.url));
const client = new Client({name: 'bridge-integration-test', version: '1.0'});
const transport = new StdioClientTransport({command: process.execPath, args: [bridge], env: { ...process.env }, stderr: 'pipe'});
let errors = '';
transport.stderr?.on('data', chunk => { errors += chunk.toString(); });
try {
  await client.connect(transport);
  const {tools} = await client.listTools();
  if (!tools.find(t => t.name === 'create_item') || tools.some(t => t.inputSchema.properties?.knowledgeBaseId)) throw new Error('Unexpected scoped tools');
  const item = await client.callTool({name:'create_item', arguments: {name:'桥接事项', description:'桥接验证', requestId:'bridge-item'}});
  if (item.isError) throw new Error('Item creation failed: ' + JSON.stringify(item.content));
  const itemId = JSON.parse(item.content[0].text).itemId;
  const result = await client.callTool({name:'create_document', arguments: {itemId, name:'桥接总结', markdown:'# 桥接', requestId:'bridge-doc'}});
  if (result.isError) throw new Error('Document creation failed: ' + JSON.stringify(result.content));
  const doc = JSON.parse(result.content[0].text);
  if (!doc.documentId || !doc.url) throw new Error('Missing document ID or URL');
  if (!tools.find(t => t.name === 'search_documents') || !tools.find(t => t.name === 'read_document')) throw new Error('Missing read tools');
  const search = await client.callTool({name:'search_documents', arguments:{query:'桥接总结'}});
  if (search.isError) throw new Error('Search failed: ' + JSON.stringify(search.content));
  const found = JSON.parse(search.content[0].text).results.find(entry => entry.documentId === doc.documentId);
  if (!found?.url) throw new Error('Search missing document or URL');
  const read = await client.callTool({name:'read_document', arguments:{documentId:doc.documentId}});
  if (read.isError) throw new Error('Read failed: ' + JSON.stringify(read.content));
  const page = JSON.parse(read.content[0].text);
  if (page.content !== '# 桥接' || !page.url) throw new Error('Read content or URL mismatch');
  const updateArgs = { documentId: doc.documentId, expectedVersion: page.version, mode: 'append', markdown: '\n更新', requestId: 'bridge-update' };
  const updated = await client.callTool({name:'update_document', arguments:updateArgs});
  if (updated.isError) throw new Error('Update failed: ' + JSON.stringify(updated.content));
  const updatedData = JSON.parse(updated.content[0].text);
  if (!updatedData.url || updatedData.version !== page.version + 1) throw new Error('Update result mismatch');
  const retry = await client.callTool({name:'update_document', arguments:updateArgs});
  if (retry.isError || JSON.parse(retry.content[0].text).version !== updatedData.version) throw new Error('Update retry mismatch');

  process.stdout.write(JSON.stringify({ itemId, documentId: doc.documentId, url: doc.url }));
} catch (error) {
  process.stderr.write(error.message + '\n' + errors);
  process.exitCode = 1;
} finally { await client.close(); }
