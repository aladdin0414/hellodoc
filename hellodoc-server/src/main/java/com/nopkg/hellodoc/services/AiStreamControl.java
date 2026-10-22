package com.nopkg.hellodoc.services;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Owns cancellation of the pending HTTP request or its streaming response body. */
public final class AiStreamControl {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicReference<Runnable> disconnect = new AtomicReference<>();

    public void attach(Runnable action) {
        disconnect.set(action);
        if (cancelled.get()) {
            disconnect();
            throw new CancellationException("AI generation cancelled or timed out");
        }
    }
    public boolean isCancelled() { return cancelled.get(); }
    public void cancel() { cancelled.set(true); disconnect(); }
    public void detach() { disconnect.set(null); }
    private void disconnect() {
        Runnable action = disconnect.getAndSet(null);
        if (action != null) action.run();
    }
}
