package com.ivan_naumenko.repository_analyzer.сonfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
public class AnalysisExecutorConfig {

    @Bean(name = "analysisExecutor", destroyMethod = "shutdown")
    public ExecutorService analysisExecutor(AnalysisProperties properties) {
        ThreadFactory threadFactory = Thread.ofPlatform()
                .name("analysis-worker-", 0)
                .factory();

        return Executors.newFixedThreadPool(
                properties.maxThreads(),
                threadFactory
        );
    }
}
