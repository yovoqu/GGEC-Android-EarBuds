package com.ggec.earbuds.domain.device.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 领域快照的构造契约：缺失语义由调用方写成显式值（未知电量、未知佩戴、空串、空表、
 * 空 Optional），记录本身不把 null 静默补成默认值。
 */
public final class DeviceSnapshotTest {

    @Test
    public void nullRequiredFieldsAreRejectedAtConstruction() {
        assertEquals("connection", assertThrows(NullPointerException.class,
                () -> snapshot(null, "HS01",  BatterySnapshot.unknown(), WearState.UNKNOWN,
                        "", "HS01", "AA:BB:CC:00:00:01", new DeviceState(Map.of()),
                        Optional.empty(), List.of())).getMessage());
        assertEquals("battery", assertThrows(NullPointerException.class,
                () -> snapshot(ConnectionState.READY, "HS01",  null, WearState.UNKNOWN,
                        "", "HS01", "AA:BB:CC:00:00:01", new DeviceState(Map.of()),
                        Optional.empty(), List.of())).getMessage());
        assertEquals("wearState", assertThrows(NullPointerException.class,
                () -> snapshot(ConnectionState.READY, "HS01",  BatterySnapshot.unknown(), null,
                        "", "HS01", "AA:BB:CC:00:00:01", new DeviceState(Map.of()),
                        Optional.empty(), List.of())).getMessage());
        assertEquals("firmware", assertThrows(NullPointerException.class,
                () -> snapshot(ConnectionState.READY, "HS01",  BatterySnapshot.unknown(),
                        WearState.UNKNOWN, null, "HS01", "AA:BB:CC:00:00:01", new DeviceState(Map.of()),
                        Optional.empty(), List.of())).getMessage());
        assertEquals("bleName", assertThrows(NullPointerException.class,
                () -> snapshot(ConnectionState.READY, "HS01",  BatterySnapshot.unknown(),
                        WearState.UNKNOWN, "", null, "AA:BB:CC:00:00:01", new DeviceState(Map.of()),
                        Optional.empty(), List.of())).getMessage());
        assertEquals("bleAddress", assertThrows(NullPointerException.class,
                () -> snapshot(ConnectionState.READY, "HS01",  BatterySnapshot.unknown(),
                        WearState.UNKNOWN, "", "HS01", null, new DeviceState(Map.of()),
                        Optional.empty(), List.of())).getMessage());
        assertEquals("state", assertThrows(NullPointerException.class,
                () -> snapshot(ConnectionState.READY, "HS01",  BatterySnapshot.unknown(),
                        WearState.UNKNOWN, "", "HS01", "AA:BB:CC:00:00:01", null,
                        Optional.empty(), List.of())).getMessage());
        assertEquals("dualConnection", assertThrows(NullPointerException.class,
                () -> snapshot(ConnectionState.READY, "HS01",  BatterySnapshot.unknown(),
                        WearState.UNKNOWN, "", "HS01", "AA:BB:CC:00:00:01", new DeviceState(Map.of()),
                        null, List.of())).getMessage());
        assertEquals("recordFiles", assertThrows(NullPointerException.class,
                () -> snapshot(ConnectionState.READY, "HS01",  BatterySnapshot.unknown(),
                        WearState.UNKNOWN, "", "HS01", "AA:BB:CC:00:00:01", new DeviceState(Map.of()),
                        Optional.empty(), null)).getMessage());
    }

    @Test
    public void idleCarriesExplicitUnknownBatteryWearAndListData() {
        DeviceSnapshot idle = DeviceSnapshot.idle();

        assertEquals(ConnectionState.IDLE, idle.connection());
        assertEquals(BatterySnapshot.UNKNOWN, idle.battery().left());
        assertEquals(BatterySnapshot.UNKNOWN, idle.battery().right());
        assertEquals(BatterySnapshot.UNKNOWN, idle.battery().caseLevel());
        assertEquals(WearState.UNKNOWN, idle.wearState());
        assertEquals("", idle.firmware());
        assertEquals("", idle.bleName());
        assertEquals("", idle.bleAddress());
        assertEquals("", idle.classicAddress());
        assertEquals("", idle.classicName());
        assertEquals(DeviceSnapshot.UNKNOWN_EAR_COLOR, idle.earColor());
        assertTrue(idle.state().values().isEmpty());
        assertTrue(idle.dualConnection().isEmpty());
        assertTrue(idle.recordFiles().isEmpty());
        assertFalse(idle.isConnected());
    }

    @Test
    public void idleIsTheOnlyStateWithoutModelIdentity() {
        // 未连接时没有设备身份：“没有设备”这一事实由 ConnectionState.IDLE 承载，两张 id 留空。
        assertNull(DeviceSnapshot.idle().modelId());
        assertNull(DeviceSnapshot.idle().modelId());
    }

    @Test
    public void listDataTravelsThroughTheSnapshotWithoutCopyingFacts() {
        DualConnectionView dual = new DualConnectionView(true, "AA:BB:CC:00:00:02",
                List.of(new PairedDeviceView("Phone", "AA:BB:CC:00:00:02",
                        PairedDeviceView.PairedDeviceType.PHONE, true,
                        PairedDeviceView.PairedBusiness.IDLE, false, true)));
        List<RecordFileView> files = List.of(new RecordFileView("f1", "rec", 12, 2048L));

        DeviceSnapshot snapshot = snapshot(ConnectionState.READY, "HS02", 
                BatterySnapshot.unknown(), WearState.NOT_WORN, "1.0.3", "Vidda V11",
                "AA:BB:CC:00:00:01", new DeviceState(Map.of()), Optional.of(dual), files);

        assertEquals(Optional.of(dual), snapshot.dualConnection());
        assertEquals(files, snapshot.recordFiles());
        assertTrue(snapshot.isConnected());
    }

    /** 具名参数装配，避免位置参数把断言目标搞混。 */
    private static DeviceSnapshot snapshot(ConnectionState connection, String modelId,
                                           BatterySnapshot battery, WearState wear,
                                           String firmware, String displayName, String bleAddress,
                                           DeviceState state,
                                           Optional<DualConnectionView> dualConnection,
                                           List<RecordFileView> recordFiles) {
        return new DeviceSnapshot(connection, modelId, battery, wear, firmware,
                bleAddress, displayName, "", "", DeviceSnapshot.UNKNOWN_EAR_COLOR, state,
                dualConnection, recordFiles);
    }
}
