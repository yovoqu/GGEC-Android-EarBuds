package com.ggec.earbuds.provider.lib_common.log;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_common.log.YoLogBootstrap;
import com.yovo.lib_common.log.model.FileNamePolicy;
import com.yovo.lib_common.log.model.LogConfig;
import com.yovo.lib_common.log.model.LogLevel;
import com.yovo.lib_common.system.file.model.PathTarget;

/**
 * App 侧 YoLog 配置的落点：只被装配根（{@code wiring/AppContainer}）在建图时调用一次。
 *
 * <p>幂等由 {@link YoLogBootstrap#initOnce} 自己负责（内部双检），本类不自建
 * 「是否已安装」状态、不做安装注册表：重复调用交给库判断，provider 只提供 App 的日志配置。
 * 其它模块（SDK）走 {@link YoLogBootstrap#ensureInitialized} 兜底，不重复初始化。</p>
 */
public final class YoLogProvider {

    private YoLogProvider() {
    }

    /** 用 App 的日志配置做一次幂等初始化：装配根构造期调用。 */
    public static void bootstrap(@NonNull Context context) {
        YoLogBootstrap.initOnce(context.getApplicationContext(), config());
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
