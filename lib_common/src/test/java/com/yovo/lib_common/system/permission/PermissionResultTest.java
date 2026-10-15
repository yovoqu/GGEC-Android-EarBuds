package com.yovo.lib_common.system.permission;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageManager;

import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionResult;
import com.yovo.lib_common.system.permission.model.PermissionStatus;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class PermissionResultTest {

    @Test
    public void mapsResultsInRequestOrder() {
        PermissionResult result = PermissionResult.of(
                Arrays.asList(Permission.BLUETOOTH_CONNECT, Permission.BLUETOOTH_SCAN),
                new int[]{PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_DENIED});

        assertTrue(result.handled());
        assertEquals(PermissionStatus.GRANTED,
                result.statusOf(Permission.BLUETOOTH_CONNECT));
        assertEquals(PermissionStatus.DENIED,
                result.statusOf(Permission.BLUETOOTH_SCAN));
        assertFalse(result.allGranted());
    }

    @Test
    public void allGranted_returnsTrueWhenEveryPermissionGranted() {
        PermissionResult result = PermissionResult.of(
                Arrays.asList(Permission.BLUETOOTH_CONNECT, Permission.BLUETOOTH_SCAN),
                new int[]{PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_GRANTED});

        assertTrue(result.allGranted());
        assertTrue(result.contains(Permission.BLUETOOTH_CONNECT));
        assertFalse(result.contains(Permission.POST_NOTIFICATIONS));
    }

    @Test
    public void ignored_notHandled() {
        PermissionResult result = PermissionResult.ignored();
        assertFalse(result.handled());
        assertFalse(result.allGranted());
    }

    @Test
    public void permanentlyDenied_markedForDeniedRuntimePermission() {
        PermissionResult result = PermissionResult.of(
                Arrays.asList(Permission.BLUETOOTH_CONNECT, Permission.BLUETOOTH_SCAN),
                new int[]{PackageManager.PERMISSION_DENIED, PackageManager.PERMISSION_GRANTED},
                Collections.singletonMap(Permission.BLUETOOTH_CONNECT, true));

        assertTrue(result.isPermanentlyDenied(Permission.BLUETOOTH_CONNECT));
        assertFalse(result.isPermanentlyDenied(Permission.BLUETOOTH_SCAN));
        assertTrue(result.hasPermanentlyDenied());
        assertEquals(PermissionStatus.DENIED,
                result.statusOf(Permission.BLUETOOTH_CONNECT));
        assertFalse(result.allGranted());
    }

    @Test
    public void noPermanentDenial_whenAllGranted() {
        PermissionResult result = PermissionResult.of(
                Arrays.asList(Permission.POST_NOTIFICATIONS),
                new int[]{PackageManager.PERMISSION_GRANTED},
                Collections.emptyMap());

        assertFalse(result.isPermanentlyDenied(Permission.POST_NOTIFICATIONS));
        assertFalse(result.hasPermanentlyDenied());
        assertTrue(result.allGranted());
    }
}
