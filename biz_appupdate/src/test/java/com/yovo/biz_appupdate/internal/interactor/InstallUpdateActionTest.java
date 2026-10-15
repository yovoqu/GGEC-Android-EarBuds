package com.yovo.biz_appupdate.internal.interactor;

import static org.junit.Assert.assertEquals;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.contract.ApkVerifier;
import com.yovo.biz_appupdate.internal.contract.InstallOutcome;
import com.yovo.biz_appupdate.internal.contract.InstallProvider;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;

import org.junit.Test;

import java.io.File;

public final class InstallUpdateActionTest {

    @Test
    public void verifyFailed_mapsToVerifyFailed() {
        InstallUpdateAction action = new InstallUpdateAction(
                result(InstallProvider.Result.SUCCESS),
                verify(ApkVerifier.Result.VERSION_MISMATCH));

        assertEquals(InstallOutcome.VERIFY_FAILED,
                action.run(null, new File("/tmp/a.apk"), null));
    }

    @Test
    public void invalidFile_mapsToInvalidFile() {
        InstallUpdateAction action = new InstallUpdateAction(
                result(InstallProvider.Result.INVALID_FILE),
                verify(ApkVerifier.Result.OK));

        assertEquals(InstallOutcome.INVALID_FILE,
                action.run(null, new File("/tmp/a.apk"), null));
    }

    @Test
    public void noInstaller_mapsToNoInstaller() {
        InstallUpdateAction action = new InstallUpdateAction(
                result(InstallProvider.Result.NO_INSTALLER),
                verify(ApkVerifier.Result.OK));

        assertEquals(InstallOutcome.NO_INSTALLER,
                action.run(null, new File("/tmp/a.apk"), null));
    }

    @Test
    public void success_mapsToSuccess() {
        InstallUpdateAction action = new InstallUpdateAction(
                result(InstallProvider.Result.SUCCESS),
                verify(ApkVerifier.Result.OK));

        assertEquals(InstallOutcome.SUCCESS,
                action.run(null, new File("/tmp/a.apk"), null));
    }

    private static InstallProvider result(InstallProvider.Result result) {
        return new InstallProvider() {
            @NonNull
            @Override
            public Result install(@NonNull android.content.Context context,
                                  @NonNull File apkFile) {
                return result;
            }
        };
    }

    private static ApkVerifier verify(ApkVerifier.Result result) {
        return new ApkVerifier() {
            @NonNull
            @Override
            public Result verify(@NonNull android.content.Context context,
                                 @NonNull File apkFile,
                                 @Nullable RemoteUpdate update) {
                return result;
            }
        };
    }
}
