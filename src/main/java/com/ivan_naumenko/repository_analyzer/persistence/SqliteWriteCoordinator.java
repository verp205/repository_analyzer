package com.ivan_naumenko.repository_analyzer.persistence;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class SqliteWriteCoordinator {

    private final ReentrantLock writeLock = new ReentrantLock(true);

    public <T> T execute(Supplier<T> operation) {
        writeLock.lock();
        try {
            return operation.get();
        } finally {
            writeLock.unlock();
        }
    }

    public void execute(Runnable operation) {
        execute(() -> {
            operation.run();
            return null;
        });
    }
}
