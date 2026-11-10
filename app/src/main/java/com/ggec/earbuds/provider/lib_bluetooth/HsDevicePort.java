package com.ggec.earbuds.provider.lib_bluetooth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DevicePort;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.yovo.lib_bluetooth.YoBluetoothSDK;
import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.api.YoCbtClient;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleDeviceSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanMode;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceSnapshot;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 接入 {@code lib_bluetooth} 与 {@code biz_ggec_hs} 的真实设备端口。
 *
 * <p>本轮先打通 HS01（旧 128-bit UUID 广播）与 HS02（新 0xFFFF "HC" 厂商广播）双代际同时扫描；
 * 使用 {@code legacyOnly(false)} 且不设硬件过滤，保证传统广播、扩展广播与扫描响应均能送达。
 * 底层 BLE 回调派发到独立后台线程（{@code callbackOnMainThread(false)}）并开启 200ms 批量聚合，
 * 避免密集广播洪峰阻塞主线程。</p>
 */
public final class HsDevicePort implements DevicePort {

    static final long SCAN_REPORT_DELAY_MILLIS = 200L;
    private static final long SCAN_TIMEOUT_MILLIS = 15_000L;

    private final YoBleClient bleClient;
    @Nullable
    private final YoCbtClient classicClient;
    private final HsBroadcastScanMapper scanMapper;
    private final CopyOnWriteArrayList<Consumer<DeviceSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();

    private volatile DeviceSnapshot currentSnapshot = DeviceSnapshot.idle();
    private long scanGeneration;
    private boolean scanning;
    @Nullable
    private BleOperationHandle activeScanHandle;
    @NonNull
    private Set<String> bondedAddresses = Collections.emptySet();

    public HsDevicePort(@NonNull Context context) {
        this(
                YoBluetoothSDK.ble(requireAppContext(context), defaultBleConfig()),
                YoBluetoothSDK.classic(requireAppContext(context)),
                new HsBroadcastScanMapper());
    }

    @NonNull
    static BleConfig defaultBleConfig() {
        return BleConfig.builder()
                .callbackOnMainThread(false)
                .build();
    }

    HsDevicePort(@NonNull YoBleClient bleClient, @NonNull HsBroadcastScanMapper scanMapper) {
        this(bleClient, null, scanMapper);
    }

    HsDevicePort(@NonNull YoBleClient bleClient,
                 @Nullable YoCbtClient classicClient,
                 @NonNull HsBroadcastScanMapper scanMapper) {
        this.bleClient = Objects.requireNonNull(bleClient, "bleClient");
        this.classicClient = classicClient;
        this.scanMapper = Objects.requireNonNull(scanMapper, "scanMapper");
    }

    @NonNull
    private static Context requireAppContext(@NonNull Context context) {
        Context app = Objects.requireNonNull(context, "context").getApplicationContext();
        return app != null ? app : context;
    }

    @Override
    public DeviceSnapshot snapshot() {
        return currentSnapshot;
    }

    @Override
    public Subscription observe(Consumer<DeviceSnapshot> listener) {
        if (listener == null) {
            return () -> {
            };
        }
        listeners.add(listener);
        listener.accept(currentSnapshot);
        return () -> listeners.remove(listener);
    }

    @Override
    public void connect(String modelId, String variantId, String mac) {
        // 协议连接与设备设置端口将在下一步按 HS01/HS02 会话接入。
    }

    @Override
    public void disconnect() {
        // 与 connect 成对预留，保持未连接空态。
    }

    @Override
    public void startScan(Consumer<ScanResult> onResult, Runnable onFinished) {
        startScan(onResult, null, onFinished);
    }

    @Override
    public void startScan(Consumer<ScanResult> onResult,
                          @Nullable Consumer<ScanFailure> onFailed,
                          Runnable onFinished) {
        startScanBatch(
                batch -> {
                    if (onResult != null) {
                        for (ScanResult item : batch) {
                            onResult.accept(item);
                        }
                    }
                },
                onFailed,
                onFinished);
    }

