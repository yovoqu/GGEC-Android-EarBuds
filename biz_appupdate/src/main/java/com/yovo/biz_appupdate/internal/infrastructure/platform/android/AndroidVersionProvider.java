package com.yovo.biz_appupdate.internal.infrastructure.platform.android;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.contract.VersionProvider;

public final class AndroidVersionProvider implements VersionProvider {

    private final Context appContext;

    public AndroidVersionProvider(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public String currentVersionName() {
        PackageInfo info = packageInfo();
        return info != null && info.versionName != null ? info.versionName : "";
    }

    @Override
    public int currentVersionCode() {
        PackageInfo info = packageInfo();
        if (info == null) {
            return 0;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? (int) info.getLongVersionCode()
                : info.versionCode;
    }

    @androidx.annotation.Nullable
    private PackageInfo packageInfo() {
        try {
            return appContext.getPackageManager().getPackageInfo(
                    appContext.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
