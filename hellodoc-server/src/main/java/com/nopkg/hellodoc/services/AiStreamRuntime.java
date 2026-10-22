package com.nopkg.hellodoc.services;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded workers and deadlines for blocking model streams. */
@Component
public class AiStreamRuntime {
    private final AtomicInteger sequence = new AtomicInteger();
    private final ThreadFactory threads = task -> {
        Thread thread = new Thread(task, "ai-stream-" + sequence.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    };
    private final ExecutorService workers = new ThreadPoolExecutor(4, 8, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), threads, new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledThreadPoolExecutor timers = new ScheduledThreadPoolExecutor(2, threads);

    public AiStreamRuntime() { timers.setRemoveOnCancelPolicy(true); }

    public void execute(Runnable action) { workers.execute(action); }
    public ScheduledFuture<?> deadline(Runnable action, long timeoutMs) { return timers.schedule(action, timeoutMs, TimeUnit.MILLISECONDS); }
    public ScheduledFuture<?> heartbeat(Runnable action) { return timers.scheduleAtFixedRate(action, 5, 5, TimeUnit.SECONDS); }
    @PreDestroy public void shutdown() { timers.shutdownNow(); workers.shutdownNow(); }
}