    @Override
    public void startScanBatch(Consumer<List<ScanResult>> onResults,
                               @Nullable Consumer<ScanFailure> onFailed,
                               Runnable onFinished) {
        Set<String> bonded = readBondedAddresses();
        final long generation;
        final BleOperationHandle previousHandle;
        synchronized (lock) {
            previousHandle = activeScanHandle;
            activeScanHandle = null;
            scanGeneration++;
            generation = scanGeneration;
            scanMapper.reset();
            bondedAddresses = bonded;
            scanning = true;
        }
        if (previousHandle != null) {
            previousHandle.cancel();
        }

        if (!bleClient.isBleSupported()) {
            synchronized (lock) {
                if (scanGeneration == generation) {
                    scanning = false;
                }
            }
            if (onFailed != null) {
                onFailed.accept(ScanFailure.BLE_UNSUPPORTED);
            }
            if (onFinished != null) {
                onFinished.run();
            }
            return;
        }
        if (!bleClient.isAdapterEnabled()) {
            synchronized (lock) {
                if (scanGeneration == generation) {
                    scanning = false;
                }
            }
            if (onFailed != null) {
                onFailed.accept(ScanFailure.BLUETOOTH_DISABLED);
            }
            if (onFinished != null) {
                onFinished.run();
            }
            return;
        }

        BleScanConfig config = BleScanConfig.builder()
                .scanMode(BleScanMode.LOW_LATENCY)
                .callbackType(BleScanCallbackType.ALL_MATCHES)
                .legacyOnly(false)
                .reportDelayMillis(SCAN_REPORT_DELAY_MILLIS)
                .deduplicationWindowMillis(0L)
                .timeoutMillis(SCAN_TIMEOUT_MILLIS)
                .build();

        AtomicBoolean finishedNotified = new AtomicBoolean(false);
        BleOperationHandle handle = bleClient.scan(config, new BleScanObserver() {
            @Override
            public void onDeviceFound(@NonNull BleScanResult result) {
                dispatchBatch(generation, Collections.singletonList(result), onResults);
            }

            @Override
            public void onBatchScanResults(@NonNull List<BleScanResult> results) {
                dispatchBatch(generation, results, onResults);
            }

            @Override
            public void onScanStopped() {
                finishGeneration(generation);
                if (finishedNotified.compareAndSet(false, true) && onFinished != null) {
                    onFinished.run();
                }
            }

            @Override
            public void onScanFailed(@NonNull BleFailure failure) {
                boolean active = finishGeneration(generation);
                if (active && onFailed != null) {
                    onFailed.accept(mapFailure(failure.errorCode()));
                }
                if (finishedNotified.compareAndSet(false, true) && onFinished != null) {
                    onFinished.run();
                }
            }
        });

        synchronized (lock) {
            if (scanGeneration == generation && scanning) {
                activeScanHandle = handle;
            } else {
                handle.cancel();
            }
        }
    }

    @Override
    public void stopScan() {
        BleOperationHandle handleToCancel;
        boolean wasScanning;
        synchronized (lock) {
            wasScanning = scanning;
            scanGeneration++;
            scanning = false;
            handleToCancel = activeScanHandle;
            activeScanHandle = null;
            scanMapper.reset();
        }
        if (handleToCancel != null) {
            handleToCancel.cancel();
        } else if (wasScanning && bleClient.isScanning()) {
            bleClient.stopScan();
        }
    }

    @Override
    public boolean isScanning() {
        synchronized (lock) {
            return scanning;
        }
    }

    private void dispatchBatch(long generation,
                               @NonNull List<BleScanResult> rawResults,
                               @Nullable Consumer<List<ScanResult>> onResults) {
        if (onResults == null || rawResults.isEmpty()) {
            return;
        }
        List<ScanResult> mappedBatch = null;
        synchronized (lock) {
            if (!scanning || scanGeneration != generation) {
                return;
            }
            for (BleScanResult rawResult : rawResults) {
                if (rawResult == null) {
                    continue;
                }
                ScanResult mapped = scanMapper.map(rawResult, bondedAddresses);
                if (mapped != null) {
                    if (mappedBatch == null) {
                        mappedBatch = new ArrayList<>();
                    }
                    mappedBatch.add(mapped);
                }
            }
        }
        if (mappedBatch != null && !mappedBatch.isEmpty()) {
            onResults.accept(Collections.unmodifiableList(mappedBatch));
        }
    }

    private boolean finishGeneration(long generation) {
        synchronized (lock) {
            if (scanGeneration != generation) {
                return false;
            }
            scanning = false;
            activeScanHandle = null;
            scanMapper.reset();
            return true;
        }
    }

    @NonNull
    private Set<String> readBondedAddresses() {
        Set<String> addresses = new HashSet<>();
        Result<List<BleDeviceSnapshot>> bleBonded = bleClient.getBondedDevices();
        List<BleDeviceSnapshot> bleDevices = bleBonded.getValue();
        if (bleBonded.isSuccess() && bleDevices != null) {
            for (BleDeviceSnapshot device : bleDevices) {
                if (device != null) {
                    String address = device.identity().deviceId().value();
                    if (!address.isEmpty()) {
                        addresses.add(address.toUpperCase(Locale.ROOT));
                    }
                }
            }
        }
        if (classicClient != null) {
            Result<List<CbtDeviceSnapshot>> cbtBonded = classicClient.getBondedDevices();
            List<CbtDeviceSnapshot> cbtDevices = cbtBonded.getValue();
            if (cbtBonded.isSuccess() && cbtDevices != null) {
                for (CbtDeviceSnapshot device : cbtDevices) {
                    if (device != null) {
                        String address = device.deviceId().value();
                        if (!address.isEmpty()) {
                            addresses.add(address.toUpperCase(Locale.ROOT));
                        }
                    }
                }
            }
        }
        return addresses.isEmpty() ? Collections.emptySet() : addresses;
    }

    @NonNull
    private static ScanFailure mapFailure(@NonNull BleErrorCode code) {
        return switch (code) {
            case BLUETOOTH_DISABLED -> ScanFailure.BLUETOOTH_DISABLED;
            case PERMISSION_SCAN_DENIED, PERMISSION_LOCATION_DENIED, PERMISSION_CONNECT_DENIED ->
                    ScanFailure.PERMISSION_DENIED;
            case LOCATION_SERVICES_DISABLED -> ScanFailure.LOCATION_DISABLED;
            case BLE_NOT_SUPPORTED, BLUETOOTH_NOT_SUPPORTED -> ScanFailure.BLE_UNSUPPORTED;
            case SCAN_THROTTLED -> ScanFailure.SCAN_THROTTLED;
            default -> ScanFailure.FAILED;
        };
    }
}
