package com.yovo.lib_common.log.internal.crash;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 可选崩溃 flush：先合成 FATAL（由调用方执行）并限时 drain，再转交原 handler；
 * 支持 {@link #uninstall()} 卸载，避免库长期持有全局 handler。
 */
public final class CrashFlushInstaller {

    public interface CrashDrain {
        void drain(@NonNull Thread thread, @NonNull Throwable cause);
    }

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    @Nullable
    private static volatile Thread.UncaughtExceptionHandler previousHandler;

    @Nullable
    private static volatile Thread.UncaughtExceptionHandler installedHandler;

    private CrashFlushInstaller() {
    }

    public static void install(@NonNull CrashDrain drain) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Thread.UncaughtExceptionHandler handler = (thread, throwable) -> {
            try {
                drain.drain(thread, throwable);
            } catch (Throwable ignored) {
            }
            Thread.UncaughtExceptionHandler previous = previousHandler;
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        };
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        installedHandler = handler;
        Thread.setDefaultUncaughtExceptionHandler(handler);
    }

    /** 仅当当前 handler 仍为本库安装的实例时恢复原 handler，避免覆盖其他库的链。 */
    public static void uninstall() {
        if (!INSTALLED.compareAndSet(true, false)) {
            return;
        }
        Thread.UncaughtExceptionHandler current =
                Thread.getDefaultUncaughtExceptionHandler();
        if (current == installedHandler) {
            Thread.UncaughtExceptionHandler previous = previousHandler;
            // previous 为 null 时显式重置，避免残留本库 handler。
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
        installedHandler = null;
        previousHandler = null;
    }

    static void resetForTest() {
        INSTALLED.set(false);
        installedHandler = null;
        previousHandler = null;
    }
}
