package com.yovo.lib_ai.internal.common.diagnostics;

import com.yovo.lib_ai.api.YoAiDiagnostics;
import com.yovo.lib_ai.error.YoAiException;

/**
 * 将公开诊断桥 {@link YoAiDiagnostics} 的落点安装为内部诊断总线/日志门面。
 *
 * <p>幂等：只安装一次。由 lib_ai 的运行时入口在 SDK 使用前调用，保证 realtime / speech 等
 * 公开包即使在“不经过 YoAiAssembly”的路径上也能把日志/失败落到 {@link YoAiLogger} /
 * {@link YoAiDiagnosticsBus}。</p>
 */
public final class YoAiDiagnosticsInstaller {

    private static volatile boolean installed;

    private YoAiDiagnosticsInstaller() {
    }

    public static void installSink() {
        if (installed) {
            return;
        }
        synchronized (YoAiDiagnosticsInstaller.class) {
            if (installed) {
                return;
            }
            YoAiDiagnostics.installSink(new YoAiDiagnostics.Sink() {
                @Override
                public void onFailure(YoAiException exception) {
                    YoAiDiagnosticsBus.get().recordFailure(exception);
                }

                @Override
                public void onLog(String level, String tag, String message, Throwable throwable) {
                    switch (level) {
                        case "E":
                            YoAiLogger.e(tag, message, throwable);
                            break;
                        case "W":
                            YoAiLogger.w(tag, message, throwable);
                            break;
                        case "I":
                            YoAiLogger.i(tag, message);
                            break;
                        default:
                            YoAiLogger.d(tag, message);
                            break;
                    }
                }
            });
            installed = true;
        }
    }
}
