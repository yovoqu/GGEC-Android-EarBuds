package com.yovo.biz_appupdate.internal.di;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.model.AppUpdateConfig;
import com.yovo.biz_appupdate.internal.contract.ApkStoragePort;
import com.yovo.biz_appupdate.internal.contract.ApkVerifier;
import com.yovo.biz_appupdate.internal.contract.DownloadDirAccessPort;
import com.yovo.biz_appupdate.internal.contract.DownloadProvider;
import com.yovo.biz_appupdate.internal.contract.InstallPermissionPort;
import com.yovo.biz_appupdate.internal.contract.InstallProvider;
import com.yovo.biz_appupdate.internal.contract.LaunchPromptBridge;
import com.yovo.biz_appupdate.internal.contract.PromptProvider;
import com.yovo.biz_appupdate.internal.contract.UpdateCheckProvider;
import com.yovo.biz_appupdate.internal.contract.UpdateMetaStore;
import com.yovo.biz_appupdate.internal.contract.UpdatePolicy;
import com.yovo.biz_appupdate.internal.contract.VersionProvider;
import com.yovo.biz_appupdate.internal.engine.AppUpdateEngine;
import com.yovo.biz_appupdate.internal.engine.SplashGate;
import com.yovo.biz_appupdate.internal.infrastructure.local.ApkStorage;
import com.yovo.biz_appupdate.internal.infrastructure.local.DownloadDirAccessAdapter;
import com.yovo.biz_appupdate.internal.infrastructure.local.PrefsUpdateMetaStore;
import com.yovo.biz_appupdate.internal.infrastructure.local.PromptStore;
import com.yovo.biz_appupdate.internal.infrastructure.local.SplashIntentBridge;
import com.yovo.biz_appupdate.internal.infrastructure.platform.android.AndroidApkMetaReader;
import com.yovo.biz_appupdate.internal.infrastructure.platform.android.AndroidApkVerifier;
import com.yovo.biz_appupdate.internal.infrastructure.platform.android.AndroidInstallProvider;
import com.yovo.biz_appupdate.internal.infrastructure.platform.android.AndroidNetworkPolicy;
import com.yovo.biz_appupdate.internal.infrastructure.platform.android.AndroidVersionProvider;
import com.yovo.biz_appupdate.provider.lib_common.YoDownloadResumerProvider;
import com.yovo.biz_appupdate.internal.infrastructure.platform.pgy.PgyUpdateCheckProvider;
import com.yovo.biz_appupdate.internal.interactor.CheckUpdateAction;
import com.yovo.biz_appupdate.internal.interactor.DownloadUpdateAction;
import com.yovo.biz_appupdate.internal.interactor.IgnoreVersionAction;
import com.yovo.biz_appupdate.internal.interactor.InstallUpdateAction;
import com.yovo.biz_appupdate.internal.interactor.MarkPromptAction;
import com.yovo.biz_appupdate.internal.interactor.ResolveLaunchUpdateAction;
import com.yovo.biz_appupdate.provider.lib_common.YoDownloadProvider;
import com.yovo.biz_appupdate.provider.lib_common.YoPermissionProvider;
import com.yovo.biz_appupdate.provider.lib_common.FileManagerProvider;
import com.yovo.biz_appupdate.provider.lib_common.log.YoUpdateLogger;

/**
 * SDK 依赖容器：自底向上组装契约端口、基础设施适配器、交互层与引擎。
 */
public final class AppUpdateGraph {

    private final AppUpdateConfig config;
    private final ApkStorage apkStorage;
    private final LaunchPromptBridge launchPromptBridge;
    private final InstallPermissionPort installPermission;
    private final AppUpdateEngine engine;

    private AppUpdateGraph(@NonNull Context context, @NonNull AppUpdateConfig config) {
        this.config = config;
        Context app = context.getApplicationContext();
        YoUpdateLogger.requireInitialized();

        YoPermissionProvider permissionProvider = new YoPermissionProvider(app);
        FileManagerProvider fileManagerProvider = new FileManagerProvider(app);
        apkStorage = new ApkStorage(app, config.downloadDir(), config.apkNamingSpec(),
                new AndroidApkMetaReader(app), fileManagerProvider);
        launchPromptBridge = new SplashIntentBridge();
        DownloadDirAccessPort downloadDirAccess = new DownloadDirAccessAdapter(
                app, config.downloadDir(), permissionProvider, fileManagerProvider);
        installPermission = permissionProvider;

        UpdatePolicy policy = new UpdatePolicy(
                config.downloadUrl(),
                config.wifiOnly(),
                config.autoInstallOnLaunch(),
                config.apkNamingSpec(),
                config.splashMinDelayMs(),
                config.splashCheckTimeoutMs(),
                config.storagePolicy());

        VersionProvider versionProvider = new AndroidVersionProvider(app);
        UpdateCheckProvider checkProvider = new PgyUpdateCheckProvider();
        PromptProvider promptProvider = new PromptStore(app);
        InstallProvider installProvider = new AndroidInstallProvider(config.fileProviderAuthority());
        ApkVerifier apkVerifier = new AndroidApkVerifier(app);
        UpdateMetaStore metaStore = new PrefsUpdateMetaStore(app);
        DownloadProvider downloadProvider =
                new YoDownloadProvider(app, apkStorage, downloadDirAccess);
        AndroidNetworkPolicy networkPolicy = new AndroidNetworkPolicy(app);
        YoDownloadResumerProvider downloadResumer = new YoDownloadResumerProvider(app);

        CheckUpdateAction checkUpdateAction =
                new CheckUpdateAction(checkProvider, versionProvider, policy, networkPolicy,
                        metaStore);
        ResolveLaunchUpdateAction resolveLaunchUpdateAction =
                new ResolveLaunchUpdateAction(
                        checkProvider, versionProvider, metaStore,
                        apkStorage, policy, networkPolicy,
                        promptProvider, downloadResumer);
        MarkPromptAction markPromptAction = new MarkPromptAction(promptProvider);
        IgnoreVersionAction ignoreVersionAction = new IgnoreVersionAction(promptProvider);
        DownloadUpdateAction downloadUpdateAction =
                new DownloadUpdateAction(
                        downloadProvider, metaStore, networkPolicy, policy, apkStorage);
        InstallUpdateAction installUpdateAction =
                new InstallUpdateAction(installProvider, apkVerifier);
        SplashGate splashGate = new SplashGate(resolveLaunchUpdateAction, policy);

        engine = new AppUpdateEngine(
                checkUpdateAction,
                markPromptAction,
                ignoreVersionAction,
                downloadUpdateAction,
                installUpdateAction,
                splashGate,
                versionProvider,
                resolveLaunchUpdateAction,
                launchPromptBridge,
                installPermission);
    }

    @NonNull
    public static AppUpdateGraph create(@NonNull Context context,
                                            @NonNull AppUpdateConfig config) {
        return new AppUpdateGraph(context, config);
    }

    @NonNull
    public AppUpdateConfig config() {
        return config;
    }

    @NonNull
    public ApkStoragePort apkStorage() {
        return apkStorage;
    }

    @NonNull
    public LaunchPromptBridge launchPromptBridge() {
        return launchPromptBridge;
    }

    @NonNull
    public InstallPermissionPort installPermission() {
        return installPermission;
    }

    @NonNull
    public AppUpdateEngine engine() {
        return engine;
    }
}