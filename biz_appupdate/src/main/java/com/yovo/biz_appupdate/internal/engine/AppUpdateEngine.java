package com.yovo.biz_appupdate.internal.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.contract.DownloadDirAccessPort;
import com.yovo.biz_appupdate.internal.contract.DownloadProvider;
import com.yovo.biz_appupdate.internal.contract.InstallOutcome;
import com.yovo.biz_appupdate.internal.contract.InstallPermissionPort;
import com.yovo.biz_appupdate.internal.contract.LaunchPromptBridge;
import com.yovo.biz_appupdate.internal.contract.LogPort;
import com.yovo.biz_appupdate.internal.contract.SafTreeStorePort;
import com.yovo.biz_appupdate.internal.contract.UpdateCheckProvider;
import com.yovo.biz_appupdate.internal.contract.VersionProvider;
import com.yovo.biz_appupdate.internal.interactor.CheckUpdateAction;
import com.yovo.biz_appupdate.internal.interactor.DownloadUpdateAction;
import com.yovo.biz_appupdate.internal.interactor.IgnoreVersionAction;
import com.yovo.biz_appupdate.internal.interactor.InstallUpdateAction;
import com.yovo.biz_appupdate.internal.interactor.MarkPromptAction;
import com.yovo.biz_appupdate.internal.interactor.ResolveLaunchUpdateAction;
import com.yovo.biz_appupdate.internal.model.DownloadMode;
import com.yovo.biz_appupdate.internal.model.LaunchDecision;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;

import java.io.File;

/**
 * APP 更新业务引擎：模块内唯一编排入口。
 *
 * <p>只依赖契约端口与用例，不感知 api 配置类型与任何基础设施实现。</p>
 */
public final class AppUpdateEngine {

    private static final String TAG = "AppUpdateEngine";

    private final CheckUpdateAction checkUpdateAction;
    private final MarkPromptAction markPromptAction;
    private final IgnoreVersionAction ignoreVersionAction;
    private final DownloadUpdateAction downloadUpdateAction;
    private final InstallUpdateAction installUpdateAction;
    private final SplashGate splashGate;
    private final VersionProvider versionProvider;
    private final ResolveLaunchUpdateAction resolveLaunchUpdateAction;
    private final LaunchPromptBridge launchPromptBridge;
    private final DownloadDirAccessPort downloadDirAccess;
    private final InstallPermissionPort installPermission;
    private final SafTreeStorePort safTreeStore;
    private final LogPort logPort;
    private final boolean safDownloadDir;

    public AppUpdateEngine(@NonNull CheckUpdateAction checkUpdateAction,
                           @NonNull MarkPromptAction markPromptAction,
                           @NonNull IgnoreVersionAction ignoreVersionAction,
                           @NonNull DownloadUpdateAction downloadUpdateAction,
                           @NonNull InstallUpdateAction installUpdateAction,
                           @NonNull SplashGate splashGate,
                           @NonNull VersionProvider versionProvider,
                           @NonNull ResolveLaunchUpdateAction resolveLaunchUpdateAction,
                           @NonNull LaunchPromptBridge launchPromptBridge,
                           @NonNull DownloadDirAccessPort downloadDirAccess,
                           @NonNull InstallPermissionPort installPermission,
                           @NonNull SafTreeStorePort safTreeStore,
                           @NonNull LogPort logPort,
                           boolean safDownloadDir) {
        this.checkUpdateAction = checkUpdateAction;
        this.markPromptAction = markPromptAction;
        this.ignoreVersionAction = ignoreVersionAction;
        this.downloadUpdateAction = downloadUpdateAction;
        this.installUpdateAction = installUpdateAction;
        this.splashGate = splashGate;
        this.versionProvider = versionProvider;
        this.resolveLaunchUpdateAction = resolveLaunchUpdateAction;
        this.launchPromptBridge = launchPromptBridge;
        this.downloadDirAccess = downloadDirAccess;
        this.installPermission = installPermission;
        this.safTreeStore = safTreeStore;
        this.logPort = logPort;
        this.safDownloadDir = safDownloadDir;
    }

    public void checkUpdate(@NonNull UpdateCheckProvider.Callback callback) {
        logPort.i(TAG, "开始检查更新");
        checkUpdateAction.run(new UpdateCheckProvider.Callback() {
            @Override
            public void onSuccess(@NonNull RemoteUpdate update) {
                logPort.i(TAG, "检查更新成功：版本=" + update.version()
                        + "，有新版本=" + update.hasNewVersion()
                        + "，地址=" + logUrl(update.downloadUrl()));
                callback.onSuccess(update);
            }

            @Override
            public void onError(@NonNull String message) {
                logPort.w(TAG, "检查更新失败: " + message);
                callback.onError(message);
            }
        });
    }

