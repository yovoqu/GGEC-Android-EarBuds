package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.model.HsScanFailure;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 逐常量钉住 {@link HsScanFailures} 的映射：扫描失败回调会发射的错误码必须得到专属分类，
 * 其余错误码必须被判为契约外，不允许任何常量落进兜底分类。
 */
public final class HsScanFailuresTest {

    /** 扫描失败回调真正会发射的错误码 → 类型化扫描失败原因。 */
    private static final Map<BleErrorCode, HsScanFailure> MAPPED = Map.ofEntries(
            Map.entry(BleErrorCode.BLUETOOTH_DISABLED, HsScanFailure.BLUETOOTH_DISABLED),
            Map.entry(BleErrorCode.PERMISSION_SCAN_DENIED, HsScanFailure.PERMISSION_DENIED),
            Map.entry(BleErrorCode.PERMISSION_CONNECT_DENIED, HsScanFailure.PERMISSION_DENIED),
            Map.entry(BleErrorCode.PERMISSION_ADVERTISE_DENIED, HsScanFailure.PERMISSION_DENIED),
            Map.entry(BleErrorCode.PERMISSION_LOCATION_DENIED, HsScanFailure.PERMISSION_DENIED),
            Map.entry(BleErrorCode.LOCATION_SERVICES_DISABLED, HsScanFailure.LOCATION_DISABLED),
            Map.entry(BleErrorCode.BLE_NOT_SUPPORTED, HsScanFailure.BLE_UNSUPPORTED),
            Map.entry(BleErrorCode.BLUETOOTH_NOT_SUPPORTED, HsScanFailure.BLE_UNSUPPORTED),
            Map.entry(BleErrorCode.SCAN_THROTTLED, HsScanFailure.SCAN_THROTTLED),
            Map.entry(BleErrorCode.SCAN_START_FAILED, HsScanFailure.SCAN_START_FAILED),
            Map.entry(BleErrorCode.SCAN_FEATURE_UNSUPPORTED, HsScanFailure.SCAN_FEATURE_UNSUPPORTED),
            Map.entry(BleErrorCode.SCAN_OUT_OF_RESOURCES, HsScanFailure.SCAN_OUT_OF_RESOURCES),
            Map.entry(BleErrorCode.SCAN_INTERNAL_ERROR, HsScanFailure.SCAN_INTERNAL_ERROR),
            Map.entry(BleErrorCode.RELEASED, HsScanFailure.RELEASED));

    /** 不属于扫描失败回调取值域的错误码：映射必须按契约不变量拒绝，而不是伪造扫描原因。 */
    private static final Set<BleErrorCode> OUTSIDE_SCAN_CALLBACK = EnumSet.of(
            BleErrorCode.UNSUPPORTED_OPERATION,
            BleErrorCode.SCAN_STOP_FAILED,
            BleErrorCode.DEVICE_INVALID,
            BleErrorCode.ILLEGAL_ARGUMENT,
            BleErrorCode.ILLEGAL_STATE,
            BleErrorCode.CONNECT_FAILED,
            BleErrorCode.CONNECT_TIMEOUT,
            BleErrorCode.LINK_LOSS,
            BleErrorCode.REMOTE_TERMINATED,
            BleErrorCode.LOCAL_TERMINATED,
            BleErrorCode.GATT_ERROR_133,
            BleErrorCode.RETRIES_EXHAUSTED,
            BleErrorCode.CONNECTION_NOT_READY,
            BleErrorCode.GATT_OPERATION_TIMEOUT,
            BleErrorCode.GATT_BUSY,
            BleErrorCode.GATT_QUEUE_FULL,
            BleErrorCode.SERVICE_DISCOVERY_FAILED,
            BleErrorCode.SERVICE_NOT_FOUND,
            BleErrorCode.CHARACTERISTIC_NOT_FOUND,
            BleErrorCode.DESCRIPTOR_NOT_FOUND,
            BleErrorCode.READ_FAILED,
            BleErrorCode.WRITE_FAILED,
            BleErrorCode.NOTIFY_FAILED,
            BleErrorCode.AUTHENTICATION_FAILED,
            BleErrorCode.AUTHORIZATION_FAILED,
            BleErrorCode.ENCRYPTION_FAILED,
            BleErrorCode.INVALID_OFFSET_OR_LENGTH,
            BleErrorCode.REQUEST_NOT_SUPPORTED,
            BleErrorCode.RELIABLE_WRITE_MISMATCH,
            BleErrorCode.TRANSFER_FAILED,
            BleErrorCode.RESPONSE_MISMATCH,
            BleErrorCode.PAYLOAD_MERGE_FAILED,
            BleErrorCode.BOND_FAILED,
            BleErrorCode.BOND_CANCELLED,
            BleErrorCode.BOND_TIMEOUT,
            BleErrorCode.HIDDEN_API_BLOCKED,
            BleErrorCode.HIDDEN_API_FAILED,
            BleErrorCode.L2CAP_OPEN_FAILED,
            BleErrorCode.L2CAP_IO_FAILED,
            BleErrorCode.ADVERTISE_FAILED,
            BleErrorCode.ADVERTISE_DATA_TOO_LARGE,
            BleErrorCode.GATT_SERVER_START_FAILED,
            BleErrorCode.GATT_SERVER_NOTIFY_FAILED,
            BleErrorCode.OPERATION_CANCELLED,
            BleErrorCode.INTERNAL_ERROR);

    @Test
    public void everyBleErrorCodeIsClassifiedExactlyOnce() {
        Set<BleErrorCode> classified = EnumSet.noneOf(BleErrorCode.class);
        classified.addAll(MAPPED.keySet());
        classified.addAll(OUTSIDE_SCAN_CALLBACK);
        assertEquals(EnumSet.allOf(BleErrorCode.class), classified);
        assertEquals(BleErrorCode.values().length, classified.size());

        Set<BleErrorCode> both = EnumSet.copyOf(MAPPED.keySet());
        both.retainAll(OUTSIDE_SCAN_CALLBACK);
        assertTrue("错误码不能同时属于两类: " + both, both.isEmpty());
    }

    @Test
    public void mapsEveryScanCallbackErrorCodeToItsOwnTypedFailure() {
        for (Map.Entry<BleErrorCode, HsScanFailure> expected : MAPPED.entrySet()) {
            assertEquals("错误码 " + expected.getKey(),
                    expected.getValue(), HsScanFailures.map(expected.getKey()));
        }
    }

    @Test
    public void rejectsErrorCodesOutsideTheScanCallbackContract() {
        for (BleErrorCode code : OUTSIDE_SCAN_CALLBACK) {
            IllegalArgumentException rejected = assertThrows("错误码 " + code,
                    IllegalArgumentException.class, () -> HsScanFailures.map(code));
            assertTrue("拒绝消息必须带出错误码: " + rejected.getMessage(),
                    rejected.getMessage().contains(code.name()));
        }
    }
}
