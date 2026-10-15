package com.yovo.biz_appupdate.internal.contract;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.model.ApkNamingSpec;
import com.yovo.biz_appupdate.internal.model.StoragePolicy;

/**
 * 更新策略（纯值对象）。
 */
public final class UpdatePolicy {

    @NonNull
    private final String downloadUrl;
    private final boolean wifiOnly;
    private final boolean autoInstallOnLaunch;
    @NonNull
    private final ApkNamingSpec apkNamingSpec;
    private final long splashMinDelayMs;
    private final long splashCheckTimeoutMs;
    @NonNull
    private final StoragePolicy storagePolicy;

    public UpdatePolicy(@NonNull String downloadUrl,
                        boolean wifiOnly,
                        boolean autoInstallOnLaunch,
                        @NonNull String apkFilePrefix,
                        long splashMinDelayMs,
                        long splashCheckTimeoutMs) {
        this(downloadUrl, wifiOnly, autoInstallOnLaunch,
                new ApkNamingSpec(apkFilePrefix), splashMinDelayMs, splashCheckTimeoutMs,
                StoragePolicy.SINGLE_LATEST);
    }

    public UpdatePolicy(@NonNull String downloadUrl,
                        boolean wifiOnly,
                        boolean autoInstallOnLaunch,
                        @NonNull ApkNamingSpec apkNamingSpec,
                        long splashMinDelayMs,
                        long splashCheckTimeoutMs,
                        @NonNull StoragePolicy storagePolicy) {
        this.downloadUrl = downloadUrl;
        this.wifiOnly = wifiOnly;
        this.autoInstallOnLaunch = autoInstallOnLaunch;
        this.apkNamingSpec = apkNamingSpec;
        this.splashMinDelayMs = splashMinDelayMs;
        this.splashCheckTimeoutMs = splashCheckTimeoutMs;
        this.storagePolicy = storagePolicy;
    }

    @NonNull
    public String downloadUrl() {
        return downloadUrl;
    }

    public boolean wifiOnly() {
        return wifiOnly;
    }

    public boolean autoInstallOnLaunch() {
        return autoInstallOnLaunch;
    }

    @NonNull
    public String apkFilePrefix() {
        return apkNamingSpec.prefix();
    }

    @NonNull
    public ApkNamingSpec apkNamingSpec() {
        return apkNamingSpec;
    }

    public long splashMinDelayMs() {
        return splashMinDelayMs;
    }

    public long splashCheckTimeoutMs() {
        return splashCheckTimeoutMs;
    }

    @NonNull
    public StoragePolicy storagePolicy() {
        return storagePolicy;
    }
}
