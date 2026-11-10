package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiException;

/**
 * 公开诊断汇聚入口（依赖倒置）。
 *
 * <p>公开包（如 {@code realtime}/{@code speech}/{@code media}）不能直接依赖 internal，
 * 故通过本类上报日志与失败；真正的落点由装配根 {@code YoAiAssembly} 安装的 {@link Sink}
 * 绑定到内部 {@code YoAiDiagnosticsBus} / {@code YoAiLogger}。未安装 Sink 时为空操作，
 * 保证可独立使用。</p>
 */
public final class YoAiDiagnostics {

    /** 落点：失败 + 分级日志。由装配根安装为内部诊断总线/日志门面。 */
    public interface Sink {
        void onFailure(@NonNull YoAiException exception);

        default void onLog(@NonNull String level,
                           @NonNull String tag,
                           @NonNull String message,
                           @Nullable Throwable throwable) {
        }
    }

    private static final Sink NOOP = exception -> {
    };

    private static volatile Sink sink = NOOP;

    private YoAiDiagnostics() {
    }

    /** 安装落点；传 null 恢复为空操作。 */
    public static void installSink(@Nullable Sink value) {
        sink = value == null ? NOOP : value;
    }

    /** 上报一次失败（空操作安全）。 */
    public static void recordFailure(@NonNull YoAiException exception) {
        sink.onFailure(exception);
    }

    // ── 分级日志（供公开包使用；内部落到 YoAiLogger）──────────────

    public static void d(@NonNull String tag, @NonNull String message) {
        sink.onLog("D", tag, message, null);
    }

    public static void i(@NonNull String tag, @NonNull String message) {
        sink.onLog("I", tag, message, null);
    }

    public static void w(@NonNull String tag, @NonNull String message) {
        sink.onLog("W", tag, message, null);
    }

    public static void w(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
        sink.onLog("W", tag, message, throwable);
    }

    public static void e(@NonNull String tag, @NonNull String message) {
        sink.onLog("E", tag, message, null);
    }

    public static void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
        sink.onLog("E", tag, message, throwable);
    }
}
