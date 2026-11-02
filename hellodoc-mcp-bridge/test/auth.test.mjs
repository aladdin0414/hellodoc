import test from 'node:test';
import assert from 'node:assert/strict';
import { HelloDocAuth } from '../auth.mjs';

test('logs in once for concurrent callers and refreshes after an expired access token', async () => {
  const calls = [];
  const fakeFetch = async (url, options) => {
    calls.push(String(url));
    if (String(url).endsWith('/api/auth/login')) return Response.json({ data: { accessToken: 'a1', refreshToken: 'r1' } });
    if (String(url).endsWith('/api/auth/refresh-token')) return Response.json({ data: { accessToken: 'a2', refreshToken: 'r2' } });
    return new Response(null, { status: 404 });
  };
  const auth = new HelloDocAuth('http://localhost', 'test', async () => 'secret', fakeFetch);
  assert.deepEqual(await Promise.all([auth.accessToken(), auth.accessToken()]), ['a1', 'a1']);
  assert.equal(calls.filter(x => x.endsWith('/api/auth/login')).length, 1);
  assert.equal(await auth.refresh('a1'), 'a2');
  assert.equal(await auth.refresh('a1'), 'a2');
  assert.equal(calls.filter(x => x.endsWith('/api/auth/refresh-token')).length, 1);
});

test('rejects invalid credentials without exposing server response', async () => {
  const auth = new HelloDocAuth('http://localhost', 'test', async () => 'secret', async () => Response.json({ message: 'secret' }, { status: 401 }));
  await assert.rejects(auth.accessToken(), error => !error.message.includes('secret'));
});
