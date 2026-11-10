package com.ggec.earbuds.provider.lib_ai;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.wiring.YoAiRuntime;

/**
 * App 侧 lib_ai Provider 安装入口：在装配根构造时调用一次。
 *
 * <p>负责安装 lib_ai 运行时（日志兜底 + 诊断桥），使 realtime/语音等独立路径也能落 lib_ai 日志；
 * 幂等，不重复初始化。</p>
 */
public final class LibAiProvider {

    private static volatile boolean installed;

    private LibAiProvider() {
    }

    public static void install(@NonNull Context context) {
        if (installed) {
            return;
        }
        synchronized (LibAiProvider.class) {
            if (installed) {
                return;
            }
            YoAiRuntime.install(context.getApplicationContext());
            installed = true;
        }
    }
}
