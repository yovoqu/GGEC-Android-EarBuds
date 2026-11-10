package com.yovo.lib_bluetooth.internal.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.channel.YoBleL2capManager;
import com.yovo.lib_bluetooth.internal.ble.channel.YoBleL2capPlatform;
import com.yovo.lib_bluetooth.internal.ble.connection.YoBleConnectionManager;
import com.yovo.lib_bluetooth.internal.ble.connection.YoBleConnectionPlatform;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleDumpBuilder;
import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleTimelineRecorder;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.pairing.YoBleBondController;
import com.yovo.lib_bluetooth.internal.ble.pairing.YoBleBondPlatform;
import com.yovo.lib_bluetooth.internal.ble.peripheral.YoBlePeripheralManager;
import com.yovo.lib_bluetooth.internal.ble.peripheral.YoBlePeripheralPlatform;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleReleaseCoordinator;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntimeConfig;
import com.yovo.lib_bluetooth.internal.ble.scan.YoBleScanController;
import com.yovo.lib_bluetooth.internal.ble.scan.YoBleScanPlatform;
import com.yovo.lib_bluetooth.model.ble.BleAdapterState;
import com.yovo.lib_bluetooth.model.ble.BleAdapterStateObserver;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleDeviceSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleDiagnosticReport;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * BLE 核心装配图：串联扫描、配对、连接、GATT、L2CAP 与外设广播/GATT Server 等正式运行时组件。
 */
public final class YoBleModuleCore {

    private static final String MODULE = "ble-core";

    public final BleConfig config;
    public final YoBleRuntime runtime;
    public final YoBleCapabilityInspector capabilityInspector;
    public final YoBleDeviceSnapshotStore snapshotStore;
    public final YoBleTimelineRecorder timeline;
    public final LongSupplier clockMillis;
    public final YoBleReleaseCoordinator releaseCoordinator = new YoBleReleaseCoordinator();

    public final YoBleScanController scanController;
    public final YoBleBondController bondController;
    public final YoBleConnectionManager connectionManager;
    public final YoBleL2capManager l2capManager;
    public final YoBlePeripheralManager peripheralManager;

    private final CopyOnWriteArrayList<BleAdapterStateObserver> adapterObservers = new CopyOnWriteArrayList<>();
    private final AtomicLong adapterObserverSeq = new AtomicLong();
    private volatile BleAdapterState adapterState;
    private volatile boolean released = false;

    public YoBleModuleCore(@NonNull BleConfig config,
                            @NonNull YoBleRuntimeConfig runtimeConfig,
                            @NonNull YoBleCapabilityInspector capabilityInspector,
                            @NonNull YoBleScanPlatform scanPlatform,
                            @NonNull YoBleConnectionPlatform connectionPlatform,
                            @NonNull YoBleBondPlatform bondPlatform,
                            @NonNull YoBleL2capPlatform l2capPlatform,
                            @NonNull YoBlePeripheralPlatform peripheralPlatform,
                            @NonNull LongSupplier clockMillis) {
        this.config = config;
        this.runtime = new YoBleRuntime(runtimeConfig);
        this.capabilityInspector = capabilityInspector;
        this.clockMillis = clockMillis;
        this.adapterState = capabilityInspector.adapterState();
        this.snapshotStore = new YoBleDeviceSnapshotStore();
        this.timeline = new YoBleTimelineRecorder(config.diagnosticsTimelineCapacity());

        this.scanController = new YoBleScanController(
                runtime, config, capabilityInspector, scanPlatform, snapshotStore, timeline, clockMillis);
        this.bondController = new YoBleBondController(
                runtime, config, capabilityInspector, bondPlatform, snapshotStore, timeline, clockMillis);
        this.connectionManager = new YoBleConnectionManager(
                runtime, config, capabilityInspector, connectionPlatform, bondController,
                snapshotStore, timeline, clockMillis);
        this.l2capManager = new YoBleL2capManager(
                runtime, capabilityInspector, l2capPlatform, timeline);
        this.peripheralManager = new YoBlePeripheralManager(
                runtime, capabilityInspector, peripheralPlatform, timeline);

        releaseCoordinator.register(scanController);
        releaseCoordinator.register(connectionManager);
        releaseCoordinator.register(l2capManager);
        releaseCoordinator.register(peripheralManager);
        releaseCoordinator.register(bondController);
        releaseCoordinator.register(snapshotStore::clear);
    }

