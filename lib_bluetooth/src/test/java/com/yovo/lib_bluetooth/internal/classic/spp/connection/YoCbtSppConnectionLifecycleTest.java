package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;

import org.junit.Before;
import org.junit.Test;

public class YoCbtSppConnectionLifecycleTest {

    @Before
    public void disableLogger() {
        YoBluetoothLogger.setEnabled(false);
    }

    @Test
    public void enterPreparing_isQueryableAsPreparing() {
        YoCbtSppConnectionLifecycle lifecycle =
                new YoCbtSppConnectionLifecycle(new YoCbtSppConnectionId("c1"));

        lifecycle.enter(YoCbtSppConnectionState.PREPARING, null);

        assertEquals(YoCbtSppConnectionState.PREPARING, lifecycle.getCurrentPhase());
        assertFalse(lifecycle.isActive());
    }

    @Test
    public void enterFailed_keepsFailureOnSnapshotPath() {
        YoCbtSppConnectionLifecycle lifecycle =
                new YoCbtSppConnectionLifecycle(new YoCbtSppConnectionId("c1"));
        YoCbtFailure failure = YoCbtFailure.of(
                YoCbtErrorCode.YO_CBT_SPP_UUID_INVALID, "strategy mismatch", "test");

        lifecycle.enter(YoCbtSppConnectionState.FAILED, failure);

        assertEquals(YoCbtSppConnectionState.FAILED, lifecycle.getCurrentPhase());
        assertSame(failure, lifecycle.getFailure());
        assertFalse(lifecycle.isActive());
    }

    @Test
    public void enterConnected_thenDisconnected_clearsActive() {
        YoCbtSppConnectionLifecycle lifecycle =
                new YoCbtSppConnectionLifecycle(new YoCbtSppConnectionId("c1"));

        lifecycle.enter(YoCbtSppConnectionState.CONNECTED, null);
        assertTrue(lifecycle.isActive());

        lifecycle.enter(YoCbtSppConnectionState.DISCONNECTED, null);
        assertEquals(YoCbtSppConnectionState.DISCONNECTED, lifecycle.getCurrentPhase());
        assertFalse(lifecycle.isActive());
    }
}
