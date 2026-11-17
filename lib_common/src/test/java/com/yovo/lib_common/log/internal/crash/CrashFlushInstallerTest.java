package com.yovo.lib_common.log.internal.crash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 崩溃路径会经 {@code YoLog} 上报，需要 Robolectric 提供 {@code android.util.Log}。 */
@RunWith(RobolectricTestRunner.class)
public final class CrashFlushInstallerTest {

    /** 每个用例持有独立安装器：handler 链状态属于实例。 */
    private final CrashFlushInstaller installer = new CrashFlushInstaller();

    @After
    public void tearDown() {
        installer.uninstall();
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
        installer.install((thread, cause) -> {
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
    public void failedDrain_stillChainsToPreviousHandler() {
        AtomicBoolean previousCalled = new AtomicBoolean();
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, throwable) -> previousCalled.set(true));
        installer.install((thread, cause) -> {
            throw new IllegalStateException("drain-failure");
        });

        Thread.getDefaultUncaughtExceptionHandler()
                .uncaughtException(Thread.currentThread(), new IllegalStateException("crash"));

        assertTrue(previousCalled.get());
    }

    @Test
    public void uninstall_restoresPreviousHandler() {
        Thread.UncaughtExceptionHandler previous =
                (thread, throwable) -> {
                };
        Thread.setDefaultUncaughtExceptionHandler(previous);
        installer.install((thread, cause) -> {
        });

        installer.uninstall();

        assertEquals(previous, Thread.getDefaultUncaughtExceptionHandler());
    }

    @Test
    public void uninstall_withNullPrevious_resetsToNull() {
        Thread.setDefaultUncaughtExceptionHandler(null);
        installer.install((thread, cause) -> {
        });

        installer.uninstall();

        assertNull(Thread.getDefaultUncaughtExceptionHandler());
    }
}
