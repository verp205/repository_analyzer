package com.ivan_naumenko.repository_analyzer.llm.client;

import com.ivan_naumenko.repository_analyzer.llm.client.config.LlmProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmClientConcurrencyTest {

    @Test
    void limitsConcurrentTransportCalls() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        LlmTransport transport = new LlmTransport() {
            @Override
            public String cacheNamespace() {
                return "test-provider:test-model";
            }

            @Override
            public String generate(String prompt) {
                int current = active.incrementAndGet();
                maximumActive.accumulateAndGet(current, Math::max);
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test timeout");
                    }
                    return "response";
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                } finally {
                    active.decrementAndGet();
                }
            }
        };
        LlmClient client = new LlmClient(transport, properties(2));

        try (ExecutorService executor = Executors.newFixedThreadPool(6)) {
            List<Future<String>> futures = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                futures.add(executor.submit(() -> client.generate("prompt")));
            }

            await(() -> maximumActive.get() == 2);
            release.countDown();

            for (Future<String> future : futures) {
                assertEquals("response", future.get(5, TimeUnit.SECONDS));
            }
        }

        assertEquals(2, maximumActive.get());
    }

    private LlmProperties properties(int maxConcurrentRequests) {
        return new LlmProperties(
                "stub",
                "test-provider",
                "http://localhost:1234",
                "/v1/chat/completions",
                "test-model",
                "",
                maxConcurrentRequests
        );
    }

    private void await(Check check) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!check.complete() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(check.complete(), "Условие не выполнилось вовремя");
    }

    @FunctionalInterface
    private interface Check {
        boolean complete();
    }
}
