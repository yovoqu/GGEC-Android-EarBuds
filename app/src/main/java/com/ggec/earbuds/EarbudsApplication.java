package com.ggec.earbuds;

import android.app.Application;

import com.yovo.lib_common.system.refresh_rate.YoRefreshRate;

/**
 * 进程入口：只做与具体页面无关的窗口级配置。
 *
 * <p>刷新率交给 {@code lib_common} 的刷新率域处理：注册一次 Activity 生命周期回调，
 * 每个 Activity 在 onResume 时申请屏幕支持的最高刷新率（120Hz / 144Hz 等）。
 * 回调里已做「窗口未挂载 / 正在结束」的跳过判断，页面侧不需要再写任何代码。</p>
 *
 * <p>这里只申请最高刷新率，不接管系统设置：用户在系统里选了 60Hz，或厂商策略限制时，
 * 系统会自行裁决，APP 不会强行改系统配置。</p>
 */
public final class EarbudsApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 必须在任何 Activity 创建之前注册，否则首个 Activity 的 onResume 收不到。
        YoRefreshRate.installHighRefreshForAllActivities(this);
    }
}
