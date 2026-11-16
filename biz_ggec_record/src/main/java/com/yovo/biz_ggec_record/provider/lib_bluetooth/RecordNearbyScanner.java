package com.yovo.biz_ggec_record.provider.lib_bluetooth;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_record.api.RecordNearbyObserver;
import com.yovo.biz_ggec_record.api.YoRecordNearby;
import com.yovo.biz_ggec_record.model.RecordNearbyDevice;
import com.yovo.biz_ggec_record.model.RecordScanFailure;
import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 用 {@link YoBleClient#subscribeScan} 扫描附近 BLE 设备，投影为 {@link RecordNearbyDevice}。
 * Phase 1：无厂商过滤；广播名含 dabaicai 的优先，其余按 RSSI。
 */
public final class RecordNearbyScanner implements YoRecordNearby {

    private static final String PREFERRED_NAME_HINT = "dabaicai";

    private final YoBleClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final Map<String, RecordNearbyDevice> devices = new LinkedHashMap<>();

    @Nullable
    private BleOperationHandle scanHandle;
    @Nullable
    private RecordNearbyObserver observer;

    public RecordNearbyScanner(@NonNull YoBleClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public void start(@Nullable RecordNearbyObserver observer) {
        stop();
        this.observer = observer;
        synchronized (lock) {
            devices.clear();
        }
        if (!client.isBleSupported()) {
            dispatchFailure(RecordScanFailure.BLE_UNSUPPORTED);
            return;
        }
        if (!client.isAdapterEnabled()) {
            dispatchFailure(RecordScanFailure.BLUETOOTH_DISABLED);
            return;
        }
        scanHandle = client.subscribeScan(new BleScanObserver() {
            @Override
            public void onDeviceFound(@NonNull BleScanResult result) {
                upsert(result);
            }

            @Override
            public void onScanFailed(@NonNull BleFailure failure) {
                dispatchFailure(mapFailure(failure.errorCode()));
            }
        });
    }

    @Override
    public void stop() {
        BleOperationHandle handle;
        synchronized (lock) {
            handle = scanHandle;
            scanHandle = null;
            devices.clear();
        }
        if (handle != null) {
            handle.cancel();
        }
        observer = null;
    }

    private void upsert(@NonNull BleScanResult result) {
        String mac = result.identity().deviceId().value();
        if (mac == null || mac.isEmpty()) {
            return;
        }
        String name = result.identity().advertisingName();
        if (name == null) {
            name = "";
        }
        RecordNearbyDevice device = new RecordNearbyDevice(mac, name, result.rssi());
        List<RecordNearbyDevice> snapshotList;
        synchronized (lock) {
            devices.put(mac.toUpperCase(Locale.US), device);
            snapshotList = sortedCopy();
        }
        RecordNearbyObserver current = observer;
        if (current != null) {
            mainHandler.post(() -> current.onDevicesChanged(snapshotList));
        }
    }

    @NonNull
    private List<RecordNearbyDevice> sortedCopy() {
        List<RecordNearbyDevice> list = new ArrayList<>(devices.values());
        list.sort(Comparator
                .comparing((RecordNearbyDevice d) -> !d.name().toLowerCase(Locale.US)
                        .contains(PREFERRED_NAME_HINT))
                .thenComparingInt(RecordNearbyDevice::rssi).reversed());
        return list;
    }

    private void dispatchFailure(@NonNull RecordScanFailure failure) {
        RecordNearbyObserver current = observer;
        if (current != null) {
            mainHandler.post(() -> current.onScanFailed(failure));
        }
    }

    @NonNull
    private static RecordScanFailure mapFailure(@Nullable BleErrorCode errorCode) {
        if (errorCode == null) {
            return RecordScanFailure.FAILED;
        }
        return switch (errorCode) {
            case BLUETOOTH_DISABLED -> RecordScanFailure.BLUETOOTH_DISABLED;
            case PERMISSION_SCAN_DENIED, PERMISSION_CONNECT_DENIED, PERMISSION_ADVERTISE_DENIED,
                 PERMISSION_LOCATION_DENIED -> RecordScanFailure.PERMISSION_DENIED;
            case BLE_NOT_SUPPORTED, BLUETOOTH_NOT_SUPPORTED, SCAN_FEATURE_UNSUPPORTED ->
                    RecordScanFailure.BLE_UNSUPPORTED;
            case SCAN_THROTTLED -> RecordScanFailure.THROTTLED;
            default -> RecordScanFailure.FAILED;
        };
    }
}
