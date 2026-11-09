export class HelloDocTokenAuth {
  #token;
  constructor(token) {
    if (!token || typeof token !== 'string') throw new Error('Token is required.');
    this.#token = token;
  }
  async accessToken() {
    return this.#token;
  }
  async refresh() {
    return this.#token;
  }
}

export class HelloDocAuth {
  #access;
  #refresh;
  #pending;
  constructor(baseUrl, username, passwordProvider, fetchImpl = fetch) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.username = username;
    this.passwordProvider = passwordProvider;
    this.fetch = fetchImpl;
  }
  async #request(path, body) {
    const response = await this.fetch(this.baseUrl + path, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), redirect: 'error',
    });
    if (!response.ok) throw new Error('HelloDoc authentication failed; check your login or reconnect.');
    const result = await response.json();
    const { accessToken, refreshToken } = result.data ?? {};
    if (!accessToken || !refreshToken) throw new Error('HelloDoc authentication returned an invalid response.');
    this.#access = accessToken;
    this.#refresh = refreshToken;
    return accessToken;
  }
  async #login() {
    return this.#request('/api/auth/login', { username: this.username, password: await this.passwordProvider() });
  }
  async #singleFlight(work) {
    if (!this.#pending) this.#pending = work().finally(() => { this.#pending = undefined; });
    return this.#pending;
  }
  async accessToken() {
    return this.#access ?? this.#singleFlight(() => this.#login());
  }
  async refresh(expired) {
    if (this.#access && this.#access !== expired) return this.#access;
    return this.#singleFlight(async () => {
      if (this.#access && this.#access !== expired) return this.#access;
      if (this.#refresh) {
        try { return await this.#request('/api/auth/refresh-token', { refreshToken: this.#refresh }); }
        catch { this.#refresh = undefined; }
      }
      return this.#login();
    });
  }
}
