package com.thegamecellar.gameservice.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-threaded executor for admin-triggered IGDB syncs. Replaces ad-hoc
 * {@code new Thread()} usage in {@link com.thegamecellar.gameservice.controller.AdminController}
 * (guarantees at most one sync runs at a time, surfaces errors through the
 * normal logging pipeline instead of swallowing them on the default uncaught
 * handler, and shuts down cleanly with the application context).
 */
@Slf4j
@Component
public class AdminSyncExecutor {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "admin-sync");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    /**
     * Submit a sync task. Returns false if another sync is already in progress
     * Caller should respond with 409 Conflict in that case.
     */
    public boolean trySubmit(String name, Runnable task) {
        if (!syncInProgress.compareAndSet(false, true)) {
            log.info("Admin sync rejected (already in progress): {}", name);
            return false;
        }
        executor.submit(() -> {
            log.info("Admin sync started: {}", name);
            long start = System.currentTimeMillis();
            try {
                task.run();
                log.info("Admin sync completed: {} ({} ms)", name, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("Admin sync failed: {} ({})", name, e.getMessage(), e);
            } finally {
                syncInProgress.set(false);
            }
        });
        return true;
    }

    public boolean isRunning() {
        return syncInProgress.get();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("AdminSyncExecutor did not terminate within 30s, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
