package com.ggec.earbuds.provider.lib_bluetooth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DevicePort;
import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.api.YoCbtClient;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanRecord;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;
import com.yovo.lib_bluetooth.model.classic.CbtAclLinkState;
import com.yovo.lib_bluetooth.model.classic.CbtBondState;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceId;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceIdentity;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtProfileType;
import com.yovo.lib_bluetooth.model.common.Result;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class HsDevicePortTest {

    private static final String FEATURE_CN = "86103800900000100000fffe";

    @Test
    public void reportsAdapterDisabledBeforeStartingScan() {
        AtomicBoolean scanInvoked = new AtomicBoolean(false);
        YoBleClient bleClient = bleProxy(true, false, Collections.emptyList(), (config, observer) -> {
            scanInvoked.set(true);
            return BleOperationHandle.completed("unused");
        });
        HsDevicePort port = new HsDevicePort(bleClient, null, new HsBroadcastScanMapper());

        List<DevicePort.ScanFailure> failures = new ArrayList<>();
        AtomicBoolean finished = new AtomicBoolean(false);
        port.startScan(result -> {
        }, failures::add, () -> finished.set(true));

        assertFalse(scanInvoked.get());
        assertFalse(port.isScanning());
        assertEquals(List.of(DevicePort.ScanFailure.BLUETOOTH_DISABLED), failures);
        assertTrue(finished.get());
    }

    @Test
    public void configuresBackgroundBleCallbacksAndBatchReportDelay() {
        assertFalse(HsDevicePort.defaultBleConfig().callbackOnMainThread());
        assertEquals(200L, HsDevicePort.SCAN_REPORT_DELAY_MILLIS);
    }

    @Test
    public void scansExtendedAndLegacyBroadcastsWithClassicBondedDetection() {
        AtomicReference<BleScanConfig> capturedConfig = new AtomicReference<>();
        AtomicReference<BleScanObserver> capturedObserver = new AtomicReference<>();
        AtomicBoolean handleCancelled = new AtomicBoolean(false);

        YoBleClient bleClient = bleProxy(true, true, Collections.emptyList(), (config, observer) -> {
            capturedConfig.set(config);
            capturedObserver.set(observer);
            return new BleOperationHandle("test-scan", () -> handleCancelled.set(true));
        });
        YoCbtClient classicClient = classicProxy(List.of(classicBondedSnapshot("AA:BB:CC:DD:EE:FF")));
        HsDevicePort port = new HsDevicePort(bleClient, classicClient, new HsBroadcastScanMapper());

        List<List<ScanResult>> batches = new ArrayList<>();
        List<DevicePort.ScanFailure> failures = new ArrayList<>();
        AtomicInteger finishedCount = new AtomicInteger();

        port.startScanBatch(batches::add, failures::add, finishedCount::incrementAndGet);
        assertTrue(port.isScanning());
        assertFalse(capturedConfig.get().legacyOnly());
        assertEquals(HsDevicePort.SCAN_REPORT_DELAY_MILLIS, capturedConfig.get().reportDelayMillis());
        assertEquals(0L, capturedConfig.get().deduplicationWindowMillis());
        assertTrue(capturedConfig.get().filters().isEmpty());

        BleScanResult nonHs = bleResult("99:88:77:66:55:44", -75, Collections.emptyList(), new byte[0], "Other BLE");
        BleScanResult hs01 = bleResult("11:22:33:44:55:01", -60, List.of(hs01Uuid(FEATURE_CN)), new byte[0], "HS01-Old");
        BleScanResult hs02 = bleResult("11:22:33:44:55:02", -48, Collections.emptyList(), hs02Packet("Vidda New"), "Vidda New");

        // 非 HS 广播批次不会产生空回调。
        capturedObserver.get().onBatchScanResults(List.of(nonHs));
        assertTrue(batches.isEmpty());

        // 混合批次过滤非 HS 帧后以单个不可变批次送达。
        capturedObserver.get().onBatchScanResults(List.of(hs01, nonHs, hs02));
        assertEquals(1, batches.size());
        List<ScanResult> firstBatch = batches.get(0);
        assertEquals(2, firstBatch.size());
        assertEquals("HS01", firstBatch.get(0).modelId());
        assertTrue(firstBatch.get(0).paired());
        assertEquals("HS02", firstBatch.get(1).modelId());
        assertTrue(firstBatch.get(1).paired());
        assertTrue(failures.isEmpty());

        port.stopScan();
        assertFalse(port.isScanning());
        assertTrue(handleCancelled.get());

        // 停扫之后即使底层还有残留回调，也不再向外分发。
        capturedObserver.get().onDeviceFound(hs01);
        capturedObserver.get().onBatchScanResults(List.of(hs02));
        assertEquals(1, batches.size());

        capturedObserver.get().onScanFailed(new BleFailure(
                BleErrorCode.SCAN_THROTTLED, "throttled", null, true, true, "test", null, null, null));
        assertTrue(failures.isEmpty());
        assertEquals(1, finishedCount.get());
    }

    private interface ScanStarter {
        BleOperationHandle start(BleScanConfig config, BleScanObserver observer);
    }

    private static YoBleClient bleProxy(boolean supported,
                                        boolean enabled,
                                        List<?> bondedDevices,
                                        ScanStarter starter) {
        return (YoBleClient) Proxy.newProxyInstance(
                YoBleClient.class.getClassLoader(),
                new Class<?>[]{YoBleClient.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isBleSupported" -> supported;
                    case "isAdapterEnabled" -> enabled;
                    case "getBondedDevices" -> Result.success(bondedDevices);
                    case "scan" -> starter.start((BleScanConfig) args[0], (BleScanObserver) args[1]);
                    case "stopScan" -> Result.success(null);
                    case "isScanning" -> false;
                    default -> throw new UnsupportedOperationException("Unexpected call: " + method.getName());
                });
    }

    private static YoCbtClient classicProxy(List<CbtDeviceSnapshot> bondedDevices) {
        return (YoCbtClient) Proxy.newProxyInstance(
                YoCbtClient.class.getClassLoader(),
                new Class<?>[]{YoCbtClient.class},
                (proxy, method, args) -> {
                    if ("getBondedDevices".equals(method.getName())) {
                        return Result.success(bondedDevices);
                    }
                    throw new UnsupportedOperationException("Unexpected call: " + method.getName());
                });
    }

    private static CbtDeviceSnapshot classicBondedSnapshot(String mac) {
        CbtDeviceIdentity identity = new CbtDeviceIdentity(
                new CbtDeviceId(mac),
                mac,
                "Bonded Headset");
        return new CbtDeviceSnapshot(
                identity,
                CbtBondState.BONDED,
                CbtAclLinkState.DISCONNECTED,
                CbtDeviceSnapshot.RSSI_UNAVAILABLE,
                0L,
                0L,
                Collections.emptyList(),
                new EnumMap<>(CbtProfileType.class),
                Collections.emptyList(),
                Collections.emptyList());
    }

    private static BleScanResult bleResult(String mac,
                                           int rssi,
                                           List<UUID> uuids,
                                           byte[] rawBytes,
                                           String deviceName) {
        BleDeviceIdentity identity = new BleDeviceIdentity(
                new BleDeviceId(mac),
                deviceName == null ? "" : deviceName,
                1000L);
        BleScanRecord record = new BleScanRecord(
                -1,
                uuids,
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Integer.MIN_VALUE,
                deviceName,
                rawBytes);
        return new BleScanResult(
                identity,
                rssi,
                record,
                1000L,
                true,
                true,
                BlePhy.LE_1M,
                BlePhy.UNKNOWN,
                BleScanResult.SID_NOT_PRESENT,
                BleScanResult.TX_POWER_NOT_PRESENT,
                BleScanResult.PERIODIC_INTERVAL_NOT_PRESENT,
                BleScanCallbackType.ALL_MATCHES);
    }

    private static UUID hs01Uuid(String featureCode) {
        byte[] prefix = hexToBytes(featureCode.substring(0, 18));
        byte[] uuidBytes = new byte[16];
        uuidBytes[0] = (byte) 0xFF;
        uuidBytes[1] = (byte) 0xEE;
        uuidBytes[2] = (byte) 0xDD;
        uuidBytes[3] = (byte) 0xCC;
        uuidBytes[4] = (byte) 0xBB;
        uuidBytes[5] = (byte) 0xAA;
        uuidBytes[6] = 0x02;
        for (int index = 0; index < prefix.length; index++) {
            uuidBytes[15 - index] = prefix[index];
        }
        ByteBuffer buffer = ByteBuffer.wrap(uuidBytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static byte[] hs02Packet(String name) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] nameField = new byte[nameBytes.length + 2];
        nameField[0] = (byte) (nameBytes.length + 1);
        nameField[1] = 0x09;
        System.arraycopy(nameBytes, 0, nameField, 2, nameBytes.length);

        byte[] manufacturer = new byte[]{
                (byte) 0xFF, (byte) 0xFF,
                0x48, 0x43,
                0x01, 0x02, 0x12,
                0x01, 0x00, 0x00, 0x00,
                0x0A, 0x06,
                (byte) 0xAA, (byte) 0xBB, (byte) 0xCC,
                (byte) 0xDD, (byte) 0xEE, (byte) 0xFF,
                0x0B, 0x01, 0x00
        };
        byte[] mfgField = new byte[manufacturer.length + 2];
        mfgField[0] = (byte) (manufacturer.length + 1);
        mfgField[1] = (byte) 0xFF;
        System.arraycopy(manufacturer, 0, mfgField, 2, manufacturer.length);

        byte[] out = new byte[nameField.length + mfgField.length];
        System.arraycopy(nameField, 0, out, 0, nameField.length);
        System.arraycopy(mfgField, 0, out, nameField.length, mfgField.length);
        return out;
    }

    private static byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(hex.substring(index * 2, index * 2 + 2), 16);
        }
        return result;
    }
}
