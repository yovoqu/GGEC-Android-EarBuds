package com.yovo.biz_appupdate.internal.interactor;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.contract.ApkVerifier;
import com.yovo.biz_appupdate.internal.contract.InstallProvider;
import com.yovo.biz_appupdate.internal.contract.InstallOutcome;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;

import java.io.File;

public final class InstallUpdateAction {

    private final InstallProvider installProvider;
    private final ApkVerifier verifier;

    public InstallUpdateAction(@NonNull InstallProvider installProvider,
                               @NonNull ApkVerifier verifier) {
        this.installProvider = installProvider;
        this.verifier = verifier;
    }

    @NonNull
    public InstallOutcome run(@NonNull Context context,
                              @NonNull File apkFile,
                              @Nullable RemoteUpdate update) {
        if (verifier.verify(context, apkFile, update) != ApkVerifier.Result.OK) {
            return InstallOutcome.VERIFY_FAILED;
        }
        InstallProvider.Result result = installProvider.install(context, apkFile);
        switch (result) {
            case SUCCESS:
                return InstallOutcome.SUCCESS;
            case INVALID_FILE:
                return InstallOutcome.INVALID_FILE;
            case NO_INSTALLER:
            default:
                return InstallOutcome.NO_INSTALLER;
        }
    }
}
