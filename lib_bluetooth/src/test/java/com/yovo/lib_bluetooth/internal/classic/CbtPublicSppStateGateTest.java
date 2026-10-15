package com.yovo.lib_bluetooth.internal.classic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionState;

import org.junit.Test;

public class CbtPublicSppStateGateTest {

    @Test
    public void connectingPhasesCollapseToOnePublicEvent() {
        CbtPublicSppStateGate gate = new CbtPublicSppStateGate();

        assertTrue(gate.accept(CbtSppConnectionState.CONNECTING));
        assertFalse(gate.accept(CbtSppConnectionState.CONNECTING));
        assertFalse(gate.accept(CbtSppConnectionState.CONNECTING));
        assertTrue(gate.accept(CbtSppConnectionState.CONNECTED));
        assertFalse(gate.accept(CbtSppConnectionState.CONNECTED));
        assertTrue(gate.accept(CbtSppConnectionState.DISCONNECTING));
        assertTrue(gate.accept(CbtSppConnectionState.DISCONNECTED));
    }

    @Test
    public void idleAndFailedAreDistinctFromConnecting() {
        CbtPublicSppStateGate gate = new CbtPublicSppStateGate();

        assertTrue(gate.accept(CbtSppConnectionState.IDLE));
        assertTrue(gate.accept(CbtSppConnectionState.CONNECTING));
        assertFalse(gate.accept(CbtSppConnectionState.CONNECTING));
        assertTrue(gate.accept(CbtSppConnectionState.FAILED));
        assertFalse(gate.accept(CbtSppConnectionState.FAILED));
    }
}
