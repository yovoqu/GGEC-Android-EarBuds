package com.ggec.earbuds.domain.device.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
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
    public void closeCancelsPendingAttempt() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);
        List<ConnectSettlement> outcomes = new ArrayList<>();

        session.open(DeviceFixtures.target(), outcomes::add);
        session.close();

        assertEquals(1, outcomes.size());
        assertTrue(outcomes.get(0) instanceof ConnectSettlement.Failed failed
                && failed.failure() == DeviceFailure.REJECTED);
        assertEquals(2, port.disconnects);
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
}
