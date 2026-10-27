package com.ivan_naumenko.repository_analyzer.cache;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Bounds completed entries; concurrent callers share one computation, failures can be retried. */
public final class BoundedSingleFlightCache<K, V> {
    private final int capacity;
    private final Map<K, V> completed = new LinkedHashMap<>(16, .75f, true);
    private final Map<K, CompletableFuture<V>> running = new HashMap<>();

    public BoundedSingleFlightCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public V get(K key, Supplier<V> loader) {
        CompletableFuture<V> future;
        boolean owner;
        synchronized (this) {
            if (completed.containsKey(key)) return completed.get(key);
            future = running.get(key);
            owner = future == null;
            if (owner) { future = new CompletableFuture<>(); running.put(key, future); }
        }
        if (!owner) return future.join();
        try {
            V result = java.util.Objects.requireNonNull(loader.get());
            synchronized (this) {
                completed.put(key, result);
                while (completed.size() > capacity) completed.remove(completed.keySet().iterator().next());
                running.remove(key);
            }
            future.complete(result);
            return result;
        } catch (Throwable error) {
            synchronized (this) { running.remove(key); }
            future.completeExceptionally(error);
            throw error;
        }
    }
}
