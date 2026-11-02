import test from 'node:test';
import assert from 'node:assert/strict';
import { authenticatedFetch } from '../transport.mjs';

test('refreshes only after 401 and retries with the same request body', async () => {
  const seen = [];
  const auth = { accessToken: async () => 'old', refresh: async value => { assert.equal(value, 'old'); return 'new'; } };
  const transport = authenticatedFetch(auth, async (input, init) => {
    const request = new Request(input, init);
    seen.push({ token: request.headers.get('authorization'), body: await request.text() });
    return new Response('', { status: seen.length === 1 ? 401 : 200 });
  });
  const result = await transport('http://localhost/mcp', { method: 'POST', body: '{"id":1}' });
  assert.equal(result.status, 200);
  assert.deepEqual(seen, [{token: 'Bearer old', body: '{"id":1}'}, {token: 'Bearer new', body: '{"id":1}'}]);
});
