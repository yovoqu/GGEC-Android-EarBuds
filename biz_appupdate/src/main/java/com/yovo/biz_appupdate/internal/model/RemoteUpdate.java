package com.yovo.biz_appupdate.internal.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 远程 APP 更新信息。
 */
public final class RemoteUpdate {

    private final boolean hasNewVersion;
    private final String version;
    private final int versionCode;
    private final String description;
    private final String downloadUrl;
    private final boolean forceUpdate;
    private final String md5;
    private final String sha256;
    private final long sizeBytes;

    public RemoteUpdate(boolean hasNewVersion,
                        @NonNull String version,
                        int versionCode,
                        @NonNull String description,
                        @NonNull String downloadUrl,
                        boolean forceUpdate) {
        this(hasNewVersion, version, versionCode, description, downloadUrl,
                forceUpdate, "", "", 0L);
    }

    public RemoteUpdate(boolean hasNewVersion,
                        @NonNull String version,
                        int versionCode,
                        @NonNull String description,
                        @NonNull String downloadUrl,
                        boolean forceUpdate,
                        @NonNull String md5,
                        @NonNull String sha256,
                        long sizeBytes) {
        this.hasNewVersion = hasNewVersion;
        this.version = version;
        this.versionCode = versionCode;
        this.description = description;
        this.downloadUrl = downloadUrl;
        this.forceUpdate = forceUpdate;
        this.md5 = md5;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
    }

    /**
     * 兼容旧构造：不携带 versionCode / 强制更新标记。
     */
    public RemoteUpdate(boolean hasNewVersion,
                        @NonNull String version,
                        @NonNull String description,
                        @NonNull String downloadUrl) {
        this(hasNewVersion, version, 0, description, downloadUrl, false);
    }

    public boolean hasNewVersion() {
        return hasNewVersion;
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

    /** APK MD5（服务端未提供时为空串，跳过校验）。 */
    @NonNull
    public String md5() {
        return md5;
    }

    /** APK SHA-256（服务端未提供时为空串，跳过校验）。 */
    @NonNull
    public String sha256() {
        return sha256;
    }

    /** APK 字节大小（服务端未提供时为 0，跳过校验）。 */
    public long sizeBytes() {
        return sizeBytes;
    }

    @NonNull
    public RemoteUpdate withDownloadUrl(@NonNull String resolvedUrl) {
        return new RemoteUpdate(
                hasNewVersion,
                version,
                versionCode,
                description,
                resolvedUrl,
                forceUpdate,
                md5,
                sha256,
                sizeBytes);
    }

    @NonNull
    public RemoteUpdate withUrlFallback(@Nullable String fallbackUrl) {
        if (downloadUrl != null && !downloadUrl.trim().isEmpty()) {
            return this;
        }
        return withDownloadUrl(fallbackUrl == null ? "" : fallbackUrl);
    }
}
