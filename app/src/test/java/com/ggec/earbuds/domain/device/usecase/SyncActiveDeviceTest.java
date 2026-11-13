package com.ggec.earbuds.domain.device.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceIdentity;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import java.util.List;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceStatus;
import com.ggec.earbuds.domain.device.model.DeviceFacts;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.RecordOrigin;
import com.ggec.earbuds.domain.device.session.DeviceSession;
import com.ggec.earbuds.testing.device.DeviceFixtures;
import com.ggec.earbuds.testing.device.FakeDeviceLinkPort;
import com.ggec.earbuds.testing.device.MemoryDeviceRosterRepository;

import org.junit.Test;

import java.util.Map;
import java.util.Optional;

public final class SyncActiveDeviceTest {

    @Test
    public void readySnapshotPromotesDeviceToLiveAndKeepsAlias() {
        long[] now = {1000L};
        MemoryDeviceRosterRepository repository = new MemoryDeviceRosterRepository(
                new DeviceRoster(java.util.List.of(seedRecord("我的耳机")), Optional.empty()));
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);
        new SyncActiveDevice(repository, session, () -> now[0]).start();

        new OpenDevice(session).execute(DeviceFixtures.target(), settlement -> {
        });
        port.emit(DeviceFixtures.ready("2.0.1", 77));

        DeviceRecord record = repository.current().find(DeviceFixtures.ID).orElseThrow();
        assertEquals(RecordOrigin.LIVE, record.status().origin());
        assertEquals(ConnectionState.READY, record.status().connection());
        assertEquals("2.0.1", record.facts().firmware());
        assertEquals(77, record.facts().battery().left());
        assertEquals("我的耳机", record.alias());
        assertTrue(repository.current().activeId().isPresent());
    }

    @Test
    public void disconnectArchivesRecordAndDropsRealtimeValues() {
        long[] now = {1000L};
        MemoryDeviceRosterRepository repository = new MemoryDeviceRosterRepository();
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);
        new SyncActiveDevice(repository, session, () -> now[0]).start();
        new OpenDevice(session).execute(DeviceFixtures.target(), settlement -> {
        });
        port.emit(DeviceFixtures.ready("2.0.1", 77));

        now[0] = 9000L;
        port.emit(DeviceFixtures.disconnected());

        DeviceRecord record = repository.current().find(DeviceFixtures.ID).orElseThrow();
        assertEquals(RecordOrigin.ARCHIVED, record.status().origin());
        assertEquals(ConnectionState.DISCONNECTED, record.status().connection());
        assertEquals(9000L, record.status().lastSeenMillis());
        // 断开后只留元信息：电量/固件这些实时值不再冒充现状。
        assertFalse(record.facts().known());
        assertEquals("", record.facts().firmware());
        assertTrue(record.identity().hasClassicAddress());
        assertTrue(repository.current().activeId().isPresent());
    }


    @Test
    public void openingAnotherDeviceFirstDisconnectsAndArchivesTheCurrentOne() {
        long[] now = {1000L};
        DeviceId first = new DeviceId("HS01", "AA:BB:CC:DD:EE:01");
        DeviceId second = new DeviceId("HS02", "AA:BB:CC:DD:EE:02");
        MemoryDeviceRosterRepository repository = new MemoryDeviceRosterRepository(
                new DeviceRoster(List.of(archived(first), archived(second)), Optional.empty()));
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(port);
        new SyncActiveDevice(repository, session, () -> now[0]).start();

        new OpenDevice(session).execute(target(first), settlement -> {
        });
        port.emit(DeviceFixtures.ready("2.0.1", 77));
        new OpenDevice(session).execute(target(second), settlement -> {
        });
        port.emit(DeviceFixtures.ready("3.0.0", 55));

        DeviceRecord firstRecord = repository.current().find(first).orElseThrow();
        DeviceRecord secondRecord = repository.current().find(second).orElseThrow();
        assertEquals("换设备时先断旧链路", 2, port.disconnects);
        assertEquals(List.of(first, second),
                port.connects.stream().map(target -> target.id()).toList());
        assertEquals(RecordOrigin.ARCHIVED, firstRecord.status().origin());
        assertEquals(ConnectionState.DISCONNECTED, firstRecord.status().connection());
        assertFalse("旧设备的电量不留在台账里", firstRecord.facts().known());
        assertEquals(RecordOrigin.LIVE, secondRecord.status().origin());
        assertEquals(55, secondRecord.facts().battery().left());
        assertEquals(Optional.of(second), repository.current().activeId());
    }

    private static DeviceTarget target(DeviceId id) {
        return new DeviceTarget(id, new DeviceIdentity(id.modelId(), id.bleAddress(), id.modelId()));
    }

    private static DeviceRecord archived(DeviceId id) {
        return new DeviceRecord(id, "", target(id).identity(),
                DeviceStatus.archived(500L), DeviceFacts.unknown(), new DeviceState(Map.of()));
    }

    private static DeviceRecord seedRecord(String alias) {
        return new DeviceRecord(DeviceFixtures.ID, alias,
                new com.ggec.earbuds.domain.device.model.DeviceIdentity("HS01",
                        "11:22:33:44:55:66", "HS01"),
                DeviceStatus.archived(500L), DeviceFacts.unknown(), new DeviceState(Map.of()));
    }
}
