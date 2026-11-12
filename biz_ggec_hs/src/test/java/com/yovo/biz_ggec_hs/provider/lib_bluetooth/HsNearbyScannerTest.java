package com.yovo.biz_ggec_hs.provider.lib_bluetooth;


import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.internal.center.port.ScanPace;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.internal.center.port.HsBleScanLink;
import com.yovo.biz_ggec_hs.internal.center.port.HsRawAdvert;
import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.api.BleScanFrameFilter;
import com.yovo.lib_bluetooth.api.BleScanHealth;
import com.yovo.lib_bluetooth.api.BleScanPlan;
import com.yovo.lib_bluetooth.api.BleScanSubscription;
import com.yovo.lib_bluetooth.model.ble.BleAdapterState;
import com.yovo.lib_bluetooth.model.common.Result;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanMode;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanRecord;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 传输层适配：只做「订阅 → 原始广播」和「库错误码 → 失败分类」，不判断产品、不组装产品语义。
 * 同时钉住订阅生命周期：同步失败不留泄漏、旧订阅回调不串台、失败/停止各只收尾一次。
 */
public final class HsNearbyScannerTest {

    @Test
    public void forwardsRawAdvertsWithoutAnyProductJudgement() {
        AtomicReference<BleScanObserver> captured = new AtomicReference<>();
        HsNearbyScanner scanner = new HsNearbyScanner(proxy(observer -> {
            captured.set(observer);
            return subscription(() -> {
            }, new AtomicReference<>());
        }));
        RecordingObserver observer = new RecordingObserver();
        scanner.start(observer);

        captured.get().onBatchScanResults(List.of(
                frame("11:22:33:44:55:01", -60, List.of(UUID.randomUUID()),
                        new byte[]{0x02, 0x01, 0x06}, "HS01-CN"),
                frame("99:88:77:66:55:44", -70, Collections.emptyList(),
                        new byte[0], "Other")));

        assertEquals(1, observer.batches.size());
        List<HsRawAdvert> batch = observer.batches.get(0);
        assertEquals("无关广播也要原样上交：识别不是传输层的事", 2, batch.size());
        HsRawAdvert first = batch.get(0);
        assertEquals("11:22:33:44:55:01", first.address());
        assertEquals("HS01-CN", first.name());
        assertEquals(-60, first.rssi());
        assertEquals(1, first.serviceUuids().size());
        assertEquals(3, first.rawAdvertisingView().length);
    }

    @Test
    public void fallsBackToAdvertisingNameWhenRecordNameIsBlank() {
        AtomicReference<BleScanObserver> captured = new AtomicReference<>();
        HsNearbyScanner scanner = new HsNearbyScanner(proxy(observer -> {
            captured.set(observer);
            return subscription(() -> {
            }, new AtomicReference<>());
        }));
        RecordingObserver observer = new RecordingObserver();
        scanner.start(observer);

        // 扫描记录里没有名称，只有广播名：仍要带出名称。
        captured.get().onDeviceFound(frameWithNames("11:22:33:44:55:02", -48,
                Collections.emptyList(), nameField("Vidda V11"), null, "Vidda V11"));

        assertEquals(1, observer.batches.size());
        assertEquals("Vidda V11", observer.batches.get(0).get(0).name());
    }

    @Test
    public void forwardsLostByAddress() {
        AtomicReference<BleScanObserver> captured = new AtomicReference<>();
        HsNearbyScanner scanner = new HsNearbyScanner(proxy(observer -> {
            captured.set(observer);
            return subscription(() -> {
            }, new AtomicReference<>());
        }));
        RecordingObserver observer = new RecordingObserver();
        scanner.start(observer);

        captured.get().onScanResultLost(frame("11:22:33:44:55:02", -48,
                Collections.emptyList(), new byte[0], "Vidda"));
        assertEquals(List.of("11:22:33:44:55:02"), observer.lost);
    }

