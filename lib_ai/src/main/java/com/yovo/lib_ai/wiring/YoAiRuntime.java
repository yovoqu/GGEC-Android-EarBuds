package com.yovo.lib_ai.wiring;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.internal.common.diagnostics.YoAiDiagnosticsInstaller;
import com.yovo.lib_ai.internal.common.diagnostics.YoAiLogger;

/**
 * lib_ai 运行时安装入口（公开）：SDK 使用前调用一次，统一完成
 * ① YoLog 兜底初始化（未初始化才初始化） ② 安装公开诊断桥到内部诊断总线/日志门面。
 *
 * <p>幂等、可重复调用。App 的 {@code provider/lib_ai} 在装配根构造时调用本方法，
 * 从而 realtime / speech 等独立路径也能落日志（不只依赖 {@code YoAiAssembly}）。</p>
 */
public final class YoAiRuntime {

    private YoAiRuntime() {
    }

    public static void install(@NonNull Context context) {
        YoAiLogger.ensureInitialized(context.getApplicationContext());
        YoAiDiagnosticsInstaller.installSink();
    }
}