    public boolean isBleSupported() {
        return capabilityInspector.isBleSupported();
    }

    public boolean isAdapterEnabled() {
        return getAdapterState() == BleAdapterState.ON;
    }

    @NonNull
    public BleAdapterState getAdapterState() {
        BleAdapterState published = adapterState;
        return published == null ? BleAdapterState.UNKNOWN : published;
    }

    @NonNull
    public BleOperationHandle observeAdapterState(@NonNull BleAdapterStateObserver observer) {
        String opId = "ble-adapter-" + adapterObserverSeq.incrementAndGet();
        adapterObservers.add(observer);
        BleAdapterState current = getAdapterState();
        runtime.callback(() -> observer.onAdapterStateChanged(current, current));
        return new BleOperationHandle(opId, () -> adapterObservers.remove(observer));
    }

    @NonNull
    public Result<BleDeviceSnapshot> getDeviceSnapshot(@NonNull BleDeviceId deviceId) {
        if (released) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "YoBleClient released", deviceId));
        }
        if (!BleDeviceId.isValid(deviceId.value())) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.DEVICE_INVALID, MODULE, "Invalid MAC address: " + deviceId, deviceId));
        }
        BleDeviceSnapshot snap = snapshotStore.get(deviceId);
        if (snap == null) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.DEVICE_INVALID, MODULE,
                    "No snapshot recorded for device " + deviceId, deviceId));
        }
        return Result.success(snap);
    }

    @NonNull
    public Result<List<BleDeviceSnapshot>> getDeviceSnapshots() {
        if (released) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "YoBleClient released"));
        }
        return Result.success(snapshotStore.all());
    }

    @NonNull
    public Result<BleDiagnosticReport> exportDiagnosticReport() {
        if (released) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "YoBleClient released"));
        }
        YoBleDumpBuilder builder = new YoBleDumpBuilder(
                capabilityInspector.isBleSupported(),
                capabilityInspector.isAdapterEnabled(),
                scanController.isScanning(),
                clockMillis.getAsLong(),
                connectionManager.exportActiveSnapshots(),
                connectionManager::pendingCount,
                timeline);
        return Result.success(builder.buildReport());
    }

    public void onAdapterStateChanged(@NonNull BleAdapterState current) {
        if (released) {
            return;
        }
        BleAdapterState previous = getAdapterState();
        adapterState = current;
        timeline.record(MODULE, "adapter state " + previous + " -> " + current);
        if (current == BleAdapterState.OFF || current == BleAdapterState.TURNING_OFF) {
            scanController.onAdapterTurnedOff();
            connectionManager.onAdapterTurnedOff();
            bondController.onAdapterTurnedOff();
            l2capManager.onAdapterTurnedOff();
            peripheralManager.onAdapterTurnedOff();
        }
        for (BleAdapterStateObserver observer : adapterObservers) {
            runtime.callback(() -> observer.onAdapterStateChanged(previous, current));
        }
    }

    public void onBondStateChanged(@NonNull BleDeviceId deviceId,
                                   @NonNull com.yovo.lib_bluetooth.model.ble.BleBondState previous,
                                   @NonNull com.yovo.lib_bluetooth.model.ble.BleBondState current,
                                   int rawReason) {
        if (released) {
            return;
        }
        bondController.onPlatformBondStateChanged(deviceId, previous, current, rawReason);
        connectionManager.onBondStateChanged(deviceId, current);
    }

    public void release(@Nullable Runnable platformTeardownHook) {
        if (released) {
            return;
        }
        released = true;
        timeline.record(MODULE, "releasing YoBleClient");
        if (platformTeardownHook != null) {
            try {
                platformTeardownHook.run();
            } catch (Throwable ignored) {
            }
        }
        releaseCoordinator.releaseAll();
        runtime.release();
    }
}
