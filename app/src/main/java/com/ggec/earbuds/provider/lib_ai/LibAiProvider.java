package com.ggec.earbuds.provider.lib_ai;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.wiring.YoAiRuntime;

/**
 * App 侧 lib_ai 运行时的启动落点：只被装配根（{@code wiring/AiGraph}）在建图时调用一次。
 *
 * <p>幂等由 {@link YoAiRuntime#install} 与其内部的诊断安装器负责，本类不自建
 * 「是否已安装」状态。作用是让 realtime / 语音等独立路径也能落 lib_ai 日志，而不只依赖
 * {@code YoAiAssembly}。</p>
 */
public final class LibAiProvider {

    private LibAiProvider() {
    }

    /** 启动 lib_ai 运行时（日志兜底 + 诊断桥）：装配根构造期调用。 */
    public static void bootstrap(@NonNull Context context) {
        YoAiRuntime.install(context.getApplicationContext());
    }
}
