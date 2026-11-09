import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { fileURLToPath } from 'node:url';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';

test('bridge starts with a token and authenticates MCP requests without username or password', async t => {
  const requests = [];
  const server = createServer(async (req, res) => {
    requests.push({ url: req.url, authorization: req.headers.authorization });
    if (req.method === 'GET') { res.writeHead(405).end(); return; }
    if (req.method === 'DELETE') { res.writeHead(200).end(); return; }
    let raw = ''; for await (const chunk of req) raw += chunk;
    const message = JSON.parse(raw);
    if (message.method === 'notifications/initialized') { res.writeHead(202).end(); return; }
    res.setHeader('Content-Type', 'application/json');
    if (message.method === 'initialize') {
      res.setHeader('Mcp-Session-Id', 'test-session');
      res.end(JSON.stringify({ jsonrpc:'2.0', id:message.id, result:{ protocolVersion:'2025-06-18', capabilities:{tools:{}}, serverInfo:{name:'mock',version:'1'} } }));
    } else if (message.method === 'tools/list') {
      res.end(JSON.stringify({jsonrpc:'2.0',id:message.id,result:{tools:[{name:'read_document',description:'Read',inputSchema:{type:'object',properties:{documentId:{type:'number'}}}}]}}));
    } else { res.writeHead(400).end(); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => server.close());
  const env = { ...process.env, HELLODOC_BASE_URL:`http://127.0.0.1:${server.address().port}`, HELLODOC_KB_ID:'1', HELLODOC_MCP_TOKEN:'hdmcp_test_only' };
  delete env.HELLODOC_USERNAME; delete env.HELLODOC_PASSWORD;
  const client = new Client({name:'token-test',version:'1'});
  const transport = new StdioClientTransport({command:process.execPath,args:[fileURLToPath(new URL('../index.mjs', import.meta.url))],env,stderr:'pipe'});
  try {
    await client.connect(transport);
    assert.deepEqual((await client.listTools()).tools.map(tool => tool.name), ['read_document']);
    assert.ok(requests.length >= 2);
    assert.ok(requests.every(request => request.url === '/api/mcp' && request.authorization === 'Bearer hdmcp_test_only'));
  } finally { await client.close(); }
});
