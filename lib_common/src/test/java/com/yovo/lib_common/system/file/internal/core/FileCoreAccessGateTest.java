package com.yovo.lib_common.system.file.internal.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.Manifest;
import android.app.Application;

import com.yovo.lib_common.system.file.model.AccessDenyReason;
import com.yovo.lib_common.system.file.model.FileFailureCode;
import com.yovo.lib_common.system.file.model.FileResult;
import com.yovo.lib_common.system.file.model.FileTarget;
import com.yovo.lib_common.system.file.model.PathTarget;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowApplication;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public final class FileCoreAccessGateTest {

    @Test
    public void absoluteRead_deniedWithoutStoragePermission() {
        Application app = RuntimeEnvironment.getApplication();
        // 用 Shadow.extract 而不是 Shadows.shadowOf：Shadows 类带有 FingerprintManager 重载，
        // compileSdk 37 的 android.jar 已移除该类，编译本测试时会解析失败。语义完全等价。
        ShadowApplication shadowApp = Shadow.extract(app);
        shadowApp.denyPermissions(
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        FileResult<byte[]> result = FileCore.readBytes(
                app,
                FileTarget.path(PathTarget.absolute("/sdcard/x.bin")),
                1024);

        assertFalse(result.success());
        assertEquals(FileFailureCode.DENIED, result.requireFailure().code());
        assertEquals(
                AccessDenyReason.MISSING_STORAGE_DIRECT,
                result.requireFailure().access().reason());
    }
}
