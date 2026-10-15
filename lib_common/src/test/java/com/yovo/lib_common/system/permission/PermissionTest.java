package com.yovo.lib_common.system.permission;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionKind;

import org.junit.Test;

public final class PermissionTest {

    @Test
    public void bluetoothPermissions_requiredOnlyOnAndroid12Plus() {
        assertFalse(Permission.BLUETOOTH_CONNECT.isRequired(30));
        assertTrue(Permission.BLUETOOTH_CONNECT.isRequired(31));
        assertTrue(Permission.BLUETOOTH_CONNECT.isRequired(36));
        assertTrue(Permission.BLUETOOTH_SCAN.isRequired(37));
    }

    @Test
    public void fineLocation_requiredFrom23To30() {
        assertFalse(Permission.ACCESS_FINE_LOCATION.isRequired(22));
        assertTrue(Permission.ACCESS_FINE_LOCATION.isRequired(23));
        assertTrue(Permission.ACCESS_FINE_LOCATION.isRequired(30));
        assertFalse(Permission.ACCESS_FINE_LOCATION.isRequired(31));
    }

    @Test
    public void notification_requiredOnAndroid13Plus() {
        assertFalse(Permission.POST_NOTIFICATIONS.isRequired(32));
        assertTrue(Permission.POST_NOTIFICATIONS.isRequired(33));
        assertTrue(Permission.POST_NOTIFICATIONS.isRequired(37));
    }

    @Test
    public void storageMapping_android10To17() {
        assertTrue(Permission.WRITE_EXTERNAL_STORAGE.isRequired(29));
        assertFalse(Permission.WRITE_EXTERNAL_STORAGE.isRequired(30));
        assertTrue(Permission.MANAGE_EXTERNAL_STORAGE.isRequired(30));
        assertTrue(Permission.MANAGE_EXTERNAL_STORAGE.isRequired(37));
        assertFalse(Permission.MANAGE_EXTERNAL_STORAGE.isRequired(29));
        assertTrue(Permission.MANAGE_EXTERNAL_STORAGE.isSpecialAccess());
        assertTrue(Permission.WRITE_EXTERNAL_STORAGE.isRuntime());
        assertTrue(Permission.MANAGE_EXTERNAL_STORAGE.kind()
                == PermissionKind.SPECIAL_ACCESS);
    }

    @Test
    public void installUnknownSources_requiredOnAndroid8Plus() {
        assertFalse(Permission.REQUEST_INSTALL_PACKAGES.isRequired(25));
        assertTrue(Permission.REQUEST_INSTALL_PACKAGES.isRequired(26));
        assertTrue(Permission.REQUEST_INSTALL_PACKAGES.isRequired(37));
        assertTrue(Permission.REQUEST_INSTALL_PACKAGES.isSpecialAccess());
    }

    @Test
    public void specialAccessCatalog_coversOverlayWriteSettingsAndNotificationListener() {
        assertTrue(Permission.SYSTEM_ALERT_WINDOW.isSpecialAccess());
        assertTrue(Permission.WRITE_SETTINGS.isSpecialAccess());
        assertTrue(Permission.BIND_NOTIFICATION_LISTENER_SERVICE.isSpecialAccess());
        assertTrue(Permission.SYSTEM_ALERT_WINDOW.isRequired(23));
        assertTrue(Permission.WRITE_SETTINGS.isRequired(37));
        assertTrue(Permission.BIND_NOTIFICATION_LISTENER_SERVICE.isRequired(37));
    }

    @Test
    public void installTimePermissions_areNotRuntimeOrSpecial() {
        assertTrue(Permission.BLUETOOTH.isInstallTime());
        assertTrue(Permission.BLUETOOTH_ADMIN.isInstallTime());
        assertFalse(Permission.BLUETOOTH.isRequired(31));
        assertTrue(Permission.BLUETOOTH.isRequired(24));
        assertFalse(Permission.BLUETOOTH_ADMIN.isRuntime());
        assertFalse(Permission.BLUETOOTH_ADMIN.isSpecialAccess());
    }

    @Test
    public void advertiseAndCoarseLocation_areTracked() {
        assertTrue(Permission.BLUETOOTH_ADVERTISE.isRequired(31));
        assertFalse(Permission.BLUETOOTH_ADVERTISE.isRequired(30));
        assertTrue(Permission.ACCESS_COARSE_LOCATION.isRequired(30));
        assertFalse(Permission.ACCESS_COARSE_LOCATION.isRequired(31));
        assertTrue(Permission.READ_EXTERNAL_STORAGE.isRequired(29));
        assertFalse(Permission.READ_EXTERNAL_STORAGE.isRequired(30));
    }
}
