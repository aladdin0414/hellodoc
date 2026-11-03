import test from 'node:test';
import assert from 'node:assert/strict';
import { RecoveringClient } from '../reconnect.mjs';

const expired = () => Object.assign(new Error('Session not found'), { code: 404 });
test('concurrent expired calls share one reconnect and preserve request arguments', async () => {
  let connections = 0; const calls = []; const arg = { name: 'create_document', arguments: { requestId: 'same' } };
  const factory = async () => {
    const generation = ++connections;
    return { client: { callTool: async data => { calls.push(data); if (generation === 1) throw expired(); return { ok: true }; }, close: async () => {} }, transport: {} };
  };
  const remote = new RecoveringClient(factory);
  await remote.connect();
  assert.deepEqual(await Promise.all([remote.call('callTool', arg), remote.call('callTool', arg)]), [{ok:true},{ok:true}]);
  assert.equal(connections, 2); assert.ok(calls.every(data => data === arg));
});
test('does not replay unrelated failures or ambiguous network failures', async () => {
  let calls = 0, connections = 0;
  const error = Object.assign(new Error('socket reset'), { code: 'ECONNRESET' });
  const remote = new RecoveringClient(async () => { connections++; return {client: {callTool: async () => {calls++; throw error;}, close: async () => {}}, transport: {}}; });
  await remote.connect(); await assert.rejects(remote.call('callTool', {}), error);
  assert.equal(calls, 1); assert.equal(connections, 1);
});
test('does not treat unrelated 404 as an expired session', async () => {
  const error = Object.assign(new Error('endpoint missing'), {code: 404}); let connections = 0;
  const remote = new RecoveringClient(async () => { connections++; return {client: {listTools: async () => {throw error;}, close: async () => {}}, transport: {}}; });
  await remote.connect(); await assert.rejects(remote.call('listTools'), error); assert.equal(connections, 1);
});

test('reconnects after a real SDK HTTP session-not-found response', async t => {
  const { createServer } = await import('node:http');
  const { Client } = await import('@modelcontextprotocol/sdk/client/index.js');
  const { StreamableHTTPClientTransport } = await import('@modelcontextprotocol/sdk/client/streamableHttp.js');
  let generation = 0; let retries = 0;
  const server = createServer(async (req, res) => {
    if (req.method === 'GET') { res.writeHead(405).end(); return; }
    if (req.method === 'DELETE') { res.writeHead(200).end(); return; }
    let raw = ''; for await (const chunk of req) raw += chunk;
    const request = JSON.parse(raw);
    if (request.method === 'notifications/initialized') { res.writeHead(202).end(); return; }
    res.setHeader('Content-Type', 'application/json');
    if (request.method === 'initialize') {
      generation++; res.setHeader('Mcp-Session-Id', `session-${generation}`);
      res.end(JSON.stringify({jsonrpc:'2.0', id:request.id, result:{protocolVersion:'2025-06-18', capabilities:{tools:{}}, serverInfo:{name:'test', version:'1'}}})); return;
    }
    if (req.headers['mcp-session-id'] === 'session-1') { res.writeHead(404).end(JSON.stringify({error:'Session not found'})); return; }
    retries++; res.end(JSON.stringify({jsonrpc:'2.0', id:request.id, result:{tools:[]}}));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => server.close());
  const address = server.address();
  const remote = new RecoveringClient(async () => {
    const client = new Client({name:'test',version:'1'});
    const transport = new StreamableHTTPClientTransport(new URL(`http://127.0.0.1:${address.port}/mcp`));
    await client.connect(transport); return {client,transport};
  });
  await remote.connect();
  assert.deepEqual((await remote.call('listTools')).tools, []);
  assert.equal(generation,2); assert.equal(retries,1);
  await remote.close();
});
