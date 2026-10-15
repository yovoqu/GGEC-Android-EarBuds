package com.yovo.biz_appupdate.internal.infrastructure.platform.android;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.yovo.biz_appupdate.internal.contract.InstallProvider;

import java.io.File;

public final class AndroidInstallProvider implements InstallProvider {

    private static final String MIME = "application/vnd.android.package-archive";

    private final String fileProviderAuthority;

    public AndroidInstallProvider(@NonNull String fileProviderAuthority) {
        this.fileProviderAuthority = fileProviderAuthority;
    }

    @NonNull
    @Override
    public Result install(@NonNull Context context, @NonNull File apkFile) {
        if (!apkFile.isFile()) {
            return Result.INVALID_FILE;
        }
        try {
            Uri uri = FileProvider.getUriForFile(context, fileProviderAuthority, apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, MIME);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
            return Result.SUCCESS;
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            return Result.NO_INSTALLER;
        }
    }
}
