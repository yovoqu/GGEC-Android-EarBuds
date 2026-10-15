package com.yovo.biz_appupdate.internal.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * SDK 内部下载完成后的 APK 记录，用于启动时精确匹配本地安装包。
 */
public final class DownloadedApk {

    @NonNull
    private final String path;
    @Nullable
    private final String version;
    private final int versionCode;
    private final long downloadedAtMillis;

    public DownloadedApk(@NonNull String path,
                         @Nullable String version,
                         int versionCode,
                         long downloadedAtMillis) {
        this.path = path;
        this.version = version;
        this.versionCode = versionCode;
        this.downloadedAtMillis = downloadedAtMillis;
    }

    @NonNull
    public String path() {
        return path;
    }

    @Nullable
    public String version() {
        return version;
    }

    public int versionCode() {
        return versionCode;
    }

    public long downloadedAtMillis() {
        return downloadedAtMillis;
    }
}
