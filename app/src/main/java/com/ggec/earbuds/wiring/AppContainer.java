package com.ggec.earbuds.wiring;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** 进程装配根：只创建应用级图，不让各页面自行 new 一套依赖。 */
public final class AppContainer {

    private final AiGraph ai;
    private final ShellGraph shell;
    private final DeviceGraph device;
    private final AppExecutors executors;

    public AppContainer(@NonNull Context context) {
        executors = new AppExecutors();
        ai = new AiGraph(context.getApplicationContext(), executors);
        shell = new ShellGraph();
        device = new DeviceGraph(context.getApplicationContext());
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
