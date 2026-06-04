package com.aiagent.config;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Bulkhead for long-running AI requests.
 */
@Component
public class AiRequestLimiter {

    private final Semaphore permits;
    private final int maxConcurrentRequests;
    private final boolean queueEnabled;
    private final int queueCapacity;
    private final ThreadPoolExecutor workerExecutor;

    public AiRequestLimiter(@Value("${app.ai.max-concurrent-requests:20}") int maxConcurrentRequests,
                            @Value("${app.ai.queue.enabled:false}") boolean queueEnabled,
                            @Value("${app.ai.queue.worker-threads:8}") int workerThreads,
                            @Value("${app.ai.queue.capacity:200}") int queueCapacity) {
        this.maxConcurrentRequests = Math.max(1, maxConcurrentRequests);
        this.permits = new Semaphore(this.maxConcurrentRequests);
        this.queueEnabled = queueEnabled;
        this.queueCapacity = Math.max(1, queueCapacity);
        int workers = Math.max(1, workerThreads);
        this.workerExecutor = queueEnabled
                ? new ThreadPoolExecutor(
                workers,
                workers,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(this.queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "ai-worker");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        )
                : null;
    }

    public <T> T call(Supplier<T> supplier) {
        if (queueEnabled) {
            return queuedCall(supplier);
        }
        acquire();
        try {
            return supplier.get();
        } finally {
            permits.release();
        }
    }

    public <T> Flux<T> flux(Supplier<Flux<T>> supplier) {
        if (queueEnabled) {
            return Flux.defer(supplier).subscribeOn(Schedulers.fromExecutor(workerExecutor));
        }
        acquire();
        try {
            return supplier.get().doFinally(signalType -> permits.release());
        } catch (RuntimeException e) {
            permits.release();
            throw e;
        }
    }

    public SseEmitter sse(Supplier<SseEmitter> supplier) {
        acquire();
        AtomicBoolean released = new AtomicBoolean(false);
        try {
            SseEmitter emitter = supplier.get();
            Runnable releaseOnce = () -> {
                if (released.compareAndSet(false, true)) {
                    permits.release();
                }
            };
            emitter.onCompletion(releaseOnce);
            emitter.onTimeout(releaseOnce);
            emitter.onError(error -> releaseOnce.run());
            return emitter;
        } catch (RuntimeException e) {
            if (released.compareAndSet(false, true)) {
                permits.release();
            }
            throw e;
        }
    }

    private void acquire() {
        if (!permits.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI 请求繁忙，请稍后再试");
        }
    }

    public int maxConcurrentRequests() {
        return queueEnabled ? workerExecutor.getMaximumPoolSize() : maxConcurrentRequests;
    }

    public int availablePermits() {
        return queueEnabled ? Math.max(0, workerExecutor.getMaximumPoolSize() - workerExecutor.getActiveCount()) : permits.availablePermits();
    }

    public int inUse() {
        return queueEnabled ? workerExecutor.getActiveCount() : maxConcurrentRequests - permits.availablePermits();
    }

    public boolean queueEnabled() {
        return queueEnabled;
    }

    public int queueDepth() {
        return queueEnabled ? workerExecutor.getQueue().size() : 0;
    }

    public int queueCapacity() {
        return queueEnabled ? queueCapacity : 0;
    }

    @PreDestroy
    public void shutdown() {
        if (workerExecutor != null) {
            workerExecutor.shutdown();
        }
    }

    private <T> T queuedCall(Supplier<T> supplier) {
        try {
            Future<T> future = workerExecutor.submit(supplier::get);
            return future.get();
        } catch (RejectedExecutionException e) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI 请求排队已满，请稍后再试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 请求已中断，请稍后再试");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }
}
