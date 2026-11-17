package com.yovo.lib_common.log;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.log.model.InitResult;
import com.yovo.lib_common.log.model.LogConfig;

/**
 * YoLog 唯一初始化门面：进程内**只允许真正初始化一次**，且配置只来自宿主显式 init。
 *
 * <p>为什么要这个类：{@link YoLog#init(Context, LogConfig)} 在已初始化时会先拆除现有日志管线再重建。
 * 因此任意位置的重复 init 都会打断日志（丢日志/重建文件）。本类提供幂等入口：</p>
 * <ul>
 *   <li>{@link #initOnce(Context, LogConfig)}：App 唯一 owner 使用，已初始化则直接返回，不重复 init；</li>
 *   <li>{@link #requireInitialized()}：SDK 入口断言「宿主已完成初始化」，未初始化直接抛错。</li>
 * </ul>
 *
 * <p>本类不再提供「默认配置兜底初始化」：兜底会让第二个配置源在宿主不知情时把日志配置
 * 改写成 logcat-only，{@code YoLogProvider} 的配置因此可能不生效。SDK 侧要么显式断言，
 * 要么什么都不做。</p>
 *
 * <p>约定：除 App 唯一 owner 外，任何地方都不应直接调用 {@code YoLog.init(...)}
 * 或 {@code YoLogBootstrap.initOnce(...)}（Gradle 静态校验强制）。</p>
 *
 * <p>本类不持任何状态：初始化幂等标志与最近一次结果都由 {@link YoLog} 持有的日志核心实例管理。</p>
 */
public final class YoLogBootstrap {

    private YoLogBootstrap() {
    }

    /**
     * 只初始化一次：已初始化则跳过（不重复 init）。
     *
     * @return 是否本次发生了初始化
     */
    public static boolean initOnce(@NonNull Context context, @NonNull LogConfig config) {
        return YoLog.initOnce(context.getApplicationContext(), config);
    }

    /**
     * 断言日志已由宿主显式初始化。
     *
     * <p>SDK 入口在需要写日志前调用本方法：未初始化说明宿主漏了唯一的
     * {@code initOnce} 装配，属于配置错误，直接抛出比「自动兜底成默认配置」更可诊断。</p>
     *
     * @throws IllegalStateException 日志尚未初始化
     */
    public static void requireInitialized() {
        if (!YoLog.isInitialized()) {
            throw new IllegalStateException(
                    "YoLog 未初始化：宿主必须在使用 SDK 前调用 YoLogBootstrap.initOnce(...)");
        }
    }

    public static boolean isInitialized() {
        return YoLog.isInitialized();
    }

    @Nullable
    public static InitResult lastResult() {
        return YoLog.lastResult();
    }
}
