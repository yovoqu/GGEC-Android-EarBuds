package com.yovo.lib_common.system.refresh_rate.model;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

/**
 * 单次异步“请求 → 稳定 → 采样 → 恢复”刷新率诊断会话。
 *
 * <p>实现位于 internal，公共契约只暴露启动/取消/运行状态。</p>
 */
public interface RefreshRateTestSession {

    int DEFAULT_SETTLE_FRAMES = 30;
    int DEFAULT_SAMPLE_INTERVALS = 180;

    @MainThread
    void start(@NonNull RefreshRatePolicy policy, @NonNull Listener listener);

    @MainThread
    void start(@NonNull RefreshRatePolicy policy,
               int settleFrames,
               int sampleIntervals,
               @NonNull Listener listener);

    @MainThread
    void cancel();

    boolean isRunning();

    interface Listener {
        void onComplete(@NonNull RefreshRateTestResult result);
    }
}
