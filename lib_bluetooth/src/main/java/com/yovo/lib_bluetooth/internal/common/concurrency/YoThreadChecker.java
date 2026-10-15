package com.yovo.lib_bluetooth.internal.common.concurrency;

import android.os.Looper;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 线程归属断言工具，校验当前是否在主线程或指定命名线程上。
 */
public final class YoThreadChecker {

    private YoThreadChecker() {
    }

    /**
     * 断言当前线程为主（UI）线程。
     *
     * @throws IllegalStateException 若在后台线程调用
     */
    public static void assertMainThread() {
        if (!isMainThread()) {
            throw new IllegalStateException(
                    "Expected to be on the main thread but was on thread: "
                            + Thread.currentThread().getName());
        }
    }

    /**
     * 断言当前线程不是主（UI）线程。
     *
     * @throws IllegalStateException 若在主线程调用
     */
    public static void assertNotMainThread() {
        if (isMainThread()) {
            throw new IllegalStateException(
                    "Expected to be on a background thread but was on the main thread");
        }
    }

    /**
     * 断言当前线程名以给定前缀开头。
     *
     * @param namePrefix 期望的线程名前缀
     * @throws IllegalStateException 若当前线程名不匹配
     */
    public static void assertThread(@NonNull String namePrefix) {
        String currentName = Thread.currentThread().getName();
        if (!currentName.startsWith(namePrefix)) {
            throw new IllegalStateException(
                    "Expected thread name to start with '" + namePrefix
                            + "' but was '" + currentName + "'");
        }
    }

    private static boolean isMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }
}
