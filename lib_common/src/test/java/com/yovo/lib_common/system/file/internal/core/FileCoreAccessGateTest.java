package com.yovo.lib_common.system.file.internal.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;

import com.yovo.lib_common.system.file.model.AccessCheckResult;
import com.yovo.lib_common.system.file.model.AccessDenyReason;
import com.yovo.lib_common.system.file.model.AccessMode;
import com.yovo.lib_common.system.file.model.FileFailureCode;
import com.yovo.lib_common.system.file.model.FileResult;
import com.yovo.lib_common.system.file.model.FileTarget;
import com.yovo.lib_common.system.file.model.PathTarget;
import com.yovo.lib_common.system.permission.model.Permission;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * 绝对路径访问门禁：存储权限缺失必须拒绝，且拒绝原因可判定。
 *
 * <p>权限态通过 {@link ContextWrapper} 桩注入（{@code checkSelfPermission} 直接返回期望值），
 * 不依赖 {@code ShadowApplication#denyPermissions}：该 Shadow 的类型签名引用
 * {@code FingerprintManager}，compileSdk 37 的 android.jar 已移除该类，
 * 编译单元测试会解析失败。桩注入与 Shadow 的门禁语义一致，且可正反两向验证。</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public final class FileCoreAccessGateTest {

    @Test
    public void absoluteRead_deniedWithoutStoragePermission() {
        Application app = RuntimeEnvironment.getApplication();

        FileResult<byte[]> result = FileCore.readBytes(
                permissionContext(app, PackageManager.PERMISSION_DENIED),
                FileTarget.path(PathTarget.absolute("/sdcard/x.bin")),
                1024);

        assertFalse(result.success());
        assertEquals(FileFailureCode.DENIED, result.requireFailure().code());
        assertEquals(
                AccessDenyReason.MISSING_STORAGE_DIRECT,
                result.requireFailure().access().reason());
        assertTrue(
                "缺失权限需如实列出存储写权限",
                result.requireFailure().access().missingPermissions()
                        .contains(Permission.WRITE_EXTERNAL_STORAGE));
    }

    @Test
    public void absoluteRead_gateOpensWhenStoragePermissionGranted() {
        Application app = RuntimeEnvironment.getApplication();

        AccessCheckResult allowed = FileCore.checkAccess(
                permissionContext(app, PackageManager.PERMISSION_GRANTED),
                FileTarget.path(PathTarget.absolute("/sdcard/x.bin")),
                AccessMode.READ);

        assertTrue("同一目标在授权后必须放行", allowed.isAllowed());
    }

    /**
     * 权限桩：{@code getApplicationContext} 返回自身，保证门禁内部的
     * {@code context.getApplicationContext()} 仍落在桩上。
     */
    private static Context permissionContext(final Application app, final int result) {
        return new ContextWrapper(app) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public int checkSelfPermission(String permission) {
                return result;
            }

            @Override
            public int checkPermission(String permission, int pid, int uid) {
                return result;
            }
        };
    }
}
