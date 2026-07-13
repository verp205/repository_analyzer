package com.ivan_naumenko.repository_analyzer.analysis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Configuration
public class AnalysisExecutorConfig {

    @Bean(name = "analysisExecutor", destroyMethod = "shutdown")
    public ExecutorService analysisExecutor(AnalysisProperties properties) {
        ThreadFactory threadFactory = Thread.ofPlatform()
                .name("analysis-worker-", 0)
                .factory();

        return new ThreadPoolExecutor(
                properties.maxThreads(),
                properties.maxThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
