package com.yovo.biz_appupdate.internal.core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.yovo.biz_appupdate.YoAppUpdate;
import com.yovo.biz_appupdate.internal.contract.LogPort;
import com.yovo.biz_appupdate.internal.di.AppUpdateGraph;
import com.yovo.biz_appupdate.internal.engine.SplashGate;
import com.yovo.biz_appupdate.internal.model.DownloadDirSpec;
import com.yovo.biz_appupdate.internal.ui.UpdateUiController;
import com.yovo.biz_appupdate.internal.ui.UpdateViewModel;

/**
 * YoAppUpdate 门面的内部委托核心。
 */
public final class YoAppUpdateCore {

    private static final String TAG = "YoAppUpdateCore";

    private static volatile YoAppUpdateCore instance;

    private final AppUpdateGraph graph;

    private YoAppUpdateCore(@NonNull AppUpdateGraph graph) {
        this.graph = graph;
    }

    public static void install(@NonNull Context context,
                               @NonNull YoAppUpdate.Config config) {
        instance = new YoAppUpdateCore(
                AppUpdateGraph.create(context, ContractMapper.toInternalConfig(config)));
        instance.log().i(TAG, "YoAppUpdate 初始化完成：下载目录类型="
                + instance.graph.config().downloadDir().type()
                + "，自动安装=" + instance.graph.config().autoInstallOnLaunch()
                + "，仅 WiFi=" + instance.graph.config().wifiOnly()
                + "，存储策略=" + instance.graph.config().storagePolicy());
    }

    @NonNull
    public static YoAppUpdateCore get() {
        YoAppUpdateCore local = instance;
        if (local == null) {
            throw new IllegalStateException("YoAppUpdate.init() must be called first");
        }
        return local;
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
        graph.engine().attachSplashIntent(
                intent, ContractMapper.toLaunchDecision(prompt));
    }

    public boolean requestDownloadDirPermissionIfNeeded(@NonNull Activity activity) {
        if (graph.downloadDirAccess().isAccessible()) {
            log().d(TAG, "下载目录已可访问，无需请求权限");
            return true;
        }
        if (graph.config().downloadDir().type() == DownloadDirSpec.Type.SAF) {
            log().d(TAG, "SAF 下载目录，不请求存储权限");
            return true;
        }
        boolean requested = graph.downloadDirPermission().requestIfNeeded(
                activity, graph.config().downloadDir().type());
        log().d(TAG, "请求下载目录权限结果=" + requested);
        return requested;
    }

    public boolean requestInstallUnknownSources(@NonNull Activity activity) {
        boolean requested = graph.installPermission().requestIfNeeded(activity);
        log().d(TAG, "请求安装未知来源权限结果=" + requested);
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

    @NonNull
    public YoAppUpdate.UpdateUiSession attachUpdateUi(
            @NonNull Activity activity,
            @Nullable Intent launchIntent,
            @NonNull YoAppUpdate.UpdateUiHost host) {
        UpdateViewModel viewModel = createViewModel(activity);
        viewModel.attach(launchIntent);
        UpdateUiController controller =
                new UpdateUiController(activity, viewModel, host);
        log().d(TAG, "更新 UI 已挂载");
        return new YoAppUpdate.UpdateUiSession() {
            @Override
            public void checkUpdate() {
                controller.checkUpdate();
            }

            @Override
            public void detach() {
                controller.detach();
            }

            @Override
            public void onSafTreeResult(boolean granted) {
                controller.onSafTreeResult(granted);
            }

            @Override
            public void onDownloadDirPermissionResult(boolean granted) {
                controller.onDownloadDirPermissionResult(granted);
            }

            @Override
            public void onDownloadDirPermissionResume() {
                controller.onDownloadDirPermissionResume();
            }

            @Override
            public void onDownloadDirPermissionPermanentlyDenied() {
                controller.onDownloadDirPermissionPermanentlyDenied();
            }

            @Override
            public void onInstallPermissionResult() {
                controller.onInstallPermissionResult();
            }
        };
    }

    @NonNull
    private UpdateViewModel createViewModel(@NonNull Activity activity) {
        if (activity instanceof ViewModelStoreOwner) {
            return new ViewModelProvider(
                    (ViewModelStoreOwner) activity, graph.viewModelFactory())
                    .get(UpdateViewModel.class);
        }
        return new UpdateViewModel(graph.engine());
    }

    @NonNull
    private LogPort log() {
        return graph.logPort();
    }
}
