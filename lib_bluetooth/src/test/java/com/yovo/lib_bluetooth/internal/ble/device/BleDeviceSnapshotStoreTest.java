package com.yovo.lib_bluetooth.internal.ble.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.model.ble.BleAddressType;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleDeviceSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleDeviceType;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanRecord;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;
import com.yovo.lib_bluetooth.model.common.Result;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-010 BLE 设备身份校验（大小写归一化 / 非法 MAC 拦截）、Dual/Random 地址合并及不可变快照存储测试。
 */
public class BleDeviceSnapshotStoreTest {

    @Test
    public void deviceIdNormalizationAndValidation_rejectsMalformedMac() {
        BleDeviceId lower = new BleDeviceId("aa:bb:cc:dd:ee:ff");
        BleDeviceId upper = new BleDeviceId("AA:BB:CC:DD:EE:FF");
        assertEquals("AA:BB:CC:DD:EE:FF", lower.value());
        assertEquals(lower, upper);
        assertEquals(lower.hashCode(), upper.hashCode());

        assertFalse(BleDeviceId.isValid(""));
        assertFalse(BleDeviceId.isValid("12:34"));
        assertFalse(BleDeviceId.isValid("ZZ:BB:CC:DD:EE:FF"));
        assertTrue(BleDeviceId.isValid("00:11:22:33:44:55"));
    }

    @Test
    public void invalidDeviceId_rejectedBeforeTouchingGattPlatform() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        try {
            AtomicReference<BleFailure> failure = new AtomicReference<>();
            harness.client.connect(
                    new BleDeviceId("12:34"),
                    BleConnectionConfig.builder().build(),
                    new BleConnectionObserver() {
                        @Override
                        public void onConnectionFailed(BleFailure f) {
                            failure.set(f);
                        }
                    });
            harness.drain();
            assertNotNull(failure.get());
            assertEquals(BleErrorCode.DEVICE_INVALID, failure.get().errorCode());
            assertEquals(0, harness.connectionPlatform.totalConnectGattCalls());
        } finally {
            harness.release();
        }
    }

    @Test
    public void snapshotStore_mergesLowerAndUpperCaseAndReturnsUnmodifiableList() {
        YoBleDeviceSnapshotStore store = new YoBleDeviceSnapshotStore();

        BleDeviceIdentity id1 = new BleDeviceIdentity(
                new BleDeviceId("aa:11:22:33:44:55"), "", null,
                BleAddressType.RESOLVABLE_PRIVATE, BleDeviceType.DUAL, 1_000L);
        store.recordScanResult(new BleScanResult(
                id1, -60, BleScanRecord.empty(), 1_000L, true, true,
                BlePhy.LE_1M, BlePhy.UNKNOWN, 0xFF, 127, 0, BleScanCallbackType.ALL_MATCHES));

        BleDeviceIdentity id2 = new BleDeviceIdentity(
                new BleDeviceId("AA:11:22:33:44:55"), "EarBuds-R", "MyBuds",
                BleAddressType.UNKNOWN, BleDeviceType.UNKNOWN, 2_000L);
        store.recordIdentity(id2, null, 2_000L);

        List<BleDeviceSnapshot> all = store.all();
        assertEquals("Normalized lowercase & uppercase MAC must deduplicate", 1, all.size());

        BleDeviceSnapshot merged = all.get(0);
        assertEquals("AA:11:22:33:44:55", merged.deviceId().value());
        assertEquals("EarBuds-R", merged.identity().displayName());
        assertEquals("MyBuds", merged.identity().alias());
        assertEquals(BleAddressType.RESOLVABLE_PRIVATE, merged.identity().addressType());
        assertEquals(BleDeviceType.DUAL, merged.identity().deviceType());

        try {
            all.clear();
            fail("getDeviceSnapshots/all() must return unmodifiable list");
        } catch (UnsupportedOperationException expected) {
        }

        // 通过 YoBleClient.getDeviceSnapshots 确认也不可变
        FakeYoBleHarness harness = new FakeYoBleHarness();
        try {
            Result<List<BleDeviceSnapshot>> res = harness.client.getDeviceSnapshots();
            assertTrue(res.isSuccess());
            try {
                res.getValue().add(merged);
                fail("Client snapshot list must be unmodifiable");
            } catch (UnsupportedOperationException expected) {
            }
        } finally {
            harness.release();
        }
    }
}
