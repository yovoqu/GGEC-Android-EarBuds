package com.yovo.biz_appupdate.internal.contract;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;

public interface InstallProvider {

    enum Result {
        SUCCESS,
        INVALID_FILE,
        NO_INSTALLER
    }

    @NonNull
    Result install(@NonNull Context context, @NonNull File apkFile);
}
