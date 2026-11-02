export function authenticatedFetch(auth, fetchImpl = fetch) {
  return async (input, init) => {
    const request = new Request(input, init);
    const replay = request.clone();
    const token = await auth.accessToken();
    const firstHeaders = new Headers(request.headers);
    firstHeaders.set('Authorization', `Bearer ${token}`);
    const response = await fetchImpl(request, { headers: firstHeaders, redirect: 'error' });
    if (response.status !== 401) return response;
    await response.body?.cancel();
    const next = await auth.refresh(token);
    const retryHeaders = new Headers(replay.headers);
    retryHeaders.set('Authorization', `Bearer ${next}`);
    return fetchImpl(replay, { headers: retryHeaders, redirect: 'error' });
  };
}
