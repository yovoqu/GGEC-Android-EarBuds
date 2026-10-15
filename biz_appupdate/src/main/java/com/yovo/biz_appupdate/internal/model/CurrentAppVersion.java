package com.yovo.biz_appupdate.internal.model;

import androidx.annotation.NonNull;

/**
 * 当前已安装应用版本信息。
 */
public final class CurrentAppVersion {

    @NonNull
    private final String versionName;
    private final int versionCode;

    public CurrentAppVersion(@NonNull String versionName, int versionCode) {
        this.versionName = versionName;
        this.versionCode = versionCode;
    }

    @NonNull
    public String versionName() {
        return versionName;
    }

    public int versionCode() {
        return versionCode;
    }
}
