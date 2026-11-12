package com.ggec.earbuds.domain.device.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

/**
 * 领域快照的构造契约：缺失语义由调用方写成显式值（未知电量、未知佩戴、空串），
 * 记录本身不再把 null 静默补成默认值。
 */
public final class DeviceSnapshotTest {

    @Test
    public void nullRequiredFieldsAreRejectedAtConstruction() {
        assertEquals("connection", assertThrows(NullPointerException.class,
                () -> snapshot(null, "HS01", "v1", BatterySnapshot.unknown(), WearState.UNKNOWN,
                        "", "HS01", "AA:BB:CC:00:00:01")).getMessage());
        assertEquals("battery", assertThrows(NullPointerException.class,
                () -> snapshot(ConnectionState.READY, "HS01", "v1", null, WearState.UNKNOWN,
                        "", "HS01", "AA:BB:CC:00:00:01")).getMessage());
        assertEquals("wear", assertThrows(NullPointerException.class,
                () -> snapshot(ConnectionState.READY, "HS01", "v1", BatterySnapshot.unknown(), null,
                        "", "HS01", "AA:BB:CC:00:00:01")).getMessage());
        assertEquals("firmware", assertThrows(NullPointerException.class,
                () -> snapshot(ConnectionState.READY, "HS01", "v1", BatterySnapshot.unknown(),
                        WearState.UNKNOWN, null, "HS01", "AA:BB:CC:00:00:01")).getMessage());
        assertEquals("deviceName", assertThrows(NullPointerException.class,
                () -> snapshot(ConnectionState.READY, "HS01", "v1", BatterySnapshot.unknown(),
                        WearState.UNKNOWN, "", null, "AA:BB:CC:00:00:01")).getMessage());
        assertEquals("mac", assertThrows(NullPointerException.class,
                () -> snapshot(ConnectionState.READY, "HS01", "v1", BatterySnapshot.unknown(),
                        WearState.UNKNOWN, "", "HS01", null)).getMessage());
    }

    @Test
    public void idleCarriesExplicitUnknownBatteryAndWear() {
        DeviceSnapshot idle = DeviceSnapshot.idle();

        assertEquals(ConnectionState.IDLE, idle.connection());
        assertEquals(BatterySnapshot.UNKNOWN, idle.battery().left());
        assertEquals(BatterySnapshot.UNKNOWN, idle.battery().right());
        assertEquals(BatterySnapshot.UNKNOWN, idle.battery().caseLevel());
        assertEquals(WearState.UNKNOWN, idle.wear());
        assertEquals("", idle.firmware());
        assertEquals("", idle.deviceName());
        assertEquals("", idle.mac());
        assertEquals(DeviceSnapshot.UNKNOWN_EAR_COLOR, idle.earColor());
    }

    @Test
    public void idleIsTheOnlyStateWithoutModelIdentity() {
        // 未连接时没有设备身份：“没有设备”这一事实由 ConnectionState.IDLE 承载，两张 id 留空。
        assertNull(DeviceSnapshot.idle().modelId());
        assertNull(DeviceSnapshot.idle().variantId());
    }

    /** 具名参数装配，避免位置参数把断言目标搞混。 */
    private static DeviceSnapshot snapshot(ConnectionState connection, String modelId,
                                           String variantId, BatterySnapshot battery, WearState wear,
                                           String firmware, String deviceName, String mac) {
        return new DeviceSnapshot(connection, modelId, variantId, battery, wear, firmware,
                deviceName, mac, DeviceSnapshot.UNKNOWN_EAR_COLOR);
    }
}
