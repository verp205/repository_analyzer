package com.ivan_naumenko.repository_analyzer.analysis.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisExecutorConfigTest {

    @Test
    void rejectsTaskWhenWorkersAndBoundedQueueAreFull() throws Exception {
        AnalysisExecutorConfig config = new AnalysisExecutorConfig();
        ExecutorService executor = config.analysisExecutor(
                new AnalysisProperties(1, 1, 3)
        );
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);

        try {
            executor.submit(() -> {
                workerStarted.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(workerStarted.await(5, TimeUnit.SECONDS));

            executor.submit(() -> {
            });

            assertThrows(
                    RejectedExecutionException.class,
                    () -> executor.submit(() -> {
                    })
            );
        } finally {
            releaseWorker.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
