package com.ggec.earbuds.domain.device.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceFacts;
import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DeviceStatus;
import com.ggec.earbuds.domain.device.model.RecordOrigin;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.session.DeviceSession;
import com.ggec.earbuds.testing.device.DeviceFixtures;
import com.ggec.earbuds.testing.device.FakeDeviceLinkPort;
import com.ggec.earbuds.testing.device.MemoryDeviceRosterRepository;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ObserveDeviceRosterTest {

    @Test
    public void liveSessionOverlaysActiveDeviceWithoutTouchingStoredFacts() {
        MemoryDeviceRosterRepository repository =
                new MemoryDeviceRosterRepository(new DeviceRoster(List.of(archived()), Optional.empty()));
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);
        ObserveDeviceRoster projection = new ObserveDeviceRoster(repository, session, () -> 2000L);

        new OpenDevice(session).execute(DeviceFixtures.target(), settlement -> {
        });
        port.emit(DeviceFixtures.connecting());

        DeviceRecord record = projection.current().find(DeviceFixtures.ID).orElseThrow();
        assertEquals(RecordOrigin.LIVE, record.status().origin());
        assertEquals(ConnectionState.CONNECTING, record.status().connection());
        assertEquals(30, record.facts().battery().left());
        assertTrue(projection.current().activeId().isPresent());
    }

    @Test
    public void disconnectedSessionFallsBackToStoredRoster() {
        MemoryDeviceRosterRepository repository =
                new MemoryDeviceRosterRepository(new DeviceRoster(List.of(archived()), Optional.empty()));
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);
        ObserveDeviceRoster projection = new ObserveDeviceRoster(repository, session, () -> 2000L);

        new OpenDevice(session).execute(DeviceFixtures.target(), settlement -> {
        });
        port.emit(DeviceFixtures.disconnected());

        assertEquals(repository.current(), projection.current());
        assertFalse(projection.current().activeId().isPresent());
    }

    private static DeviceRecord archived() {
        return new DeviceRecord(DeviceFixtures.ID, "",
                new com.ggec.earbuds.domain.device.model.DeviceIdentity("HS01",
                        "11:22:33:44:55:66", "HS01"),
                DeviceStatus.archived(500L),
                new DeviceFacts(new BatterySnapshot(30, 20, -1, false), "1.0.0",
                        WearState.UNKNOWN, DeviceSnapshot.UNKNOWN_EAR_COLOR),
                new DeviceState(Map.of()));
    }
}
