package com.ggec.earbuds.provider.lib_common;

import android.app.Application;

import androidx.annotation.NonNull;

import com.yovo.lib_common.system.refresh_rate.YoRefreshRate;

/**
 * App 侧窗口/刷新率 Provider（按需封装 {@code lib_common} 刷新率域）。
 */
public final class WindowProvider {

    private WindowProvider() {
    }

    /** 为所有 Activity 申请屏幕支持的最高刷新率（幂等注册一次）。 */
    public static void installHighRefresh(@NonNull Application application) {
        YoRefreshRate.installHighRefreshForAllActivities(application);
    }
}
