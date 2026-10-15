package com.yovo.biz_appupdate.internal.interactor;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.contract.ApkStoragePort;
import com.yovo.biz_appupdate.internal.contract.DownloadProvider;
import com.yovo.biz_appupdate.internal.contract.NetworkPolicy;
import com.yovo.biz_appupdate.internal.contract.UpdateMetaStore;
import com.yovo.biz_appupdate.internal.contract.UpdatePolicy;
import com.yovo.biz_appupdate.internal.model.DownloadMode;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;
import com.yovo.biz_appupdate.internal.model.StoragePolicy;

import java.io.File;
import java.util.Collections;

public final class DownloadUpdateAction {

    private final DownloadProvider downloadProvider;
    private final UpdateMetaStore metaStore;
    private final NetworkPolicy networkPolicy;
    private final UpdatePolicy policy;
    private final ApkStoragePort apkStorage;

    public DownloadUpdateAction(@NonNull DownloadProvider downloadProvider,
                                @NonNull UpdateMetaStore metaStore,
                                @NonNull NetworkPolicy networkPolicy,
                                @NonNull UpdatePolicy policy,
                                @NonNull ApkStoragePort apkStorage) {
        this.downloadProvider = downloadProvider;
        this.metaStore = metaStore;
        this.networkPolicy = networkPolicy;
        this.policy = policy;
        this.apkStorage = apkStorage;
    }

    public void run(@NonNull DownloadMode mode,
                    @NonNull RemoteUpdate update,
                    @NonNull DownloadProvider.Callback callback) {
        if (policy.wifiOnly() && !networkPolicy.isWifiConnected()) {
            callback.onStartFailed(mode, DownloadProvider.StartFailure.WIFI_REQUIRED);
            return;
        }
        RemoteUpdate resolved = DownloadUrlResolver.resolve(update, policy.downloadUrl());
        if (!DownloadUrlResolver.hasUsableUrl(resolved)) {
            callback.onStartFailed(mode, DownloadProvider.StartFailure.INVALID_URL);
            return;
        }
        downloadProvider.download(mode, resolved, new DownloadProvider.Callback() {
            @Override
            public void onStarted(@NonNull DownloadMode mode, @NonNull String taskId) {
                callback.onStarted(mode, taskId);
            }

            @Override
            public void onStartFailed(@NonNull DownloadMode mode,
                                      @NonNull DownloadProvider.StartFailure failure) {
                callback.onStartFailed(mode, failure);
            }

            @Override
            public void onProgress(@NonNull String taskId, int percent) {
                callback.onProgress(taskId, percent);
            }

            @Override
            public void onSuccess(@NonNull String taskId, File file) {
                if (mode == DownloadMode.INTERNAL && file != null) {
                    metaStore.recordDownloadedApk(resolved, file);
                    if (policy.storagePolicy() == StoragePolicy.SINGLE_LATEST) {
                        apkStorage.deleteOwnedFilesExcept(Collections.singletonList(file));
                    }
                }
                callback.onSuccess(taskId, file);
            }

            @Override
            public void onError(@NonNull String taskId, @NonNull String message) {
                callback.onError(taskId, message);
            }
        });
    }
}
