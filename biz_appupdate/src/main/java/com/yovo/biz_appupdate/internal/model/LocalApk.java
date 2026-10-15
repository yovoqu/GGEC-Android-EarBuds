package com.yovo.biz_appupdate.internal.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * SDK 下载目录中扫描到的本地 APK。
 */
public final class LocalApk {

    @NonNull
    private final File file;
    private final long lastModified;
    @Nullable
    private final String versionName;
    private final int versionCode;
    @Nullable
    private final String signatureHash;
    @Nullable
    private final String packageName;

    public LocalApk(@NonNull File file, long lastModified, @Nullable String versionName) {
        this(file, lastModified, versionName, 0, null, null);
    }

    public LocalApk(@NonNull File file,
                    long lastModified,
                    @Nullable String versionName,
                    int versionCode,
                    @Nullable String signatureHash) {
        this(file, lastModified, versionName, versionCode, signatureHash, null);
    }

    public LocalApk(@NonNull File file,
                    long lastModified,
                    @Nullable String versionName,
                    int versionCode,
                    @Nullable String signatureHash,
                    @Nullable String packageName) {
        this.file = file;
        this.lastModified = lastModified;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.signatureHash = signatureHash;
        this.packageName = packageName;
    }

    @NonNull
    public File file() {
        return file;
    }

    public long lastModified() {
        return lastModified;
    }

    @Nullable
    public String versionName() {
        return versionName;
    }

    /** APK 真实 versionCode（未解析到时为 0）。 */
    public int versionCode() {
        return versionCode;
    }

    /** APK 签名 SHA-256（未解析到时为 null）。 */
    @Nullable
    public String signatureHash() {
        return signatureHash;
    }

    @Nullable
    public String packageName() {
        return packageName;
    }
}
