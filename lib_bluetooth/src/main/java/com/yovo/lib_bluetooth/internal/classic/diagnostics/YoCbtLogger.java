package com.yovo.lib_bluetooth.internal.classic.diagnostics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description YoCbt 专用日志器，封装 {@link YoBluetoothLogger} 并支持按连接 ID 作用域输出。
 */
public final class YoCbtLogger {

    private static final String TAG_PREFIX = "YoCbt:";

    private final String tag;

    private YoCbtLogger(@NonNull String tag) {
        this.tag = tag;
    }

    // ── 工厂 ───────────────────────────────────────────────────────

    /**
     * 为给定 tag 创建日志器，tag 会加上 "YoCbt:" 前缀。
     */
    @NonNull
    public static YoCbtLogger of(@NonNull String tag) {
        return new YoCbtLogger(TAG_PREFIX + tag);
    }

    /**
     * 创建在 tag 后追加连接 ID 的作用域日志器。
     *
     * @param connectionId 连接标识
     * @return 绑定到该连接的新日志器
     */
    @NonNull
    public YoCbtLogger withConnection(@NonNull String connectionId) {
        return new YoCbtLogger(tag + ":" + connectionId);
    }

    // ── 日志方法 ───────────────────────────────────────────────

    public void d(@NonNull String message) {
        YoBluetoothLogger.d(tag, message);
    }

    public void i(@NonNull String message) {
        YoBluetoothLogger.i(tag, message);
    }

    public void w(@NonNull String message) {
        YoBluetoothLogger.w(tag, message);
    }

    public void e(@NonNull String message) {
        YoBluetoothLogger.e(tag, message, null);
    }

    public void e(@NonNull String message, @Nullable Throwable throwable) {
        YoBluetoothLogger.e(tag, message, throwable);
    }

    // ── 静态便捷方法（以调用方类简单名作为 tag） ─────

    /**
     * 使用调用方类派生的 tag 记录 debug 日志。
     */
    public static void d(@NonNull Class<?> clazz, @NonNull String message) {
        YoBluetoothLogger.d(TAG_PREFIX + clazz.getSimpleName(), message);
    }

    /**
     * 使用调用方类派生的 tag 记录 info 日志。
     */
    public static void i(@NonNull Class<?> clazz, @NonNull String message) {
        YoBluetoothLogger.i(TAG_PREFIX + clazz.getSimpleName(), message);
    }

    /**
     * 使用调用方类派生的 tag 记录 warning 日志。
     */
    public static void w(@NonNull Class<?> clazz, @NonNull String message) {
        YoBluetoothLogger.w(TAG_PREFIX + clazz.getSimpleName(), message);
    }

    /**
     * 使用调用方类派生的 tag 记录 error 日志。
     */
    public static void e(@NonNull Class<?> clazz, @NonNull String message) {
        YoBluetoothLogger.e(TAG_PREFIX + clazz.getSimpleName(), message, null);
    }

    /**
     * 使用调用方类派生的 tag 记录 error 日志及异常。
     */
    public static void e(@NonNull Class<?> clazz, @NonNull String message,
                         @Nullable Throwable throwable) {
        YoBluetoothLogger.e(TAG_PREFIX + clazz.getSimpleName(), message, throwable);
    }

    // ── Object 重写 ──────────────────────────────────────────────

    @NonNull
    @Override
    public String toString() {
        return "YoCbtLogger{tag='" + tag + "'}";
    }
}
