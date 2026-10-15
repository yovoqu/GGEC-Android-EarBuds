package com.yovo.lib_common.system.permission;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionPolicy;

import org.junit.Test;

import java.util.List;

public final class PermissionPolicyTest {

    @Test
    public void bluetoothFull_android12Plus_connectAndScan() {
        List<Permission> required = PermissionPolicy.BLUETOOTH_FULL.requiredFor(31);
        assertEquals(2, required.size());
        assertTrue(required.contains(Permission.BLUETOOTH_CONNECT));
        assertTrue(required.contains(Permission.BLUETOOTH_SCAN));
        assertEquals(2, PermissionPolicy.BLUETOOTH_FULL.requiredFor(37).size());
    }

    @Test
    public void bluetoothFull_android6To11_fineLocationOnly() {
        List<Permission> required = PermissionPolicy.BLUETOOTH_FULL.requiredFor(30);
        assertEquals(1, required.size());
        assertTrue(required.contains(Permission.ACCESS_FINE_LOCATION));
    }

    @Test
    public void bluetoothSpp_android12Plus_connectAndScan_withoutLocation() {
        List<Permission> required = PermissionPolicy.BLUETOOTH_SPP.requiredFor(31);
        assertEquals(2, required.size());
        assertTrue(required.contains(Permission.BLUETOOTH_CONNECT));
        assertTrue(required.contains(Permission.BLUETOOTH_SCAN));
        assertFalse(required.contains(Permission.ACCESS_FINE_LOCATION));
        assertFalse(PermissionPolicy.BLUETOOTH_SPP.requiredFor(36)
                .contains(Permission.ACCESS_FINE_LOCATION));
    }

    @Test
    public void bluetoothSpp_beforeAndroid12_installTimeOnly() {
        List<Permission> required = PermissionPolicy.BLUETOOTH_SPP.requiredFor(30);
        assertEquals(2, required.size());
        assertTrue(required.contains(Permission.BLUETOOTH));
        assertTrue(required.contains(Permission.BLUETOOTH_ADMIN));
        assertFalse(required.contains(Permission.ACCESS_FINE_LOCATION));
        assertFalse(PermissionPolicy.BLUETOOTH_SPP.requiredFor(23)
                .contains(Permission.ACCESS_FINE_LOCATION));
    }

    @Test
    public void notification_android13Plus() {
        assertTrue(PermissionPolicy.NOTIFICATION.requiredFor(33)
                .contains(Permission.POST_NOTIFICATIONS));
        assertTrue(PermissionPolicy.NOTIFICATION.requiredFor(32).isEmpty());
    }

    @Test
    public void storageWrite_android11Plus_manageExternalStorage() {
        assertTrue(PermissionPolicy.STORAGE_WRITE.requiredFor(30)
                .contains(Permission.MANAGE_EXTERNAL_STORAGE));
        assertTrue(PermissionPolicy.STORAGE_WRITE.requiredFor(29)
                .contains(Permission.WRITE_EXTERNAL_STORAGE));
    }

    @Test
    public void installUnknownSources_android8Plus() {
        assertTrue(PermissionPolicy.INSTALL_UNKNOWN_SOURCES.requiredFor(26)
                .contains(Permission.REQUEST_INSTALL_PACKAGES));
        assertTrue(PermissionPolicy.INSTALL_UNKNOWN_SOURCES.requiredFor(25).isEmpty());
    }

    @Test
    public void storageDirect_android11Plus_manageExternalStorage() {
        assertTrue(PermissionPolicy.STORAGE_DIRECT.requiredFor(30)
                .contains(Permission.MANAGE_EXTERNAL_STORAGE));
        assertTrue(PermissionPolicy.STORAGE_DIRECT.requiredFor(29)
                .contains(Permission.WRITE_EXTERNAL_STORAGE));
        assertTrue(PermissionPolicy.STORAGE_DIRECT.requiredFor(28).isEmpty());
    }
}
