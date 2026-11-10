package com.ggec.earbuds.provider.lib_common;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_common.log.YoLogBootstrap;
import com.yovo.lib_common.log.model.FileNamePolicy;
import com.yovo.lib_common.log.model.LogConfig;
import com.yovo.lib_common.log.model.LogLevel;
import com.yovo.lib_common.system.file.model.PathTarget;

/**
 * App 侧 YoLog 唯一初始化 Provider（单点 owner）。
 *
 * <p>App 只在这里、且只通过 {@link YoLogBootstrap#initOnce} 初始化 YoLog 一次；重复调用为幂等 no-op。
 * 其它模块（SDK）只通过 {@link YoLogBootstrap#ensureInitialized} 兜底，不重复初始化。</p>
 */
public final class YoLogProvider {

    private static volatile boolean installed;

    private YoLogProvider() {
    }

    /** 安装日志：仅在未初始化时真正初始化一次。 */
    public static void install(@NonNull Context context) {
        if (installed) {
            return;
        }
        synchronized (YoLogProvider.class) {
            if (installed) {
                return;
            }
            YoLogBootstrap.initOnce(context.getApplicationContext(), config());
            installed = true;
        }
    }

    @NonNull
    private static LogConfig config() {
        return LogConfig.builder()
                .defaultTag("Earbuds")
                .minLevel(LogLevel.DEBUG)
                .enableLogcat(true)
                .enableFile(true)
                .fileTarget(PathTarget.externalFiles("YoSdkLog"))
                .fileNamePolicy(FileNamePolicy.fixed("earbuds.log"))
                .installCrashFlush(true)
                .build();
    }
}
