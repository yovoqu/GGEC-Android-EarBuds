package com.yovo.lib_common.log;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.log.model.InitResult;
import com.yovo.lib_common.log.model.LogConfig;
import com.yovo.lib_common.log.model.LogLevel;

/**
 * YoLog 唯一初始化门面：进程内**只允许真正初始化一次**。
 *
 * <p>为什么要这个类：{@link YoLog#init(Context, LogConfig)} 在已初始化时会先拆除现有日志管线再重建。
 * 因此任意位置的重复 init 都会打断日志（丢日志/重建文件）。本类提供幂等入口：</p>
 * <ul>
 *   <li>{@link #initOnce(Context, LogConfig)}：App 唯一 owner 使用，已初始化则直接返回，不重复 init；</li>
 *   <li>{@link #ensureInitialized(Context)}：SDK 入口兜底自动初始化，未初始化才用安全默认配置初始化一次。</li>
 * </ul>
 *
 * <p>约定：除本类外，任何地方都不应直接调用 {@code YoLog.init(...)}（Gradle 静态校验强制）。</p>
 */
public final class YoLogBootstrap {

    private static final Object LOCK = new Object();

    @Nullable
    private static volatile InitResult lastResult;

    private YoLogBootstrap() {
    }

    /**
     * 只初始化一次：已初始化则跳过（不重复 init）。
     *
     * @return 是否本次发生了初始化
     */
    public static boolean initOnce(@NonNull Context context, @NonNull LogConfig config) {
        synchronized (LOCK) {
            if (YoLog.isInitialized()) {
                return false;
            }
            lastResult = YoLog.init(context.getApplicationContext(), config);
            return true;
        }
    }

    /**
     * 兜底初始化：未初始化时用安全默认配置（仅 Logcat）初始化一次。
     *
     * @return 是否本次发生了初始化
     */
    public static boolean ensureInitialized(@NonNull Context context) {
        synchronized (LOCK) {
            if (YoLog.isInitialized()) {
                return false;
            }
            lastResult = YoLog.init(context.getApplicationContext(), defaultConfig());
            return true;
        }
    }

    public static boolean isInitialized() {
        return YoLog.isInitialized();
    }

    @Nullable
    public static InitResult lastResult() {
        return lastResult;
    }

    /**
     * 兜底默认配置：只开 Logcat、DEBUG 级，不落盘。App 应通过 {@code YoLogBootstrap.initOnce}
     * 传入自己的完整配置（开启文件落盘）。
     */
    @NonNull
    public static LogConfig defaultConfig() {
        return LogConfig.builder()
                .defaultTag("YoSDK")
                .minLevel(LogLevel.DEBUG)
                .enableLogcat(true)
                .enableFile(false)
                .build();
    }
}
