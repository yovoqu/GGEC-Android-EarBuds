package com.yovo.lib_common.system.permission;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;

import com.yovo.lib_common.system.permission.internal.PermissionChecker;
import com.yovo.lib_common.system.permission.internal.PermissionRationale;
import com.yovo.lib_common.system.permission.internal.PermissionRequester;
import com.yovo.lib_common.system.permission.internal.core.PermissionCore;
import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionIgnoreReason;
import com.yovo.lib_common.system.permission.model.PermissionResult;
import com.yovo.lib_common.system.permission.model.PermissionStatus;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * pending 表按 Activity + requestCode 隔离。
 *
 * <p>权限态由 {@link PermissionCore#create} 构造注入的 checker 桩决定，
 * 不依赖 {@code ShadowApplication#denyPermissions}（该类签名引用
 * {@code FingerprintManager}，compileSdk 37 的 android.jar 已移除该类）。</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PermissionCorePendingTest {

    @Test
    public void pendingRequests_areKeyedByActivityAndRequestCode() {
        ComponentActivity first = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        ComponentActivity second = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        RecordingRequester requester = new RecordingRequester();
        PermissionCore core = deniedCore(requester);

        assertFalse(core.requestIfNeeded(
                first, Permission.ACCESS_FINE_LOCATION, 100));
        assertFalse(core.requestIfNeeded(
                second, Permission.WRITE_EXTERNAL_STORAGE, 100));
        assertEquals("两个 Activity 各发起一次运行时请求",
                2, requester.runtimeRequests);

        PermissionResult firstResult = core.handleRequestPermissionsResult(
                first, 100,
                new String[]{Permission.ACCESS_FINE_LOCATION.androidPermission()},
                new int[]{PackageManager.PERMISSION_GRANTED});
        assertTrue(firstResult.handled());
        assertTrue(firstResult.contains(Permission.ACCESS_FINE_LOCATION));

        PermissionResult secondResult = core.handleRequestPermissionsResult(
                second, 100,
                new String[]{Permission.WRITE_EXTERNAL_STORAGE.androidPermission()},
                new int[]{PackageManager.PERMISSION_GRANTED});
        assertTrue(secondResult.handled());
        assertTrue(secondResult.contains(Permission.WRITE_EXTERNAL_STORAGE));
    }

    @Test
    public void noActivityOverload_ambiguousRequestCode_returnsIgnored() {
        ComponentActivity first = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        ComponentActivity second = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        PermissionCore core = deniedCore(new RecordingRequester());

        assertFalse(core.requestIfNeeded(
                first, Permission.ACCESS_FINE_LOCATION, 200));
        assertFalse(core.requestIfNeeded(
                second, Permission.WRITE_EXTERNAL_STORAGE, 200));

        PermissionResult result = core.handleRequestPermissionsResult(
                null, 200,
                new String[]{Permission.ACCESS_FINE_LOCATION.androidPermission()},
                new int[]{PackageManager.PERMISSION_GRANTED});
        assertFalse(result.handled());
        assertEquals(PermissionIgnoreReason.AMBIGUOUS_ACTIVITY, result.ignoreReason());
    }

    @Test
    public void unknownActivity_isClassifiedNotSilentlyNull() {
        ComponentActivity requester = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        ComponentActivity stranger = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        PermissionCore core = deniedCore(new RecordingRequester());
        assertFalse(core.requestIfNeeded(requester, Permission.ACCESS_FINE_LOCATION, 300));

        PermissionResult result = core.handleRequestPermissionsResult(
                stranger, 300,
                new String[]{Permission.ACCESS_FINE_LOCATION.androidPermission()},
                new int[]{PackageManager.PERMISSION_GRANTED});

        assertFalse(result.handled());
        assertEquals(PermissionIgnoreReason.UNKNOWN_ACTIVITY, result.ignoreReason());
    }

    @Test
    public void unknownRequestCode_isClassifiedNotSilentlyNull() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        PermissionCore core = deniedCore(new RecordingRequester());
        assertFalse(core.requestIfNeeded(activity, Permission.ACCESS_FINE_LOCATION, 400));

        PermissionResult result = core.handleRequestPermissionsResult(
                activity, 401,
                new String[]{Permission.ACCESS_FINE_LOCATION.androidPermission()},
                new int[]{PackageManager.PERMISSION_GRANTED});

        assertFalse(result.handled());
        assertEquals(PermissionIgnoreReason.UNKNOWN_REQUEST_CODE, result.ignoreReason());
    }

    @Test
    public void noPendingRequest_isClassifiedAsNoPendingRequest() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        PermissionCore core = deniedCore(new RecordingRequester());

        PermissionResult result = core.handleRequestPermissionsResult(
                activity, 500,
                new String[]{Permission.ACCESS_FINE_LOCATION.androidPermission()},
                new int[]{PackageManager.PERMISSION_GRANTED});

        assertFalse(result.handled());
        assertEquals(PermissionIgnoreReason.NO_PENDING_REQUEST, result.ignoreReason());
    }

    /** 全部权限判为拒绝的独立实例（构造注入 checker 桩）。 */
    private static PermissionCore deniedCore(@NonNull RecordingRequester requester) {
        return PermissionCore.create(
                new PermissionChecker() {
                    @NonNull
                    @Override
                    public PermissionStatus check(@NonNull Context context,
                                                  @NonNull Permission permission) {
                        return PermissionStatus.DENIED;
                    }
                },
                requester,
                new PermissionRationale() {
                    @Override
                    public boolean shouldShow(@NonNull Activity activity,
                                              @NonNull Permission permission) {
                        return false;
                    }
                });
    }

    private static final class RecordingRequester implements PermissionRequester {

        private int runtimeRequests;

        @Override
        public void requestRuntime(@NonNull Activity activity,
                                   @NonNull List<Permission> permissions,
                                   int requestCode) {
            runtimeRequests++;
        }

        @Override
        public boolean requestSpecialAccess(@NonNull Activity activity,
                                            @NonNull Permission permission) {
            return true;
        }

        @Override
        public void openAppSettings(@NonNull Activity activity) {
        }
    }
}