    @Test
    public void mapsFailureAndCancelsItsSubscription() throws Exception {
        AtomicReference<BleScanObserver> captured = new AtomicReference<>();
        AtomicInteger cancellations = new AtomicInteger();
        HsNearbyScanner scanner = new HsNearbyScanner(proxy(observer -> {
            captured.set(observer);
            return subscription(cancellations::incrementAndGet, new AtomicReference<>());
        }));
        RecordingObserver observer = new RecordingObserver();
        scanner.start(observer);
        assertTrue(scanner.isScanning());

        captured.get().onScanFailed(new BleFailure(
                BleErrorCode.SCAN_THROTTLED, "throttled", null, false, true, "test",
                null, null, null));

        assertFalse(scanner.isScanning());
        assertEquals(List.of(HsControl.Failure.SCAN_THROTTLED), observer.failures);
        assertEquals("失败必须取消订阅", 1, cancellations.get());
        assertNull(readSubscription(scanner));
    }

    @Test
    public void synchronousFailureLeavesNoLeakedSubscription() throws Exception {
        AtomicInteger cancellations = new AtomicInteger();
        HsNearbyScanner scanner = new HsNearbyScanner(proxy(observer -> {
            observer.onScanFailed(new BleFailure(
                    BleErrorCode.BLUETOOTH_DISABLED, "off", null, true, true, "test",
                    null, null, null));
            return subscription(cancellations::incrementAndGet, new AtomicReference<>());
        }));
        RecordingObserver observer = new RecordingObserver();
        scanner.start(observer);

        assertEquals(List.of(HsControl.Failure.BLUETOOTH_DISABLED), observer.failures);
        assertFalse(scanner.isScanning());
        assertEquals(1, cancellations.get());
        assertNull(readSubscription(scanner));
    }

    @Test
    public void forwardsStoppedOnce() {
        AtomicReference<BleScanObserver> captured = new AtomicReference<>();
        HsNearbyScanner scanner = new HsNearbyScanner(proxy(observer -> {
            captured.set(observer);
            return subscription(() -> {
            }, new AtomicReference<>());
        }));
        RecordingObserver observer = new RecordingObserver();
        scanner.start(observer);

        captured.get().onScanStopped();
        assertEquals(1, observer.stops);
        assertFalse(scanner.isScanning());

        // 已结束的订阅再报停止：属于旧代次，不得重复上报。
        captured.get().onScanStopped();
        assertEquals(1, observer.stops);
    }

    @Test
    public void callbacksFromPreviousSubscriptionAreDropped() {
        List<BleScanObserver> captured = new ArrayList<>();
        HsNearbyScanner scanner = new HsNearbyScanner(proxy(observer -> {
            captured.add(observer);
            return subscription(() -> {
            }, new AtomicReference<>());
        }));
        RecordingObserver previous = new RecordingObserver();
        scanner.start(previous);
        BleScanObserver previousCallbacks = captured.get(0);

        RecordingObserver current = new RecordingObserver();
        scanner.start(current);
        BleScanObserver currentCallbacks = captured.get(1);

        BleScanResult frame = frame("11:22:33:44:55:02", -48, Collections.emptyList(),
                new byte[0], "Vidda");
        previousCallbacks.onDeviceFound(frame);
        previousCallbacks.onScanResultLost(frame);
        previousCallbacks.onScanStopped();

        assertTrue("旧订阅的帧不得上台", previous.batches.isEmpty());
        assertTrue(previous.lost.isEmpty());
        assertEquals(0, previous.stops);
        assertTrue(scanner.isScanning());

        currentCallbacks.onDeviceFound(frame);
        assertEquals(1, current.batches.size());
    }

