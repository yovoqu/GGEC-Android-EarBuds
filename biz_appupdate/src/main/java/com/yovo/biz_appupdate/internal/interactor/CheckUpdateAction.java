package com.yovo.biz_appupdate.internal.interactor;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.contract.NetworkPolicy;
import com.yovo.biz_appupdate.internal.contract.UpdateCheckProvider;
import com.yovo.biz_appupdate.internal.contract.UpdateMetaStore;
import com.yovo.biz_appupdate.internal.contract.VersionProvider;
import com.yovo.biz_appupdate.internal.contract.UpdatePolicy;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;

public final class CheckUpdateAction {

    static final String ERROR_NO_DOWNLOAD_URL = "未获取到有效下载地址";

    private final UpdateCheckProvider checkProvider;
    private final VersionProvider versionProvider;
    private final UpdatePolicy policy;
    private final NetworkPolicy networkPolicy;
    private final UpdateMetaStore metaStore;

    public CheckUpdateAction(@NonNull UpdateCheckProvider checkProvider,
                             @NonNull VersionProvider versionProvider,
                             @NonNull UpdatePolicy policy,
                             @NonNull NetworkPolicy networkPolicy,
                             @NonNull UpdateMetaStore metaStore) {
        this.checkProvider = checkProvider;
        this.versionProvider = versionProvider;
        this.policy = policy;
        this.networkPolicy = networkPolicy;
        this.metaStore = metaStore;
    }

    public void run(@NonNull UpdateCheckProvider.Callback callback) {
        if (policy.wifiOnly() && !networkPolicy.isWifiConnected()) {
            callback.onError("当前非 WiFi 网络，已跳过更新检查");
            return;
        }
        checkProvider.check(versionProvider.currentVersionName(),
                new UpdateCheckProvider.Callback() {
                    @Override
                    public void onSuccess(@NonNull RemoteUpdate update) {
                        RemoteUpdate resolved =
                                DownloadUrlResolver.resolve(update, policy.downloadUrl());
                        if (!resolved.hasNewVersion()) {
                            metaStore.clear();
                            callback.onSuccess(resolved);
                            return;
                        }
                        if (!DownloadUrlResolver.hasUsableUrl(resolved)) {
                            callback.onError(ERROR_NO_DOWNLOAD_URL);
                            return;
                        }
                        metaStore.record(resolved);
                        callback.onSuccess(resolved);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        callback.onError(message);
                    }
                });
    }
}
