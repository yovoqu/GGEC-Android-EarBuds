package com.ggec.earbuds.wiring;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.provider.lib_common.log.YoLogProvider;
import com.ggec.earbuds.provider.lib_common.refresh_rate.WindowProvider;

/**
 * 进程装配根：唯一创建应用级图、唯一触碰 provider 的地方。
 *
 * <p>日志与刷新率这两个进程级能力也在这里安装：装配根是 provider 的唯一引用者，
 * 进程入口只依赖装配根。</p>
 */
public final class AppContainer {

    private final AiGraph ai;
    private final ShellGraph shell;
    private final DeviceGraph device;
    private final AppExecutors executors;

    public AppContainer(@NonNull Application application) {
        Context appContext = application.getApplicationContext();
        // 日志必须最先初始化：全进程唯一 owner，重复调用为幂等 no-op。
        YoLogProvider.bootstrap(appContext);
        // 必须在任何 Activity 创建之前注册，否则首个 Activity 的 onResume 收不到。
        WindowProvider.installHighRefresh(application);
        executors = new AppExecutors();
        ai = new AiGraph(appContext, executors);
        shell = new ShellGraph();
        device = new DeviceGraph(appContext);
    }

    @NonNull
    public AppExecutors executors() {
        return executors;
    }

    @NonNull
    public AiGraph ai() {
        return ai;
    }

    @NonNull
    public ShellGraph shell() {
        return shell;
    }

    @NonNull
    public DeviceGraph device() {
        return device;
    }

    /**
     * 进程级释放：只释放设备子图（停止 BLE 扫描、关闭协议会话、释放 BLE 侧线程池）。
     * AI 与 shell 子图没有进程级资源需要关闭；{@link AppExecutors} 的线程池为 daemon 且开启
     * core thread 超时，随进程退出回收。Android 生产环境不保证回调 {@code onTerminate}，
     * 因此这里是 best-effort。
     */
    public void release() {
        device.release();
    }

    @Nullable
    public static AppContainer from(@NonNull Context context) {
        Context application = context.getApplicationContext();
        if (application instanceof ContainerOwner owner) {
            return owner.container();
        }
        return null;
    }

    public interface ContainerOwner {
        @NonNull
        AppContainer container();
    }
}
