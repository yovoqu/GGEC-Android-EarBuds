package com.yovo.lib_common.system.permission;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.pm.PackageManager;

import androidx.activity.ComponentActivity;

import com.yovo.lib_common.system.permission.internal.core.PermissionCore;
import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PermissionCorePendingTest {

    @Test
    public void pendingRequests_areKeyedByActivityAndRequestCode() {
        Application app = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app).denyPermissions(
                Permission.ACCESS_FINE_LOCATION.androidPermission(),
                Permission.WRITE_EXTERNAL_STORAGE.androidPermission());

        ComponentActivity first = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        ComponentActivity second = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        PermissionCore core = PermissionCore.with(app);

        assertFalse(core.requestIfNeeded(
                first, Permission.ACCESS_FINE_LOCATION, 100));
        assertFalse(core.requestIfNeeded(
                second, Permission.WRITE_EXTERNAL_STORAGE, 100));

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
        Application app = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app).denyPermissions(
                Permission.ACCESS_FINE_LOCATION.androidPermission(),
                Permission.WRITE_EXTERNAL_STORAGE.androidPermission());

        ComponentActivity first = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        ComponentActivity second = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        PermissionCore core = PermissionCore.with(app);

        assertFalse(core.requestIfNeeded(
                first, Permission.ACCESS_FINE_LOCATION, 200));
        assertFalse(core.requestIfNeeded(
                second, Permission.WRITE_EXTERNAL_STORAGE, 200));

        PermissionResult result = core.handleRequestPermissionsResult(
                null, 200,
                new String[]{Permission.ACCESS_FINE_LOCATION.androidPermission()},
                new int[]{PackageManager.PERMISSION_GRANTED});
        assertFalse(result.handled());
    }
}
