package com.yovo.lib_common.log.internal.crash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CrashFlushInstallerTest {

    @After
    public void tearDown() {
        CrashFlushInstaller.uninstall();
        CrashFlushInstaller.resetForTest();
    }

    @Test
    public void install_drainsThenChainsToPreviousHandler() throws Exception {
        AtomicBoolean drained = new AtomicBoolean();
        AtomicBoolean previousCalled = new AtomicBoolean();
        CountDownLatch latch = new CountDownLatch(2);
        Thread.UncaughtExceptionHandler previous =
                (thread, throwable) -> {
                    previousCalled.set(true);
                    latch.countDown();
                };
        Thread.setDefaultUncaughtExceptionHandler(previous);
        CrashFlushInstaller.install((thread, cause) -> {
            drained.set(true);
            latch.countDown();
        });

        Thread thread = new Thread(() -> {
            throw new IllegalStateException("crash-test");
        });
        thread.setUncaughtExceptionHandler(Thread.getDefaultUncaughtExceptionHandler());
        thread.start();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(drained.get());
        assertTrue(previousCalled.get());
    }

    @Test
    public void uninstall_restoresPreviousHandler() {
        Thread.UncaughtExceptionHandler previous =
                (thread, throwable) -> {
                };
        Thread.setDefaultUncaughtExceptionHandler(previous);
        CrashFlushInstaller.install((thread, cause) -> {
        });

        CrashFlushInstaller.uninstall();

        assertEquals(previous, Thread.getDefaultUncaughtExceptionHandler());
    }

    @Test
    public void uninstall_withNullPrevious_resetsToNull() {
        Thread.setDefaultUncaughtExceptionHandler(null);
        CrashFlushInstaller.install((thread, cause) -> {
        });

        CrashFlushInstaller.uninstall();

        assertNull(Thread.getDefaultUncaughtExceptionHandler());
    }
}
