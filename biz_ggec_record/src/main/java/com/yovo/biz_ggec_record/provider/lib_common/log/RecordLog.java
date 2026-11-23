package com.yovo.biz_ggec_record.provider.lib_common.log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.log.YoLog;
import com.yovo.lib_common.log.YoLogBootstrap;

/**
 * Record 模块唯一日志门面，同时是 {@code lib_common.log} 的 Provider 接入点
 * （对齐 {@code biz_ggec_hs.HsLog} / {@code biz_ggec_wy.WyLog}）。
 *
 * <p>本类只做一行转发：日志级别、落盘、滚动与降级全部由 {@code lib_common.log} 决定；
 * 模块内不再出现 {@code android.util.Log}，也不复制任何日志实现。</p>
 *
 * <p>初始化不是本类的职责：宿主须在使用 SDK 前调用 {@code YoLogBootstrap.initOnce(...)}，
 * 这里只做断言，不做默认配置兜底。</p>
 */
public final class RecordLog {

    private RecordLog() {
    }

    /**
     * 断言宿主已完成日志初始化。
     *
     * @throws IllegalStateException 日志尚未初始化
     */
    public static void requireInitialized() {
        YoLogBootstrap.requireInitialized();
    }

    public static void d(@NonNull String tag, @NonNull String message) {
        YoLog.d(tag, message);
    }

    public static void d(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        YoLog.d(tag, message, throwable);
    }

    public static void i(@NonNull String tag, @NonNull String message) {
        YoLog.i(tag, message);
    }

    public static void i(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        YoLog.i(tag, message, throwable);
    }

    public static void w(@NonNull String tag, @NonNull String message) {
        YoLog.w(tag, message);
    }

    public static void w(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        YoLog.w(tag, message, throwable);
    }

    public static void e(@NonNull String tag, @NonNull String message) {
        YoLog.e(tag, message);
    }

    public static void e(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        YoLog.e(tag, message, throwable);
    }
}
