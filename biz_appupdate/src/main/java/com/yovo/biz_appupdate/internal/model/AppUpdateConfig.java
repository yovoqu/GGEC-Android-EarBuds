package com.yovo.biz_appupdate.internal.model;

import androidx.annotation.NonNull;

/**
 * APP 更新模块内部配置。
 */
public final class AppUpdateConfig {

    private final String fileProviderAuthority;
    private final ApkNamingSpec apkNamingSpec;
    private final String downloadUrl;
    private final DownloadDirSpec downloadDir;
    private final boolean autoInstallOnLaunch;
    private final boolean wifiOnly;
    private final long splashMinDelayMs;
    private final long splashCheckTimeoutMs;
    private final StoragePolicy storagePolicy;

    private AppUpdateConfig(@NonNull Builder builder) {
        fileProviderAuthority = builder.fileProviderAuthority;
        apkNamingSpec = new ApkNamingSpec(builder.apkFilePrefix);
        downloadUrl = builder.downloadUrl;
        downloadDir = builder.downloadDir;
        autoInstallOnLaunch = builder.autoInstallOnLaunch;
        wifiOnly = builder.wifiOnly;
        splashMinDelayMs = builder.splashMinDelayMs;
        splashCheckTimeoutMs = builder.splashCheckTimeoutMs;
        storagePolicy = builder.storagePolicy;
    }

    @NonNull
    public String fileProviderAuthority() {
        return fileProviderAuthority;
    }

    @NonNull
    public String apkFilePrefix() {
        return apkNamingSpec.prefix();
    }

    @NonNull
    public ApkNamingSpec apkNamingSpec() {
        return apkNamingSpec;
    }

    @NonNull
    public String downloadUrl() {
        return downloadUrl;
    }

    @NonNull
    public DownloadDirSpec downloadDir() {
        return downloadDir;
    }

    public boolean autoInstallOnLaunch() {
        return autoInstallOnLaunch;
    }

    public boolean wifiOnly() {
        return wifiOnly;
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

    public static final class Builder {

        private String fileProviderAuthority = "";
        private String apkFilePrefix = ApkNamingSpec.DEFAULT_PREFIX;
        private String downloadUrl = "";
        private DownloadDirSpec downloadDir = DownloadDirSpec.appPrivate("AppUpdate");
        private boolean autoInstallOnLaunch = true;
        private boolean wifiOnly = false;
        private long splashMinDelayMs = 1000L;
        private long splashCheckTimeoutMs = 5000L;
        private StoragePolicy storagePolicy = StoragePolicy.SINGLE_LATEST;

        public Builder fileProviderAuthority(@NonNull String authority) {
            fileProviderAuthority = authority;
            return this;
        }

        public Builder apkFilePrefix(@NonNull String prefix) {
            apkFilePrefix = prefix;
            return this;
        }

        public Builder downloadUrl(@NonNull String url) {
            downloadUrl = url;
            return this;
        }

        public Builder downloadDir(@NonNull DownloadDirSpec dir) {
            downloadDir = dir;
            return this;
        }

        public Builder autoInstallOnLaunch(boolean enabled) {
            autoInstallOnLaunch = enabled;
            return this;
        }

        public Builder wifiOnly(boolean enabled) {
            wifiOnly = enabled;
            return this;
        }

        public Builder splashMinDelayMs(long millis) {
            splashMinDelayMs = millis;
            return this;
        }

        public Builder splashCheckTimeoutMs(long millis) {
            splashCheckTimeoutMs = millis;
            return this;
        }

        public Builder storagePolicy(@NonNull StoragePolicy policy) {
            storagePolicy = policy;
            return this;
        }

        @NonNull
        public AppUpdateConfig build() {
            if (fileProviderAuthority.isEmpty()) {
                throw new IllegalStateException("fileProviderAuthority is required");
            }
            if (apkFilePrefix.trim().isEmpty()) {
                throw new IllegalStateException("apkFilePrefix is required");
            }
            if (downloadUrl.trim().isEmpty()) {
                throw new IllegalStateException("downloadUrl is required");
            }
            return new AppUpdateConfig(this);
        }
    }
}
