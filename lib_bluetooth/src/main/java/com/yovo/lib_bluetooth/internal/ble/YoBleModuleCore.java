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
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleDiagnosticReport;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * BLE 纯领域模块核心装配图（平台无关：既可用于 Android 生产图，也可直接注入 Fake 平台进行全链路 JVM 单测）。
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
        return capabilityInspector.isAdapterEnabled();
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
                connectionManager::pendingCountAtSnapshotIndex,
                timeline);
        return Result.success(builder.buildReport());
    }

    public void onAdapterTurnedOff() {
        if (released) {
            return;
        }
        timeline.record(MODULE, "system BluetoothAdapter turned OFF -> cleaning BLE subsystems");
        scanController.onAdapterTurnedOff();
        connectionManager.onAdapterTurnedOff();
        peripheralManager.onAdapterTurnedOff();
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
