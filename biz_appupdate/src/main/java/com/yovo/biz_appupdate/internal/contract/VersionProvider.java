package com.yovo.biz_appupdate.internal.contract;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.model.CurrentAppVersion;

public interface VersionProvider {

    @NonNull
    String currentVersionName();

    int currentVersionCode();

    @NonNull
    default CurrentAppVersion currentVersion() {
        return new CurrentAppVersion(currentVersionName(), currentVersionCode());
    }
}
