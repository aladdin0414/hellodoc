#!/usr/bin/env node
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { CallToolRequestSchema, ListToolsRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { HelloDocAuth } from './auth.mjs';
import { RecoveringClient } from './reconnect.mjs';
import { authenticatedFetch } from './transport.mjs';
import { scopeTool, exposedTools, prepareToolArguments, addSourceUrls } from './scope.mjs';

const execute = promisify(execFile);
const allowed = exposedTools;

async function main() {
  const base = new URL(process.env.HELLODOC_BASE_URL ?? '');
  if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password || base.search || base.hash) {
    throw new Error('HELLODOC_BASE_URL must be an HTTP(S) URL without credentials or query.');
  }
  const kb = Number(process.env.HELLODOC_KB_ID);
  if (!Number.isSafeInteger(kb) || kb <= 0) throw new Error('HELLODOC_KB_ID must be a positive integer.');
  const username = process.env.HELLODOC_USERNAME;
  if (!username) throw new Error('HELLODOC_USERNAME is required.');
  const passwordProvider = async () => {
    if (process.env.HELLODOC_PASSWORD) return process.env.HELLODOC_PASSWORD;
    if (process.platform !== 'darwin') throw new Error('Set HELLODOC_PASSWORD through your client secret mechanism.');
    try {
      const { stdout } = await execute('/usr/bin/security', ['find-generic-password', '-s', 'hellodoc-mcp', '-a', username, '-w']);
      return stdout.replace(/\r?\n$/, '');
    } catch { throw new Error('HelloDoc password not found in macOS Keychain.'); }
  };
  const auth = new HelloDocAuth(base.href.replace(/\/$/, ''), username, passwordProvider);
  const remote = new RecoveringClient(async () => {
    const client = new Client({ name: 'hellodoc-local-bridge', version: '0.1.0' });
    const transport = new StreamableHTTPClientTransport(new URL('api/mcp', base.href.endsWith('/') ? base : new URL(base.href + '/')), {
      fetch: authenticatedFetch(auth),
    });
    try { await client.connect(transport); } catch (error) { await client.close().catch(() => {}); throw error; }
    return { client, transport };
  });
  await remote.connect();
  const server = new Server({ name: 'hellodoc', version: '0.1.0' }, { capabilities: { tools: {} } });
  server.setRequestHandler(ListToolsRequestSchema, async () => {
    const result = await remote.call('listTools');
    return { tools: result.tools.filter(tool => allowed.has(tool.name)).map(scopeTool) };
  });
  server.setRequestHandler(CallToolRequestSchema, async request => {
    if (!allowed.has(request.params.name)) throw new Error('Unsupported HelloDoc tool.');
    const result = await remote.call('callTool', { name: request.params.name, arguments: prepareToolArguments(request.params.name, request.params.arguments ?? {}, kb) });
    if (['create_document', 'search_documents', 'read_document', 'update_document', 'list_folder_children'].includes(request.params.name)) {
      addSourceUrls(result, base);
    }
    return result;
  });
  server.onclose = () => { void remote.close(); };
  process.stdin.once('end', () => { void server.close(); });
  await server.connect(new StdioServerTransport());
}

main().catch(error => {
  process.stderr.write(`HelloDoc MCP: ${error.message}\n`);
  process.exitCode = 1;
});
