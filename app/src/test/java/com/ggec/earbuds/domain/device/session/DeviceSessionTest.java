package com.ggec.earbuds.domain.device.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceIdentity;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.testing.device.DeviceFixtures;
import com.ggec.earbuds.testing.device.FakeDeviceLinkPort;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class DeviceSessionTest {

    @Test
    public void openDisconnectsFirstThenConnects() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);

        session.open(DeviceFixtures.target(), settlement -> {
        });

        assertEquals(1, port.connects.size());
        assertEquals(1, port.disconnects);
        assertTrue(session.state() instanceof SessionState.Connecting);
    }

    @Test
    public void supersededAttemptSettlesRejectedAndStaleResultIsDropped() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);
        List<ConnectSettlement> first = new ArrayList<>();

        session.open(DeviceFixtures.target(), first::add);
        session.open(DeviceFixtures.target(), settlement -> {
        });

        assertEquals(1, first.size());
        assertTrue(first.get(0) instanceof ConnectSettlement.Failed failed
                && failed.failure() == DeviceFailure.REJECTED);

        port.settleReady();
        assertEquals(1, first.size());
    }

    @Test
    public void closeCancelsPendingAttemptAndClearsTarget() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);
        List<ConnectSettlement> outcomes = new ArrayList<>();

        session.open(DeviceFixtures.target(), outcomes::add);
        session.close();

        assertEquals(1, outcomes.size());
        assertTrue(outcomes.get(0) instanceof ConnectSettlement.Failed failed
                && failed.failure() == DeviceFailure.REJECTED);
        assertEquals(2, port.disconnects);
        assertTrue(session.state() instanceof SessionState.Idle);
        assertFalse(session.target().isPresent());
    }

    @Test
    public void closePublishesExactlyOneDisconnectedSnapshot() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);
        List<DeviceSnapshot> seen = new ArrayList<>();
        session.observe(seen::add);

        session.open(DeviceFixtures.target(), settlement -> {
        });
        port.emit(DeviceFixtures.ready("1.0.0", 42));
        session.close();

        long disconnected = seen.stream()
                .filter(snapshot -> snapshot.connection() == ConnectionState.DISCONNECTED)
                .count();
        assertEquals(1, disconnected);
        assertEquals(ConnectionState.DISCONNECTED, session.current().connection());
    }

    @Test
    public void readySnapshotMovesConnectingToReady() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);

        session.open(DeviceFixtures.target(), settlement -> {
        });
        port.emit(DeviceFixtures.ready("1.0.0", 42));

        assertTrue(session.state() instanceof SessionState.Ready ready
                && ready.target().is(DeviceFixtures.ID));
        assertEquals(42, session.current().battery().left());
    }

    @Test
    public void deviceDisconnectFromReadyReturnsToIdleAndClearsTarget() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);
        session.open(DeviceFixtures.target(), settlement -> {
        });
        port.emit(DeviceFixtures.ready("1.0.0", 42));

        port.emit(DeviceFixtures.disconnected());

        assertTrue(session.state() instanceof SessionState.Idle);
        assertFalse(session.target().isPresent());
    }

    @Test
    public void failedSettlementMovesConnectingToFailedAndKeepsTarget() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);
        List<ConnectSettlement> outcomes = new ArrayList<>();

        session.open(DeviceFixtures.target(), outcomes::add);
        port.settleFailed(DeviceFailure.TIMEOUT);

        assertTrue(outcomes.get(0) instanceof ConnectSettlement.Failed failed
                && failed.failure() == DeviceFailure.TIMEOUT);
        assertTrue(session.state() instanceof SessionState.Failed failed
                && failed.failure() == DeviceFailure.TIMEOUT);
        assertEquals(DeviceFixtures.ID, session.target().orElseThrow().id());
    }

    @Test
    public void reopeningReadyTargetSettlesReadyWithoutReconnecting() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);
        session.open(DeviceFixtures.target(), settlement -> {
        });
        port.emit(DeviceFixtures.ready("1.0.0", 42));
        List<ConnectSettlement> outcomes = new ArrayList<>();

        session.open(DeviceFixtures.target(), outcomes::add);

        assertTrue(outcomes.get(0) instanceof ConnectSettlement.Ready);
        assertEquals(1, port.connects.size());
        assertEquals(1, port.disconnects);
        assertTrue(session.state() instanceof SessionState.Ready);
    }

    @Test
    public void switchingDevicePublishesDisconnectedForThePreviousOne() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);
        List<DeviceSnapshot> seen = new ArrayList<>();
        session.observe(seen::add);
        session.open(DeviceFixtures.target(), settlement -> {
        });
        port.emit(DeviceFixtures.ready("1.0.0", 42));

        session.open(secondTarget(), settlement -> {
        });

        DeviceSnapshot archived = seen.stream()
                .filter(snapshot -> snapshot.connection() == ConnectionState.DISCONNECTED)
                .findFirst()
                .orElseThrow();
        assertEquals(DeviceFixtures.ID.address(), archived.bleAddress());
        assertTrue(session.state() instanceof SessionState.Connecting);
    }

    @Test
    public void currentSnapshotFollowsPortAndReachesObservers() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);
        List<Integer> batteries = new ArrayList<>();

        session.observe(snapshot -> batteries.add(snapshot.battery().left()));
        port.emit(DeviceFixtures.ready("1.0.0", 42));

        assertEquals(42, session.current().battery().left());
        assertEquals(2, batteries.size());
    }

    /** 第二台设备：与 fixtures 的目标只差地址，用来验证「换设备先替旧设备归档」。 */
    private static DeviceTarget secondTarget() {
        DeviceId id = new DeviceId("HS01", "AA:BB:CC:DD:EE:02");
        return new DeviceTarget(id, new DeviceIdentity("HS01", "11:22:33:44:55:77", "HS01"));
    }
}
