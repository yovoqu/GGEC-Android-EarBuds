package com.ggec.earbuds.wiring;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.update.port.UpdateUiPort;
import com.ggec.earbuds.platform.update.AndroidUpdateInteractions;
import com.ggec.earbuds.provider.biz_appupdate.AppUpdateProviders;
import com.ggec.earbuds.provider.lib_common.download.YoDownloadProvider;
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
    private final AppUpdateProviders appUpdate;
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
        device = new DeviceGraph(appContext, executors.deviceStore(), System::currentTimeMillis);
        // 设备子图的跟踪由装配根显式启动：DeviceGraph 构造期不自行订阅会话，
        // release() 里的停止因此一定与这次启动成对。
        device.start();
        // 下载引擎是更新能力的底层依赖（启动检查会 queryAll 恢复断点续传任务），
        // 必须在 AppUpdateProviders 之前装好，否则 YoDownload.queryAll 抛未初始化。
        YoDownloadProvider.install(appContext);
        appUpdate = new AppUpdateProviders(appContext);
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

    /** 更新能力的 provider（只给装配根/装配代码用；页面拿 {@link #openUpdateUi}）。 */
    @NonNull
    public AppUpdateProviders appUpdate() {
        return appUpdate;
    }

    /**
     * 为一次宿主生命周期开更新接缝：页面只认 {@link UpdateUiPort}，SDK 类型不出 provider。
     *
     * <p>系统页面动作由平台层实现、在这里注入：适配层因此不拼 {@code Intent}、不认 {@code Activity}
     * 之外的东西。</p>
     */
    @NonNull
    public UpdateUiPort openUpdateUi(@NonNull Activity activity) {
        return appUpdate.openHost(activity, new AndroidUpdateInteractions(activity));
    }

    /**
     * 进程级释放：只释放设备子图（停止台账跟踪、停止 BLE 扫描、关闭协议会话、释放 BLE 侧线程池）。
     * AI 与 shell 子图没有进程级资源需要关闭；{@link AppExecutors} 的线程池为 daemon 且开启
     * core thread 超时，随进程退出回收。更新能力（biz_appupdate）不暴露 close，生命周期与进程一致，
     * 因此这里不为它新增关闭语义。Android 生产环境不保证回调 {@code onTerminate}，
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
