package com.yovo.lib_bluetooth.internal.ble.error;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;

import org.junit.Test;

import java.util.UUID;

/**
 * BLE-004 HCI 0x00..0x3E 与 ATT/GATT 0x00..0x101 错误码全量映射与诊断上下文测试。
 */
public class BleStatusErrorMappingTest {

    private static final BleDeviceId DEV = new BleDeviceId("11:22:33:44:55:66");

    @Test
    public void hciCodes_mapAccuratelyAndCarryMetadata() {
        assertMappedHci(YoBleHciStatus.CONNECTION_TIMEOUT, BleErrorCode.LINK_LOSS, true);
        assertMappedHci(YoBleHciStatus.LMP_RESPONSE_TIMEOUT, BleErrorCode.LINK_LOSS, true);
        assertMappedHci(YoBleHciStatus.CONNECTION_TERMINATED_MIC_FAILURE, BleErrorCode.LINK_LOSS, true);

        assertMappedHci(YoBleHciStatus.REMOTE_USER_TERMINATED_CONNECTION, BleErrorCode.REMOTE_TERMINATED, false);
        assertMappedHci(YoBleHciStatus.REMOTE_DEVICE_TERMINATED_LOW_RESOURCES, BleErrorCode.REMOTE_TERMINATED, false);
        assertMappedHci(YoBleHciStatus.REMOTE_DEVICE_TERMINATED_POWER_OFF, BleErrorCode.REMOTE_TERMINATED, false);

        assertMappedHci(YoBleHciStatus.CONNECTION_TERMINATED_BY_LOCAL_HOST, BleErrorCode.LOCAL_TERMINATED, false);
        assertMappedHci(YoBleHciStatus.GATT_CONN_CANCEL, BleErrorCode.LOCAL_TERMINATED, false);

        assertMappedHci(YoBleHciStatus.GATT_ERROR_133, BleErrorCode.GATT_ERROR_133, true);
        assertMappedHci(YoBleHciStatus.CONNECTION_FAILED_TO_ESTABLISH, BleErrorCode.CONNECT_FAILED, true);
        assertMappedHci(YoBleHciStatus.AUTHENTICATION_FAILURE, BleErrorCode.AUTHENTICATION_FAILED, false);

        // 未知 HCI 码也必须保留 rawStatusCode 并安全描述
        BleFailure vendor = YoBleErrorMapper.fromHciStatus(0x3B, "ble-conn", DEV);
        assertEquals(BleErrorCode.CONNECT_FAILED, vendor.errorCode());
        assertEquals(0x3B, vendor.rawStatusCode());
        assertNotNull(vendor.traceId());
        assertFalse(vendor.traceId().isEmpty());
        assertNotNull(vendor.suggestedAction());
        assertTrue(YoBleHciStatus.describe(0x28).contains("INSTANT_PASSED"));
    }

    @Test
    public void gattAndAttCodes_mapAccuratelyWithFallbackAndCauseChain() {
        BleGattTarget target = BleGattTarget.characteristic(
                UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"),
                UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb"));

        assertMappedGatt(YoBleGattStatus.GATT_INSUFFICIENT_AUTHENTICATION, BleErrorCode.READ_FAILED,
                BleErrorCode.AUTHENTICATION_FAILED, target);
        assertMappedGatt(YoBleGattStatus.GATT_INSUFFICIENT_AUTHORIZATION, BleErrorCode.READ_FAILED,
                BleErrorCode.AUTHORIZATION_FAILED, target);
        assertMappedGatt(YoBleGattStatus.GATT_INSUFFICIENT_ENCRYPTION, BleErrorCode.READ_FAILED,
                BleErrorCode.ENCRYPTION_FAILED, target);
        assertMappedGatt(YoBleGattStatus.GATT_REQUEST_NOT_SUPPORTED, BleErrorCode.WRITE_FAILED,
                BleErrorCode.REQUEST_NOT_SUPPORTED, target);
        assertMappedGatt(YoBleGattStatus.GATT_INVALID_OFFSET, BleErrorCode.WRITE_FAILED,
                BleErrorCode.INVALID_OFFSET_OR_LENGTH, target);
        assertMappedGatt(YoBleGattStatus.GATT_BUSY, BleErrorCode.WRITE_FAILED,
                BleErrorCode.GATT_BUSY, target);
        assertMappedGatt(YoBleGattStatus.GATT_ERROR, BleErrorCode.WRITE_FAILED,
                BleErrorCode.GATT_ERROR_133, target);

        // 未知厂商扩展码 0x99 回退 fallback，同时保留 rawStatusCode=0x99 与描述
        BleFailure ext = YoBleErrorMapper.fromGattStatus(
                0x99, BleErrorCode.WRITE_FAILED, "ble-io", DEV, target, "writeChar");
        assertEquals(BleErrorCode.WRITE_FAILED, ext.errorCode());
        assertEquals(0x99, ext.rawStatusCode());
        assertTrue(ext.message().contains("0x99"));

        // status = -1 (NO_RAW_STATUS) 与 cause 链保留校验
        IllegalStateException rootCause = new IllegalStateException("root");
        BleFailure noRaw = YoBleErrorMapper.failure(
                BleErrorCode.TRANSFER_FAILED, "ble-transfer", "Transfer error",
                rootCause, BleFailure.NO_RAW_STATUS, DEV, target, "Retry split write");
        assertEquals(BleFailure.NO_RAW_STATUS, noRaw.rawStatusCode());
        assertSame(rootCause, noRaw.cause());
        assertEquals("Retry split write", noRaw.suggestedAction());
        assertNotNull(noRaw.traceId());
        assertTrue(noRaw.recoverable());

        // 不可恢复码校验
        BleFailure fatal = YoBleErrorMapper.failure(
                BleErrorCode.BLE_NOT_SUPPORTED, "ble-scan", "No BLE");
        assertFalse(fatal.recoverable());
        assertFalse(fatal.retryable());

        // 描述全覆盖 0x00..0x101 不崩溃
        for (int status = 0; status <= 0x101; status++) {
            assertFalse(YoBleGattStatus.describe(status).isEmpty());
        }
    }

    private static void assertMappedHci(int hci, BleErrorCode expected, boolean expectedRetryable) {
        BleFailure f = YoBleErrorMapper.fromHciStatus(hci, "ble-session", DEV);
        assertEquals(expected, f.errorCode());
        assertEquals(hci, f.rawStatusCode());
        assertEquals(expectedRetryable, f.retryable());
        assertTrue(f.recoverable());
        assertNotNull(f.traceId());
        assertFalse(f.traceId().isEmpty());
        assertNotNull(f.suggestedAction());
    }

    private static void assertMappedGatt(int gatt,
                                         BleErrorCode fallback,
                                         BleErrorCode expected,
                                         BleGattTarget target) {
        BleFailure f = YoBleErrorMapper.fromGattStatus(gatt, fallback, "ble-io", DEV, target, "op");
        assertEquals(expected, f.errorCode());
        assertEquals(gatt, f.rawStatusCode());
        assertEquals(target.serviceUuid(), f.serviceUuid());
        assertEquals(target.characteristicUuid(), f.characteristicUuid());
        assertNotNull(f.traceId());
        assertNotNull(f.suggestedAction());
    }
}
