package com.ivan_naumenko.repository_analyzer.cache;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BoundedSingleFlightCacheTest {
    @Test void parallelReportsShareWorkAndDifferentKeysDoNotBlockEachOther() throws Exception {
        var cache = new BoundedSingleFlightCache<String, String>(2);
        var calls = new AtomicInteger();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var first = executor.submit(() -> cache.get("head", () -> {
                calls.incrementAndGet(); entered.countDown();
                try { assertTrue(release.await(2, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                return "snapshot";
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var second = executor.submit(() -> cache.get("head", () -> { calls.incrementAndGet(); return "duplicate"; }));
            var independent = executor.submit(() -> cache.get("other", () -> "another snapshot"));
            assertEquals("another snapshot", independent.get(1, TimeUnit.SECONDS));
            release.countDown();
            assertEquals("snapshot", first.get(2, TimeUnit.SECONDS));
            assertEquals("snapshot", second.get(2, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        }
    }
    @Test void retriesFailuresAndEvictsLeastRecentlyUsedCompletedEntry() {
        var cache = new BoundedSingleFlightCache<String, String>(2);
        assertThrows(IllegalStateException.class, () -> cache.get("a", () -> { throw new IllegalStateException("read failed"); }));
        assertEquals("A", cache.get("a", () -> "A"));
        cache.get("b", () -> "B");
        cache.get("a", () -> "wrong");
        cache.get("c", () -> "C");
        assertEquals("new B", cache.get("b", () -> "new B"));
    }
}