    /** 机制层两条热路径：帧级过滤器随订阅生效、档位只在变化时重扫一次。 */
    @Test
    public void appliesFrameFilterAndScanPlanThroughTheMechanismLayer() {
        List<BleScanPlan> reconfigured = new ArrayList<>();
        AtomicReference<BleScanFrameFilter> filter = new AtomicReference<>();
        HsNearbyScanner scanner = new HsNearbyScanner(proxy(
                observer -> subscription(() -> {
                }, filter), reconfigured));

        scanner.applyScanProfile(ScanPace.lowPower());
        scanner.setFramePredicate(advert -> advert.rssi() >= -50);

        RecordingObserver observer = new RecordingObserver();
        scanner.start(observer);

        assertEquals("档位变更必须走 lib 的唯一重扫入口", 1, reconfigured.size());
        assertEquals(BleScanMode.LOW_POWER, reconfigured.get(0).mode());
        assertNotNull("订阅时必须带上当前谓词", filter.get());
        assertTrue(filter.get().matches(frame("11:22:33:44:55:01", -40,
                Collections.emptyList(), new byte[0], "buds")));
        assertFalse(filter.get().matches(frame("11:22:33:44:55:01", -60,
                Collections.emptyList(), new byte[0], "buds")));

        // 同档位再次 start 不得重复重扫（30 秒窗口内的启动次数是硬约束）。
        scanner.start(observer);
        assertEquals(1, reconfigured.size());
    }

    private static Object readSubscription(HsNearbyScanner scanner) throws Exception {
        Field handle = HsNearbyScanner.class.getDeclaredField("handle");
        handle.setAccessible(true);
        return handle.get(scanner);
    }

    private interface Subscribe {
        BleScanSubscription start(BleScanObserver observer);
    }

    private static YoBleClient proxy(Subscribe subscribe) {
        return proxy(subscribe, null);
    }

    private static YoBleClient proxy(Subscribe subscribe, List<BleScanPlan> reconfigured) {
        return (YoBleClient) Proxy.newProxyInstance(
                YoBleClient.class.getClassLoader(),
                new Class<?>[]{YoBleClient.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "subscribeScan":
                            return subscribe.start((BleScanObserver) args[0]);
                        case "scanHealth":
                            return new BleScanHealth(false, 0L, 0, 0L, BleAdapterState.UNKNOWN);
                        case "reconfigureScan":
                            if (reconfigured != null) {
                                reconfigured.add((BleScanPlan) args[0]);
                            }
                            return Result.success(null);
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private static BleScanSubscription subscription(Runnable cancel,
                                                    AtomicReference<BleScanFrameFilter> filter) {
        return new BleScanSubscription("sub", cancel) {
            @Override
            public void setFrameFilter(BleScanFrameFilter value) {
                filter.set(value);
            }
        };
    }

    private static BleScanResult frame(String mac, int rssi, List<UUID> uuids,
                                       byte[] raw, String name) {
        return frameWithNames(mac, rssi, uuids, raw, name, name);
    }

    private static BleScanResult frameWithNames(String mac, int rssi, List<UUID> uuids,
                                                byte[] raw, String recordName,
                                                String advertisingName) {
        return new BleScanResult(
                new BleDeviceIdentity(new BleDeviceId(mac),
                        advertisingName == null ? "" : advertisingName, 1000L),
                rssi,
                new BleScanRecord(-1, uuids, Collections.emptyList(), Collections.emptyMap(),
                        Collections.emptyMap(), Integer.MIN_VALUE, recordName, raw),
                1000L, true, true, BlePhy.LE_1M, BlePhy.UNKNOWN,
                BleScanResult.SID_NOT_PRESENT, BleScanResult.TX_POWER_NOT_PRESENT,
                BleScanResult.PERIODIC_INTERVAL_NOT_PRESENT, BleScanCallbackType.ALL_MATCHES);
    }

    private static byte[] nameField(String name) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] field = new byte[nameBytes.length + 2];
        field[0] = (byte) (nameBytes.length + 1);
        field[1] = 0x09;
        System.arraycopy(nameBytes, 0, field, 2, nameBytes.length);
        return field;
    }

    private static final class RecordingObserver implements HsBleScanLink.Observer {
        private final List<List<HsRawAdvert>> batches = new ArrayList<>();
        private final List<String> lost = new ArrayList<>();
        private final List<HsControl.Failure> failures = new ArrayList<>();
        private int stops;

        @Override
        public void onBatch(List<HsRawAdvert> adverts) {
            batches.add(adverts);
        }

        @Override
        public void onLost(String address) {
            lost.add(address);
        }

        @Override
        public void onFailed(HsControl.Failure failure) {
            failures.add(failure);
        }

        @Override
        public void onStopped() {
            stops++;
        }
    }
}
