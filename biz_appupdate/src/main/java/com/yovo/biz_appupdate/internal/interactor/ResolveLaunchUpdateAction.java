package com.yovo.biz_appupdate.internal.interactor;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.contract.ApkStoragePort;
import com.yovo.biz_appupdate.internal.contract.DownloadDirAccessPort;
import com.yovo.biz_appupdate.internal.contract.NetworkPolicy;
import com.yovo.biz_appupdate.internal.contract.PendingDownloadResumer;
import com.yovo.biz_appupdate.internal.contract.PromptProvider;
import com.yovo.biz_appupdate.internal.contract.UpdateCheckProvider;
import com.yovo.biz_appupdate.internal.contract.UpdateMetaStore;
import com.yovo.biz_appupdate.internal.contract.UpdatePolicy;
import com.yovo.biz_appupdate.internal.contract.VersionProvider;
import com.yovo.biz_appupdate.internal.interactor.ApkRetentionDecider.RetentionPlan;
import com.yovo.biz_appupdate.internal.model.CurrentAppVersion;
import com.yovo.biz_appupdate.internal.model.LaunchDecision;
import com.yovo.biz_appupdate.internal.model.LocalApk;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;
import com.yovo.biz_appupdate.internal.model.UpdateMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动编排：扫描 + 保留策略 + 远程检查 + 决策。
 */
public final class ResolveLaunchUpdateAction {

    public interface Callback {
        void onComplete(@NonNull LaunchDecision decision);
    }

    private final UpdateCheckProvider checkProvider;
    private final VersionProvider versionProvider;
    private final UpdateMetaStore metaStore;
    private final ApkStoragePort apkStorage;
    private final UpdatePolicy policy;
    private final NetworkPolicy networkPolicy;
    private final PromptProvider promptProvider;
    private final PendingDownloadResumer resumer;
    private final DownloadDirAccessPort downloadDirAccess;

    public ResolveLaunchUpdateAction(@NonNull UpdateCheckProvider checkProvider,
                                     @NonNull VersionProvider versionProvider,
                                     @NonNull UpdateMetaStore metaStore,
                                     @NonNull ApkStoragePort apkStorage,
                                     @NonNull UpdatePolicy policy,
                                     @NonNull NetworkPolicy networkPolicy,
                                     @NonNull PromptProvider promptProvider,
                                     @NonNull PendingDownloadResumer resumer,
                                     @NonNull DownloadDirAccessPort downloadDirAccess) {
        this.checkProvider = checkProvider;
        this.versionProvider = versionProvider;
        this.metaStore = metaStore;
        this.apkStorage = apkStorage;
        this.policy = policy;
        this.networkPolicy = networkPolicy;
        this.promptProvider = promptProvider;
        this.resumer = resumer;
        this.downloadDirAccess = downloadDirAccess;
    }

    public void run(@NonNull Callback callback) {
        if (!downloadDirAccess.isAccessible()) {
            callback.onComplete(LaunchDecision.needDownloadDir());
            return;
        }

        CurrentAppVersion current = versionProvider.currentVersion();
        String expectedPackage = apkStorage.currentPackageName();
        String expectedSignature = apkStorage.currentSignatureHash();
        resumer.resume(apkStorage.rootDir(), policy.apkFilePrefix());

        List<LocalApk> candidates = apkStorage.listApks();
        RetentionPlan plan = ApkRetentionDecider.decide(
                candidates, current, expectedPackage, expectedSignature, policy.storagePolicy());
        for (LocalApk remove : plan.remove()) {
            apkStorage.deleteApk(remove);
        }
        List<LocalApk> kept = plan.keep();
        apkStorage.deleteOwnedFilesExcept(toFiles(kept));

        if (policy.wifiOnly() && !networkPolicy.isWifiConnected()) {
            completeWithMeta(current, kept, expectedSignature, callback);
            return;
        }

        checkProvider.check(versionProvider.currentVersionName(),
                new UpdateCheckProvider.Callback() {
                    @Override
                    public void onSuccess(@NonNull RemoteUpdate update) {
                        RemoteUpdate effective =
                                DownloadUrlResolver.resolve(update, policy.downloadUrl());
                        if (effective.hasNewVersion()
                                && !DownloadUrlResolver.hasUsableUrl(effective)) {
                            completeWithMeta(current, kept, expectedSignature, callback);
                            return;
                        }
                        if (effective.hasNewVersion()) {
                            metaStore.record(effective);
                        } else {
                            metaStore.clear();
                        }
                        LaunchDecision decision;
                        if (effective.hasNewVersion()
                                && !effective.forceUpdate()
                                && promptProvider.isIgnored(effective.version())) {
                            decision = LaunchDecision.none();
                        } else {
                            decision = LaunchUpdateDecisionResolver.resolve(
                                    effective,
                                    metaStore.snapshot(),
                                    kept,
                                    policy.autoInstallOnLaunch(),
                                    expectedSignature,
                                    policy.storagePolicy());
                            if (decision.action() == LaunchDecision.Action.SHOW_UPDATE_DIALOG
                                    && !effective.forceUpdate()
                                    && !promptProvider.shouldPrompt(effective.version())) {
                                decision = LaunchDecision.none();
                            }
                        }
                        callback.onComplete(decision);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        completeWithMeta(current, kept, expectedSignature, callback);
                    }
                });
    }

    private void completeWithMeta(@NonNull CurrentAppVersion current,
                                  @NonNull List<LocalApk> kept,
                                  @NonNull String expectedSignature,
                                  @NonNull Callback callback) {
        UpdateMeta meta = metaStore.snapshot();
        LaunchDecision decision;
        if (meta != null && meta.hasNewVersion()
                && (!promptProvider.isIgnored(meta.version()) || meta.forceUpdate())) {
            RemoteUpdate fallback =
                    DownloadUrlResolver.resolve(meta.toRemoteUpdate(), policy.downloadUrl());
            decision = LaunchUpdateDecisionResolver.resolve(
                    fallback,
                    meta,
                    kept,
                    policy.autoInstallOnLaunch(),
                    expectedSignature,
                    policy.storagePolicy());
            if (decision.action() == LaunchDecision.Action.SHOW_UPDATE_DIALOG
                    && !fallback.forceUpdate()
                    && !promptProvider.shouldPrompt(fallback.version())) {
                decision = LaunchDecision.none();
            }
        } else {
            RemoteUpdate noRemote = new RemoteUpdate(
                    false, current.versionName(), current.versionCode(), "", "", false);
            decision = LaunchUpdateDecisionResolver.resolve(
                    noRemote,
                    null,
                    kept,
                    policy.autoInstallOnLaunch(),
                    expectedSignature,
                    policy.storagePolicy());
        }
        callback.onComplete(decision);
    }

    @NonNull
    private static List<File> toFiles(@NonNull List<LocalApk> apks) {
        List<File> files = new ArrayList<>();
        for (LocalApk apk : apks) {
            files.add(apk.file());
        }
        return files;
    }
}
