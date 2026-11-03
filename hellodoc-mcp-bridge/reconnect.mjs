// Only replay a request when the server explicitly rejects its expired session.
function sessionNotFound(error) {
  return (error?.code === 404 || error?.code === -32001) && /session not found/i.test(error?.message ?? '');
}
export class RecoveringClient {
  constructor(factory) { this.factory = factory; this.current = null; this.connecting = null; this.closed = false; }
  async connect() {
    if (this.closed) throw new Error('HelloDoc bridge is closed');
    if (!this.connecting) {
      this.connecting = this.factory().then(async next => {
        if (this.closed) { await next.client.close(); throw new Error('HelloDoc bridge is closed'); }
        const old = this.current; this.current = next;
        if (old) await old.client.close().catch(() => {});
        return next;
      }).finally(() => { this.connecting = null; });
    }
    return this.connecting;
  }
  async call(method, args) {
    const connection = this.current ?? await this.connect();
    try { return await connection.client[method](args); }
    catch (error) {
      if (!sessionNotFound(error)) throw error;
      const next = this.current !== connection ? this.current : await this.connect();
      return next.client[method](args);
    }
  }
  async close() {
    this.closed = true;
    const pending = this.connecting;
    if (pending) await pending.catch(() => {});
    const connection = this.current; this.current = null;
    if (connection) {
      try { await connection.transport.terminateSession?.(); } catch { /* Session may already be gone. */ }
      finally { await connection.client.close(); }
    }
  }
}
