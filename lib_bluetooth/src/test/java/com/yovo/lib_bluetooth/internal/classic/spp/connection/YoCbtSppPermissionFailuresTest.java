package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;

import org.junit.Test;

public final class YoCbtSppPermissionFailuresTest {

    @Test
    public void connectPermission_android12AttributionSource() {
        SecurityException error = new SecurityException(
                "Need android.permission.BLUETOOTH_CONNECT permission for AttributionSource");
        assertEquals(YoCbtErrorCode.YO_CBT_PERMISSION_CONNECT_DENIED,
                YoCbtSppPermissionFailures.fromThrowable(error));
    }

    @Test
    public void legacyBluetooth_android12VendorSocketConnect() {
        SecurityException error = new SecurityException(
                "UID 10391 / PID 18566 lacks permission android.permission.BLUETOOTH");
        assertEquals(YoCbtErrorCode.YO_CBT_PERMISSION_CONNECT_DENIED,
                YoCbtSppPermissionFailures.fromThrowable(error));
    }

    @Test
    public void scanPermission_cancelDiscovery() {
        SecurityException error = new SecurityException(
                "Need android.permission.BLUETOOTH_SCAN permission: AdapterService cancelDiscovery");
        assertEquals(YoCbtErrorCode.YO_CBT_PERMISSION_SCAN_DENIED,
                YoCbtSppPermissionFailures.fromThrowable(error));
    }

    @Test
    public void wrappedReflectionCause_isStillClassified() {
        SecurityException cause = new SecurityException(
                "Need android.permission.BLUETOOTH_CONNECT permission");
        Exception wrapped = new Exception("createRfcommSocket failed", cause);
        assertEquals(YoCbtErrorCode.YO_CBT_PERMISSION_CONNECT_DENIED,
                YoCbtSppPermissionFailures.fromThrowable(wrapped));
    }

    @Test
    public void ordinaryIoFailure_isNotPermission() {
        assertNull(YoCbtSppPermissionFailures.fromThrowable(
                new java.io.IOException("read failed, socket might closed or timeout")));
    }
}
