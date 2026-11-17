package com.yovo.biz_appupdate.internal.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 最近一次远程更新检查的元信息（含首次发现时间），用于与本地 APK 修改时间对比。
 *
 * <p>本类型只记录「已经发现的更新」：是否存在更新由持有方（{@code meta != null}）表达，
 * 不再提供恒真的判定方法。</p>
 */
public final class UpdateMeta {

    @NonNull
    private final String version;
    private final int versionCode;
    @NonNull
    private final String description;
    @NonNull
    private final String downloadUrl;
    private final boolean forceUpdate;
    private final String md5;
    private final String sha256;
    private final long sizeBytes;
    private final long discoveredAtMillis;
    @Nullable
    private final DownloadedApk downloadedApk;

    public UpdateMeta(@NonNull String version,
                      int versionCode,
                      @NonNull String description,
                      @NonNull String downloadUrl,
                      boolean forceUpdate,
                      @NonNull String md5,
                      @NonNull String sha256,
                      long sizeBytes,
                      long discoveredAtMillis,
                      @Nullable DownloadedApk downloadedApk) {
        this.version = version;
        this.versionCode = versionCode;
        this.description = description;
        this.downloadUrl = downloadUrl;
        this.forceUpdate = forceUpdate;
        this.md5 = md5;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.discoveredAtMillis = discoveredAtMillis;
        this.downloadedApk = downloadedApk;
    }

    @NonNull
    public String version() {
        return version;
    }

    public int versionCode() {
        return versionCode;
    }

    @NonNull
    public String description() {
        return description;
    }

    @NonNull
    public String downloadUrl() {
        return downloadUrl;
    }

    public boolean forceUpdate() {
        return forceUpdate;
    }

    @NonNull
    public String md5() {
        return md5;
    }

    @NonNull
    public String sha256() {
        return sha256;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public long discoveredAtMillis() {
        return discoveredAtMillis;
    }

    @Nullable
    public DownloadedApk downloadedApk() {
        return downloadedApk;
    }

    @NonNull
    public RemoteUpdate toRemoteUpdate() {
        return new RemoteUpdate(
                true, version, versionCode, description, downloadUrl,
                forceUpdate, md5, sha256, sizeBytes);
    }
}
