package com.thegamecellar.gameservice.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSyncExecutorTest {

    private AdminSyncExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new AdminSyncExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void trySubmit_runs_task_when_idle() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean ran = new AtomicBoolean(false);

        boolean accepted = executor.trySubmit("test", () -> {
            ran.set(true);
            done.countDown();
        });

        assertThat(accepted).isTrue();
        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(ran).isTrue();
    }

    @Test
    void trySubmit_rejects_when_sync_in_progress() throws InterruptedException {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch firstRelease = new CountDownLatch(1);

        executor.trySubmit("first", () -> {
            firstStarted.countDown();
            try { firstRelease.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        });

        assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(executor.isRunning()).isTrue();

        boolean secondAccepted = executor.trySubmit("second", () -> {});
        assertThat(secondAccepted).isFalse();

        firstRelease.countDown();
    }

    @Test
    void trySubmit_clears_running_flag_after_task_throws() throws InterruptedException {
        CountDownLatch failed = new CountDownLatch(1);
        executor.trySubmit("failing", () -> {
            try {
                throw new RuntimeException("boom");
            } finally {
                failed.countDown();
            }
        });

        assertThat(failed.await(2, TimeUnit.SECONDS)).isTrue();
        // Wait for finally-block to clear the flag (executor task wraps the runnable)
        for (int i = 0; i < 50 && executor.isRunning(); i++) {
            Thread.sleep(20);
        }
        assertThat(executor.isRunning()).isFalse();

        AtomicInteger ran = new AtomicInteger(0);
        boolean accepted = executor.trySubmit("after-failure", () -> ran.incrementAndGet());
        assertThat(accepted).isTrue();
    }
}
