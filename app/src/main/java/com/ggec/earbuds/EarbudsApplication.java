package com.ggec.earbuds;

import android.app.Application;

import androidx.annotation.NonNull;

import com.ggec.earbuds.provider.lib_common.YoLogProvider;
import com.ggec.earbuds.provider.lib_common.WindowProvider;
import com.ggec.earbuds.wiring.AppContainer;

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
public final class EarbudsApplication extends Application implements AppContainer.ContainerOwner {

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        // 日志必须最先初始化：全进程唯一 owner，重复调用为幂等 no-op。
        YoLogProvider.install(this);
        container = new AppContainer(this);
        // 必须在任何 Activity 创建之前注册，否则首个 Activity 的 onResume 收不到。
        WindowProvider.installHighRefresh(this);
    }

    @NonNull
    @Override
    public AppContainer container() {
        return container;
    }

    @Override
    public void onTerminate() {
        // best-effort：Android 生产环境不保证对 Application.onTerminate 回调（仅测试/模拟器语义），
        // 因此这里只尝试释放一次底层资源；进程被杀时由系统回收，不视为缺陷。
        if (container != null) {
            container.release();
        }
        super.onTerminate();
    }
}
