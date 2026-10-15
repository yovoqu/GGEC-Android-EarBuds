package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoCbtSppConnectionStateTest {

    @Test
    public void classifiesLifecycleStatesWithoutUiSpecificDuplication() {
        assertTrue(YoCbtSppConnectionState.PREPARING.isConnecting());
        assertTrue(YoCbtSppConnectionState.STARTING_WRITER.isConnecting());
        assertFalse(YoCbtSppConnectionState.CONNECTED.isConnecting());
        assertTrue(YoCbtSppConnectionState.CONNECTED.isConnected());
        assertTrue(YoCbtSppConnectionState.DISCONNECTED.isTerminal());
        assertTrue(YoCbtSppConnectionState.FAILED.isTerminal());
        assertTrue(YoCbtSppConnectionState.RELEASED.isTerminal());
    }
}