    @NonNull
    public SplashGate.Session runSplashGate(@NonNull SplashGate.Callback callback) {
        logPort.i(TAG, "开始启动检查（SplashGate）");
        return splashGate.run(decision -> {
            logPort.i(TAG, "启动检查完成，决策=" + decision.action());
            callback.onReady(decision);
        });
    }

    public void markAutoPromptShown(@NonNull String remoteVersion) {
        logPort.d(TAG, "标记自动提示已展示，版本=" + remoteVersion);
        markPromptAction.run(remoteVersion);
    }

    public void ignoreVersion(@NonNull String remoteVersion) {
        logPort.d(TAG, "忽略版本: " + remoteVersion);
        ignoreVersionAction.run(remoteVersion);
    }

    public void download(@NonNull DownloadMode mode,
                         @NonNull RemoteUpdate update,
                         @NonNull DownloadProvider.Callback callback) {
        logPort.i(TAG, "开始下载：模式=" + mode + "，版本=" + update.version()
                + "，地址=" + logUrl(update.downloadUrl()));
        downloadUpdateAction.run(mode, update, new DownloadProvider.Callback() {
            @Override
            public void onStarted(@NonNull DownloadMode mode, @NonNull String taskId) {
                logPort.i(TAG, "下载已启动：模式=" + mode + "，任务=" + taskId);
                callback.onStarted(mode, taskId);
            }

            @Override
            public void onStartFailed(@NonNull DownloadMode mode,
                                      @NonNull DownloadProvider.StartFailure failure) {
                logPort.w(TAG, "下载启动失败：模式=" + mode + "，原因=" + failure
                        + "，地址=" + logUrl(update.downloadUrl()));
                callback.onStartFailed(mode, failure);
            }

            @Override
            public void onProgress(@NonNull String taskId, int percent) {
                callback.onProgress(taskId, percent);
            }

            @Override
            public void onSuccess(@NonNull String taskId, @Nullable File file) {
                logPort.i(TAG, "下载成功：任务=" + taskId
                        + (file != null ? "，文件=" + file.getAbsolutePath() : "，无文件"));
                callback.onSuccess(taskId, file);
            }

            @Override
            public void onError(@NonNull String taskId, @NonNull String message) {
                logPort.e(TAG, "下载失败：任务=" + taskId + "，原因=" + message);
                callback.onError(taskId, message);
            }
        });
    }

    @NonNull
    public InstallOutcome install(@NonNull android.content.Context context,
                                  @NonNull File apkFile,
                                  @Nullable RemoteUpdate update) {
        logPort.i(TAG, "开始安装：文件=" + apkFile.getAbsolutePath()
                + (update != null ? "，版本=" + update.version() : "，无远程信息"));
        InstallOutcome outcome = installUpdateAction.run(context, apkFile, update);
        logPort.i(TAG, "安装结果=" + outcome);
        return outcome;
    }

    @NonNull
    public String versionName() {
        return versionProvider.currentVersionName();
    }

    public int versionCode() {
        return versionProvider.currentVersionCode();
    }

    @Nullable
    public LaunchDecision consumeSplashIntent(@Nullable android.content.Intent intent) {
        LaunchDecision decision = launchPromptBridge.consume(intent);
        if (decision != null) {
            logPort.d(TAG, "消费启动 Intent 决策=" + decision.action());
        }
        return decision;
    }

    public void attachSplashIntent(@NonNull android.content.Intent intent,
                                   @NonNull LaunchDecision decision) {
        logPort.d(TAG, "附加启动 Intent 决策=" + decision.action());
        launchPromptBridge.attach(intent, decision);
    }

    /**
     * 下载目录当前是否可访问（权限/SAF 授权判定收敛到端口适配器）。
     */
    public boolean isDownloadDirAccessible() {
        return downloadDirAccess.isAccessible();
    }

    /**
     * 是否已具备“安装未知来源应用”能力（统一委托 lib_common 权限服务）。
     */
    public boolean canInstallUnknownSources() {
        return installPermission.canInstallUnknownSources();
    }

    public boolean shouldPickSafDownloadDir() {
        return safDownloadDir && !downloadDirAccess.isAccessible() && !safTreeStore.isDismissed();
    }

    public boolean isSafDownloadDir() {
        return safDownloadDir;
    }

    public interface LaunchResolutionCallback {
        void onResult(@NonNull LaunchDecision decision);
    }

    /**
     * 直接执行一次启动决策解析（无 Splash 最短展示/超时语义），
     * 用于“下载目录权限授权后自动重跑”。
     */
    public void runLaunchResolution(@NonNull LaunchResolutionCallback callback) {
        logPort.i(TAG, "开始启动决策解析");
        resolveLaunchUpdateAction.run(decision -> {
            logPort.i(TAG, "启动决策解析完成，决策=" + decision.action());
            callback.onResult(decision);
        });
    }

    @NonNull
    private static String logUrl(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) {
            return "(empty)";
        }
        return url.trim().replaceAll("([?&]_api_key=)[^&]*", "$1***");
    }
}
