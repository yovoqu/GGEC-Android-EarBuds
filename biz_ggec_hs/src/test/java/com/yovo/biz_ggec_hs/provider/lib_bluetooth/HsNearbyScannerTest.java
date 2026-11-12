package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.api.HsNearbyObserver;
import com.yovo.biz_ggec_hs.model.HsNearbyDevice;
import com.yovo.biz_ggec_hs.model.HsScanFailure;
import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanRecord;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class HsNearbyScannerTest {

    private static final String FEATURE_CN = "86103800900000100000fffe";

    @Test
    public void forwardsLibraryScanFailureWithoutStartingItsOwnPolicy() {
        AtomicInteger subscriptions = new AtomicInteger();
        YoBleClient client = proxy(observer -> {
            subscriptions.incrementAndGet();
            observer.onScanFailed(new BleFailure(
                    BleErrorCode.BLUETOOTH_DISABLED, "off", null, true, true, "test", null, null, null));
            return new BleOperationHandle("sub", () -> {
            });
        });
        HsNearbyScanner scanner = new HsNearbyScanner(client);
        List<HsScanFailure> failures = new ArrayList<>();
        scanner.start(new HsNearbyObserver() {
            @Override
            public void onUpdated(List<HsNearbyDevice> devices) {
            }

            @Override
            public void onFailed(HsScanFailure failure) {
                failures.add(failure);
            }

            @Override
            public void onFinished() {
            }
        });
        assertEquals(1, subscriptions.get());
        assertEquals(List.of(HsScanFailure.BLUETOOTH_DISABLED), failures);
        assertFalse(scanner.isScanning());
    }

    @Test
    public void keepsOnlyAdvertisingHsDevicesFromRawFrames() {
        AtomicReference<BleScanObserver> captured = new AtomicReference<>();
        YoBleClient client = proxy(observer -> {
            captured.set(observer);
            return new BleOperationHandle("sub", () -> {
            });
        });
        HsNearbyScanner scanner = new HsNearbyScanner(client);
        List<List<HsNearbyDevice>> batches = new ArrayList<>();
        scanner.start(new HsNearbyObserver() {
            @Override
            public void onUpdated(List<HsNearbyDevice> devices) {
                batches.add(devices);
            }

            @Override
            public void onFailed(HsScanFailure failure) {
            }

            @Override
            public void onFinished() {
            }
        });
        captured.get().onBatchScanResults(List.of(
                frame("99:88:77:66:55:44", -70, Collections.emptyList(), new byte[0], "Other"),
                frame("11:22:33:44:55:01", -60, List.of(hs01Uuid(FEATURE_CN)), new byte[0], "HS01-Old"),
                frame("11:22:33:44:55:02", -48, Collections.emptyList(), hs02Packet("Vidda New"), "Vidda New")));
        assertEquals(1, batches.size());
        assertEquals(2, batches.get(0).size());
        assertEquals("HS01", batches.get(0).get(0).modelId());
        assertEquals("HS02", batches.get(0).get(1).modelId());
        assertTrue(scanner.isScanning());
        scanner.stop();
        assertFalse(scanner.isScanning());
    }

    @Test
    public void synchronousFailureLeavesNoLeakedSubscription() throws Exception {
        AtomicInteger cancellations = new AtomicInteger();
        YoBleClient client = proxy(observer -> {
            observer.onScanFailed(new BleFailure(
                    BleErrorCode.BLUETOOTH_DISABLED, "off", null, true, true, "test", null, null, null));
            return new BleOperationHandle("sub", cancellations::incrementAndGet);
        });
        HsNearbyScanner scanner = new HsNearbyScanner(client);
        List<HsScanFailure> failures = new ArrayList<>();
        scanner.start(new HsNearbyObserver() {
            @Override
            public void onUpdated(List<HsNearbyDevice> devices) {
            }

            @Override
            public void onFailed(HsScanFailure failure) {
                failures.add(failure);
            }

            @Override
            public void onFinished() {
            }
        });
        assertEquals(List.of(HsScanFailure.BLUETOOTH_DISABLED), failures);
        assertFalse(scanner.isScanning());
        assertEquals(1, cancellations.get());
        assertNull(readSubscription(scanner));
    }

    @Test
    public void forwardsLostForSeenDeviceByAddress() {
        AtomicReference<BleScanObserver> captured = new AtomicReference<>();
        YoBleClient client = proxy(observer -> {
            captured.set(observer);
            return new BleOperationHandle("sub", () -> {
            });
        });
        HsNearbyScanner scanner = new HsNearbyScanner(client);
        List<HsNearbyDevice> updated = new ArrayList<>();
        List<HsNearbyDevice> lost = new ArrayList<>();
        scanner.start(new HsNearbyObserver() {
            @Override
            public void onUpdated(List<HsNearbyDevice> devices) {
                updated.addAll(devices);
            }

            @Override
            public void onLost(HsNearbyDevice device) {
                lost.add(device);
            }

            @Override
            public void onFailed(HsScanFailure failure) {
            }

            @Override
            public void onFinished() {
            }
        });
        BleScanResult frame = frame("11:22:33:44:55:02", -48, Collections.emptyList(),
                hs02Packet("Vidda New"), "Vidda New");
        captured.get().onDeviceFound(frame);
        assertEquals(1, updated.size());

        captured.get().onScanResultLost(frame);
        assertEquals(1, lost.size());
        assertEquals(updated.get(0).bleMac(), lost.get(0).bleMac());

        // 未上报过的地址，lost 直接忽略。
        captured.get().onScanResultLost(
                frame("22:22:22:22:22:22", -60, Collections.emptyList(), new byte[0], "Other"));
        assertEquals(1, lost.size());
        scanner.stop();
    }

    @Test
    public void asyncScanFailureAfterStartEndsSessionAndCancelsSubscription() throws Exception {
        AtomicReference<BleScanObserver> captured = new AtomicReference<>();
        AtomicInteger cancellations = new AtomicInteger();
        YoBleClient client = proxy(observer -> {
            captured.set(observer);
            return new BleOperationHandle("sub", cancellations::incrementAndGet);
        });
        HsNearbyScanner scanner = new HsNearbyScanner(client);
        List<HsScanFailure> failures = new ArrayList<>();
        AtomicInteger finished = new AtomicInteger();
        scanner.start(new HsNearbyObserver() {
            @Override
            public void onUpdated(List<HsNearbyDevice> devices) {
            }

            @Override
            public void onFailed(HsScanFailure failure) {
                failures.add(failure);
            }

            @Override
            public void onFinished() {
                finished.incrementAndGet();
            }
        });
        assertTrue(scanner.isScanning());

        // start() 已返回，失败由 lib 异步投递。
        captured.get().onScanFailed(new BleFailure(
                BleErrorCode.SCAN_THROTTLED, "throttled", null, false, true, "test", null, null, null));

        assertFalse(scanner.isScanning());
        assertEquals(List.of(HsScanFailure.SCAN_THROTTLED), failures);
        assertEquals(1, finished.get());
        assertEquals(1, cancellations.get());
        assertNull(readSubscription(scanner));
    }

    @Test
    public void callbacksFromPreviousSessionAreDroppedAfterRestart() {
        List<BleScanObserver> captured = new ArrayList<>();
        YoBleClient client = proxy(observer -> {
            captured.add(observer);
            return new BleOperationHandle("sub", () -> {
            });
        });
        HsNearbyScanner scanner = new HsNearbyScanner(client);
        RecordingObserver previous = new RecordingObserver();
        scanner.start(previous);
        BleScanObserver previousCallbacks = captured.get(0);

        RecordingObserver current = new RecordingObserver();
        scanner.start(current);
        assertEquals(2, captured.size());
        BleScanObserver currentCallbacks = captured.get(1);

        BleScanResult frame = frame("11:22:33:44:55:02", -48, Collections.emptyList(),
                hs02Packet("Vidda New"), "Vidda New");
        // 旧会话的观察者在新 start() 之后仍被 lib 触发：帧、lost、停止、失败全部丢弃。
        previousCallbacks.onDeviceFound(frame);
        previousCallbacks.onBatchScanResults(List.of(frame));
        previousCallbacks.onScanResultLost(frame);
        previousCallbacks.onScanStopped();
        previousCallbacks.onScanFailed(new BleFailure(
                BleErrorCode.BLUETOOTH_DISABLED, "off", null, true, true, "test", null, null, null));

        assertTrue(previous.events.isEmpty());
        assertTrue(scanner.isScanning());

        // 同一帧在新会话里照常上报，证明丢弃按代次生效而不是整体静音。
        currentCallbacks.onBatchScanResults(List.of(frame));
        assertEquals(List.of("updated"), current.events);
        assertEquals(1, current.updated.size());
        scanner.stop();
    }

    private static final class RecordingObserver implements HsNearbyObserver {
        private final List<String> events = new ArrayList<>();
        private final List<HsNearbyDevice> updated = new ArrayList<>();

        @Override
        public void onUpdated(List<HsNearbyDevice> devices) {
            events.add("updated");
            updated.addAll(devices);
        }

        @Override
        public void onLost(HsNearbyDevice device) {
            events.add("lost");
        }

        @Override
        public void onFailed(HsScanFailure failure) {
            events.add("failed");
        }

        @Override
        public void onFinished() {
            events.add("finished");
        }
    }

    private static Object readSubscription(HsNearbyScanner scanner) throws Exception {
        Field sessionField = HsNearbyScanner.class.getDeclaredField("scanSession");
        sessionField.setAccessible(true);
        Object scanSession = sessionField.get(scanner);
        Field subscription = scanSession.getClass().getDeclaredField("subscription");
        subscription.setAccessible(true);
        return subscription.get(scanSession);
    }

    private interface Subscribe {
        BleOperationHandle start(BleScanObserver observer);
    }

    private static YoBleClient proxy(Subscribe subscribe) {
        return (YoBleClient) Proxy.newProxyInstance(
                YoBleClient.class.getClassLoader(),
                new Class<?>[]{YoBleClient.class},
                (proxy, method, args) -> {
                    if ("subscribeScan".equals(method.getName())) {
                        return subscribe.start((BleScanObserver) args[0]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static BleScanResult frame(String mac, int rssi, List<UUID> uuids, byte[] raw, String name) {
        return new BleScanResult(
                new BleDeviceIdentity(new BleDeviceId(mac), name == null ? "" : name, 1000L),
                rssi,
                new BleScanRecord(-1, uuids, Collections.emptyList(), Collections.emptyMap(),
                        Collections.emptyMap(), Integer.MIN_VALUE, name, raw),
                1000L, true, true, BlePhy.LE_1M, BlePhy.UNKNOWN,
                BleScanResult.SID_NOT_PRESENT, BleScanResult.TX_POWER_NOT_PRESENT,
                BleScanResult.PERIODIC_INTERVAL_NOT_PRESENT, BleScanCallbackType.ALL_MATCHES);
    }

    private static UUID hs01Uuid(String featureCode) {
        byte[] prefix = new byte[9];
        for (int index = 0; index < prefix.length; index++) {
            prefix[index] = (byte) Integer.parseInt(featureCode.substring(index * 2, index * 2 + 2), 16);
        }
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
                (byte) 0xFF, (byte) 0xFF, 0x48, 0x43, 0x01, 0x02, 0x12,
                0x01, 0x00, 0x00, 0x00, 0x0A, 0x06,
                (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF,
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
}
