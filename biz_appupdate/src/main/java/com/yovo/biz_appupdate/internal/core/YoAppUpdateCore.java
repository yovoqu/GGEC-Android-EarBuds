package com.yovo.biz_appupdate.internal.core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.YoAppUpdate;
import com.yovo.biz_appupdate.api.UpdateUiPort;
import com.yovo.biz_appupdate.api.UpdateUiSession;
import com.yovo.biz_appupdate.internal.di.AppUpdateGraph;
import com.yovo.biz_appupdate.internal.engine.SplashGate;
import com.yovo.biz_appupdate.internal.presenter.UpdateUiPresenter;
import com.yovo.biz_appupdate.provider.lib_common.log.YoUpdateLogger;

/**
 * YoAppUpdate 门面的内部委托核心：一个门面实例 = 一张依赖图。
 *
 * <p>没有静态可写状态、没有 {@code get()} 单例：装配根持有一个 {@link YoAppUpdate}，
 * 它就持有这一个核心。</p>
 */
public final class YoAppUpdateCore {

    private static final String TAG = "YoAppUpdateCore";

    private final AppUpdateGraph graph;

    private YoAppUpdateCore(@NonNull AppUpdateGraph graph) {
        this.graph = graph;
    }

    @NonNull
    public static YoAppUpdateCore create(@NonNull Context context,
                                         @NonNull YoAppUpdate.Config config) {
        AppUpdateGraph graph = AppUpdateGraph.create(context, ContractMapper.toInternalConfig(config));
        YoUpdateLogger.i(TAG, "YoAppUpdate 装配完成：下载目录类型="
                + graph.config().downloadDir().type()
                + "，自动安装=" + graph.config().autoInstallOnLaunch()
                + "，仅 WiFi=" + graph.config().wifiOnly()
                + "，存储策略=" + graph.config().storagePolicy());
        return new YoAppUpdateCore(graph);
    }

    @NonNull
    public YoAppUpdate.LaunchSession startLaunchCheck(
            @NonNull Activity activity,
            @NonNull YoAppUpdate.LaunchCallback callback) {
        SplashGate.Session session = graph.engine().runSplashGate(
                decision -> callback.onResult(ContractMapper.toPublicPrompt(decision)));
        return session::cancel;
    }

    public void attachLaunchPrompt(@NonNull Intent intent,
                                   @NonNull YoAppUpdate.LaunchPrompt prompt) {
        graph.engine().attachSplashIntent(intent, ContractMapper.toLaunchDecision(prompt));
    }

    public boolean requestInstallUnknownSources(@NonNull Activity activity) {
        boolean requested = graph.installPermission().requestIfNeeded(activity);
        YoUpdateLogger.d(TAG, "请求安装未知来源权限结果=" + requested);
        return requested;
    }

    @NonNull
    public java.io.File downloadRootDir() {
        return graph.apkStorage().rootDir();
    }

    @NonNull
    public String versionName() {
        return graph.engine().versionName();
    }

    public int versionCode() {
        return graph.engine().versionCode();
    }

    /**
     * 每次挂载一个新的呈现器：状态全在引擎与呈现器里，宿主解绑就丢弃，不做跨 Activity 的隐式单例。
     */
    @NonNull
    public UpdateUiSession attachUpdateUi(@NonNull Context context,
                                          @Nullable Intent launchIntent,
                                          @NonNull UpdateUiPort port) {
        UpdateUiPresenter presenter = new UpdateUiPresenter(graph.engine());
        presenter.attach(context, launchIntent, port);
        YoUpdateLogger.d(TAG, "更新 UI 已挂载");
        return presenter;
    }
}
