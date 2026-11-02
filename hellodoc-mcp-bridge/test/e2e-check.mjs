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
  process.stdout.write(JSON.stringify({ itemId, documentId: doc.documentId, url: doc.url }));
} catch (error) {
  process.stderr.write(error.message + '\n' + errors);
  process.exitCode = 1;
} finally { await client.close(); }
