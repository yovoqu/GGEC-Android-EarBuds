package com.yovo.lib_bluetooth.internal.ble.peripheral;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleTimelineRecorder;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleGattStatus;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoReleasable;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseConfig;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseData;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseObserver;
import com.yovo.lib_bluetooth.model.ble.BleCharacteristicScope;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattServerCharacteristic;
import com.yovo.lib_bluetooth.model.ble.BleGattServerConfig;
import com.yovo.lib_bluetooth.model.ble.BleGattServerDescriptor;
import com.yovo.lib_bluetooth.model.ble.BleGattServerObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattServerService;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BLE 外设广播（Legacy + Extended AdvertisingSet）与本地多客户端 GATT Server 托管管理器：
 * <ul>
 *   <li>完整检查 {@code BLUETOOTH_ADVERTISE} 与 {@code BLUETOOTH_CONNECT} 运行时权限；</li>
 *   <li>支持特征值 {@link BleCharacteristicScope#SHARED} 与 {@link BleCharacteristicScope#PER_CLIENT} 隔离；</li>
 *   <li>完整处理 {@code PreparedWrite} 偏移分块缓冲与 {@code executeWrite} 提交/中止；</li>
 *   <li>托管 CCCD 订阅状态并支持向指定中心设备下发 Notify / Indicate。</li>
 * </ul>
 */
public final class YoBlePeripheralManager implements YoReleasable {

    private static final String MODULE = "ble-peripheral";

    private static final class PreparedWriteOp {
        final boolean isDescriptor;
        @NonNull
        final BleGattTarget target;
        final int offset;
        @NonNull
        final byte[] data;

        PreparedWriteOp(boolean isDescriptor,
                        @NonNull BleGattTarget target,
                        int offset,
                        @NonNull byte[] data) {
            this.isDescriptor = isDescriptor;
            this.target = target;
            this.offset = offset;
            this.data = data.clone();
        }
    }

    private final YoBleRuntime runtime;
    private final YoBleCapabilityInspector capabilityInspector;
    private final YoBlePeripheralPlatform platform;
    private final YoBleTimelineRecorder timeline;
    private final AtomicLong seq = new AtomicLong();

    private volatile boolean advertising = false;
    private boolean advertisingOrStarting = false;
    private long advGen = 0L;
    @Nullable
    private BleAdvertiseObserver advertiseObserver;
    @Nullable
    private YoTimeoutHandle advertiseTimeoutHandle;

    private volatile boolean serverRunning = false;
    private boolean serverOpened = false;
    private long serverGen = 0L;
    @Nullable
    private BleGattServerConfig serverConfig;
    @Nullable
    private BleGattServerObserver serverObserver;

    // Key: (serviceUuid, characteristicUuid) without instanceId for canonical definition lookup
    private final Map<BleGattTarget, BleGattServerCharacteristic> charDefinitions = new LinkedHashMap<>();
    private final Map<BleGattTarget, BleGattServerDescriptor> descDefinitions = new LinkedHashMap<>();
    private final Map<BleGattTarget, byte[]> sharedCharValues = new LinkedHashMap<>();
    private final Map<BleDeviceId, Map<BleGattTarget, byte[]>> perClientCharValues = new LinkedHashMap<>();
    private final Map<BleGattTarget, byte[]> sharedDescValues = new LinkedHashMap<>();
    private final Map<BleDeviceId, Map<BleGattTarget, byte[]>> perClientDescValues = new LinkedHashMap<>();
    private final Map<BleDeviceId, List<PreparedWriteOp>> preparedWrites = new LinkedHashMap<>();

    private volatile boolean released = false;

    public YoBlePeripheralManager(@NonNull YoBleRuntime runtime,
                                  @NonNull YoBleCapabilityInspector capabilityInspector,
                                  @NonNull YoBlePeripheralPlatform platform,
                                  @NonNull YoBleTimelineRecorder timeline) {
        this.runtime = runtime;
        this.capabilityInspector = capabilityInspector;
        this.platform = platform;
        this.timeline = timeline;
    }

    // ── Advertising ─────────────────────────────────────────────────

    @NonNull
    public BleOperationHandle startAdvertising(@NonNull BleAdvertiseConfig config,
                                               @NonNull BleAdvertiseData advertiseData,
                                               @Nullable BleAdvertiseData scanResponse,
                                               @NonNull BleAdvertiseObserver observer) {
        long gen = seq.incrementAndGet();
        String opId = "ble-adv-" + gen;
        runtime.state(() -> startAdvertisingInternal(gen, config, advertiseData, scanResponse, observer));
        return new BleOperationHandle(opId, () -> runtime.state(() -> {
            if (advGen == gen) {
                stopAdvertisingInternal(true);
            }
        }));
    }

    @NonNull
    public Result<Void> stopAdvertising() {
        runtime.state(() -> stopAdvertisingInternal(true));
        return Result.success(null);
    }

    private void startAdvertisingInternal(long gen,
                                          @NonNull BleAdvertiseConfig config,
                                          @NonNull BleAdvertiseData advertiseData,
                                          @Nullable BleAdvertiseData scanResponse,
                                          @NonNull BleAdvertiseObserver observer) {
        if (released || runtime.isReleased()) {
            BleFailure f = YoBleErrorMapper.failure(BleErrorCode.RELEASED, MODULE, "Client released");
            runtime.callback(() -> observer.onAdvertisingFailed(f));
            return;
        }
        if (!capabilityInspector.isBleSupported()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.BLE_NOT_SUPPORTED, MODULE, "BLE not supported");
            runtime.callback(() -> observer.onAdvertisingFailed(f));
            return;
        }
        if (!capabilityInspector.isAdapterEnabled()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.BLUETOOTH_DISABLED, MODULE, "Bluetooth adapter disabled");
            runtime.callback(() -> observer.onAdvertisingFailed(f));
            return;
        }
        if (!capabilityInspector.hasAdvertisePermission()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.PERMISSION_ADVERTISE_DENIED, MODULE,
                    "Missing BLUETOOTH_ADVERTISE permission");
            runtime.callback(() -> observer.onAdvertisingFailed(f));
            return;
        }
        if (!config.legacyMode() && capabilityInspector.sdkInt() < 26) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.UNSUPPORTED_OPERATION, MODULE,
                    "Extended AdvertisingSet requires Android 8.0 (API 26+)");
            runtime.callback(() -> observer.onAdvertisingFailed(f));
            return;
        }

        stopAdvertisingInternal(false);
        this.advGen = gen;
        this.advertisingOrStarting = true;
        this.advertiseObserver = observer;

        String immediateError = platform.startAdvertising(config, advertiseData, scanResponse,
                new YoBlePeripheralPlatform.AdvertisePlatformCallback() {
                    @Override
                    public void onAdvertiseStarted() {
                        runtime.state(() -> {
                            if (released || advGen != gen) {
                                return;
                            }
                            advertising = true;
                            timeline.record(MODULE, "advertising started legacy=" + config.legacyMode());
                            runtime.callback(observer::onAdvertisingStarted);
                            if (config.timeoutMillis() > 0) {
                                advertiseTimeoutHandle = runtime.scheduler().schedule(
                                        () -> runtime.state(() -> {
                                            if (advGen == gen && advertising) {
                                                stopAdvertisingInternal(true);
                                            }
                                        }),
                                        config.timeoutMillis());
                            }
                        });
                    }

                    @Override
                    public void onAdvertiseFailed(int errorCode, @Nullable String detail) {
                        runtime.state(() -> {
                            if (advGen != gen) {
                                return;
                            }
                            advertising = false;
                            advertisingOrStarting = false;
                            advertiseObserver = null;
                            BleErrorCode mapped;
                            if (errorCode == 1 /* ADVERTISE_FAILED_DATA_TOO_LARGE */) {
                                mapped = BleErrorCode.ADVERTISE_DATA_TOO_LARGE;
                            } else if (errorCode == 5 /* ADVERTISE_FAILED_FEATURE_UNSUPPORTED */) {
                                mapped = BleErrorCode.UNSUPPORTED_OPERATION;
                            } else {
                                mapped = BleErrorCode.ADVERTISE_FAILED;
                            }
                            BleFailure f = YoBleErrorMapper.failure(
                                    mapped, MODULE,
                                    detail != null ? detail : ("Advertising failed: " + errorCode),
                                    null, errorCode, null, null, null);
                            timeline.record(MODULE, "advertising failed: " + f.errorCode());
                            runtime.callback(() -> observer.onAdvertisingFailed(f));
                        });
                    }

                    @Override
                    public void onAdvertiseStopped() {
                        runtime.state(() -> {
                            if (advGen == gen && advertising) {
                                stopAdvertisingInternal(true);
                            }
                        });
                    }
                });

        if (immediateError != null) {
            advertising = false;
            advertisingOrStarting = false;
            advertiseObserver = null;
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.ADVERTISE_FAILED, MODULE, immediateError);
            timeline.record(MODULE, "startAdvertising immediate error: " + immediateError);
            runtime.callback(() -> observer.onAdvertisingFailed(f));
        }
    }

    private void stopAdvertisingInternal(boolean notifyObserver) {
        if (advertiseTimeoutHandle != null) {
            advertiseTimeoutHandle.cancel();
            advertiseTimeoutHandle = null;
        }
        BleAdvertiseObserver obs = advertiseObserver;
        boolean wasActive = advertising;
        boolean wasOpened = advertisingOrStarting;
        advertising = false;
        advertisingOrStarting = false;
        advertiseObserver = null;
        if (wasOpened) {
            try {
                platform.stopAdvertising();
            } catch (Throwable ignored) {
            }
            if (wasActive) {
                timeline.record(MODULE, "advertising stopped");
                if (notifyObserver && obs != null) {
                    runtime.callback(obs::onAdvertisingStopped);
                }
            }
        }
    }

    // ── GATT Server ─────────────────────────────────────────────────

    @NonNull
    public BleOperationHandle startGattServer(@NonNull BleGattServerConfig config,
                                              @NonNull BleGattServerObserver observer) {
        long gen = seq.incrementAndGet();
        String opId = "ble-gatt-server-" + gen;
        runtime.state(() -> startGattServerInternal(gen, config, observer));
        return new BleOperationHandle(opId, () -> runtime.state(() -> {
            if (serverGen == gen) {
                stopGattServerInternal(true);
            }
        }));
    }

    @NonNull
    public Result<Void> notifyServerCharacteristic(@NonNull BleDeviceId clientDeviceId,
                                                   @NonNull BleGattTarget target,
                                                   boolean confirm,
                                                   @NonNull byte[] value) {
        if (released) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "Client released", clientDeviceId));
        }
        if (!BleDeviceId.isValid(clientDeviceId.value())) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.DEVICE_INVALID, MODULE, "Invalid client MAC: " + clientDeviceId, clientDeviceId));
        }
        if (!serverRunning) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.ILLEGAL_STATE, MODULE, "GATT Server is not running", clientDeviceId));
        }
        if (!capabilityInspector.hasConnectPermission()) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.PERMISSION_CONNECT_DENIED, MODULE,
                    "Missing BLUETOOTH_CONNECT permission", clientDeviceId));
        }
        byte[] copy = value.clone();
        boolean ok = platform.notifyCharacteristic(clientDeviceId, target, confirm, copy);
        if (!ok) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.GATT_SERVER_NOTIFY_FAILED, MODULE,
                    "notifyCharacteristicChanged returned false", clientDeviceId));
        }
        timeline.record(MODULE, "server notify " + clientDeviceId + " target=" + target
                + " " + YoBleTimelineRecorder.summarizeBytes(copy));
        return Result.success(null);
    }

    @NonNull
    public Result<Void> stopGattServer() {
        runtime.state(() -> stopGattServerInternal(true));
        return Result.success(null);
    }

    public void onAdapterTurnedOff() {
        runtime.state(() -> {
            stopAdvertisingInternal(true);
            stopGattServerInternal(true);
        });
    }

    @Override
    public void release() {
        released = true;
        runtime.state(() -> {
            stopAdvertisingInternal(false);
            stopGattServerInternal(false);
        });
    }

    private void startGattServerInternal(long gen,
                                         @NonNull BleGattServerConfig config,
                                         @NonNull BleGattServerObserver observer) {
        if (released || runtime.isReleased()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "Client released");
            runtime.callback(() -> observer.onFailed(f));
            return;
        }
        if (!capabilityInspector.isBleSupported()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.BLE_NOT_SUPPORTED, MODULE, "BLE not supported");
            runtime.callback(() -> observer.onFailed(f));
            return;
        }
        if (!capabilityInspector.isAdapterEnabled()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.BLUETOOTH_DISABLED, MODULE, "Bluetooth adapter disabled");
            runtime.callback(() -> observer.onFailed(f));
            return;
        }
        if (!capabilityInspector.hasConnectPermission()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.PERMISSION_CONNECT_DENIED, MODULE,
                    "Missing BLUETOOTH_CONNECT permission for GATT Server");
            runtime.callback(() -> observer.onFailed(f));
            return;
        }

        stopGattServerInternal(false);
        this.serverGen = gen;
        this.serverOpened = true;
        this.serverConfig = config;
        this.serverObserver = observer;
        initServerMetadata(config);

        int expectedServices = config.services().size();
        int[] addedServices = {0};

        String err = platform.startGattServer(config, new YoBlePeripheralPlatform.GattServerPlatformCallback() {
            @Override
            public void onServiceAdded(int status) {
                runtime.state(() -> {
                    if (released || serverGen != gen) {
                        return;
                    }
                    if (status != 0) {
                        BleFailure f = YoBleErrorMapper.fromGattStatus(
                                status, BleErrorCode.GATT_SERVER_START_FAILED,
                                MODULE, new BleDeviceId("00:00:00:00:00:00"), null, "addService");
                        stopGattServerInternal(false);
                        runtime.callback(() -> observer.onFailed(f));
                        return;
                    }
                    addedServices[0]++;
                    if (addedServices[0] >= expectedServices && !serverRunning) {
                        serverRunning = true;
                        timeline.record(MODULE, "GATT server started services=" + expectedServices);
                        runtime.callback(observer::onServerStarted);
                    }
                });
            }

            @Override
            public void onConnectionStateChange(@NonNull BleDeviceId clientDeviceId, boolean connected) {
                runtime.state(() -> {
                    if (released || serverGen != gen) {
                        return;
                    }
                    if (connected) {
                        perClientCharValues.computeIfAbsent(clientDeviceId, k -> new LinkedHashMap<>());
                        perClientDescValues.computeIfAbsent(clientDeviceId, k -> new LinkedHashMap<>());
                        timeline.record(MODULE, "GATT server client connected: " + clientDeviceId);
                        runtime.callback(() -> observer.onClientConnected(clientDeviceId));
                    } else {
                        perClientCharValues.remove(clientDeviceId);
                        perClientDescValues.remove(clientDeviceId);
                        preparedWrites.remove(clientDeviceId);
                        timeline.record(MODULE, "GATT server client disconnected: " + clientDeviceId);
                        runtime.callback(() -> observer.onClientDisconnected(clientDeviceId));
                    }
                });
            }

            @Override
            public void onCharacteristicReadRequest(@NonNull BleDeviceId clientDeviceId,
                                                    int requestId,
                                                    int offset,
                                                    @NonNull BleGattTarget target) {
                runtime.state(() -> handleCharReadRequest(
                        gen, clientDeviceId, requestId, offset, target, observer));
            }

            @Override
            public void onCharacteristicWriteRequest(@NonNull BleDeviceId clientDeviceId,
                                                     int requestId,
                                                     @NonNull BleGattTarget target,
                                                     boolean preparedWrite,
                                                     boolean responseNeeded,
                                                     int offset,
                                                     @NonNull byte[] value) {
                runtime.state(() -> handleCharWriteRequest(
                        gen, clientDeviceId, requestId, target,
                        preparedWrite, responseNeeded, offset, value, observer));
            }

            @Override
            public void onDescriptorReadRequest(@NonNull BleDeviceId clientDeviceId,
                                                int requestId,
                                                int offset,
                                                @NonNull BleGattTarget target) {
                runtime.state(() -> handleDescReadRequest(gen, clientDeviceId, requestId, offset, target));
            }

            @Override
            public void onDescriptorWriteRequest(@NonNull BleDeviceId clientDeviceId,
                                                 int requestId,
                                                 @NonNull BleGattTarget target,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset,
                                                 @NonNull byte[] value) {
                runtime.state(() -> handleDescWriteRequest(
                        gen, clientDeviceId, requestId, target,
                        preparedWrite, responseNeeded, offset, value, observer));
            }

            @Override
            public void onExecuteWrite(@NonNull BleDeviceId clientDeviceId, int requestId, boolean execute) {
                runtime.state(() -> handleExecuteWrite(gen, clientDeviceId, requestId, execute, observer));
            }

            @Override
            public void onNotificationSent(@NonNull BleDeviceId clientDeviceId, int status) {
                runtime.state(() -> {
                    if (serverGen == gen) {
                        runtime.callback(() -> observer.onNotificationSent(clientDeviceId, status));
                    }
                });
            }

            @Override
            public void onMtuChanged(@NonNull BleDeviceId clientDeviceId, int mtu) {
                runtime.state(() -> {
                    if (serverGen == gen) {
                        runtime.callback(() -> observer.onMtuChanged(clientDeviceId, mtu));
                    }
                });
            }
        });

        if (err != null) {
            stopGattServerInternal(false);
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.GATT_SERVER_START_FAILED, MODULE, err);
            runtime.callback(() -> observer.onFailed(f));
            return;
        }

        if (expectedServices == 0) {
            serverRunning = true;
            timeline.record(MODULE, "GATT server started with 0 services");
            runtime.callback(observer::onServerStarted);
        }
    }

    private void initServerMetadata(@NonNull BleGattServerConfig config) {
        charDefinitions.clear();
        descDefinitions.clear();
        sharedCharValues.clear();
        perClientCharValues.clear();
        sharedDescValues.clear();
        perClientDescValues.clear();
        preparedWrites.clear();

        for (BleGattServerService svc : config.services()) {
            for (BleGattServerCharacteristic ch : svc.characteristics()) {
                BleGattTarget charKey = BleGattTarget.characteristic(svc.uuid(), ch.uuid());
                charDefinitions.put(charKey, ch);
                sharedCharValues.put(charKey, ch.initialValue());
                for (BleGattServerDescriptor desc : ch.descriptors()) {
                    BleGattTarget descKey = BleGattTarget.descriptor(
                            svc.uuid(), ch.uuid(), desc.uuid());
                    descDefinitions.put(descKey, desc);
                    sharedDescValues.put(descKey, desc.initialValue());
                }
            }
        }
    }

    private void handleCharReadRequest(long gen,
                                       @NonNull BleDeviceId clientDeviceId,
                                       int requestId,
                                       int offset,
                                       @NonNull BleGattTarget rawTarget,
                                       @NonNull BleGattServerObserver observer) {
        if (released || serverGen != gen) {
            return;
        }
        BleGattTarget key = rawTarget.withoutDescriptor();
        byte[] current = readCharacteristicValue(clientDeviceId, key);
        if (current == null) {
            platform.sendResponse(clientDeviceId, requestId,
                    YoBleGattStatus.GATT_ATTRIBUTE_NOT_FOUND, 0, null);
            return;
        }
        if (offset < 0 || offset > current.length) {
            platform.sendResponse(clientDeviceId, requestId,
                    YoBleGattStatus.GATT_INVALID_OFFSET, 0, null);
            return;
        }
        byte[] slice = Arrays.copyOfRange(current, offset, current.length);
        platform.sendResponse(clientDeviceId, requestId, YoBleGattStatus.GATT_SUCCESS, offset, slice);
        byte[] copyCurrent = current.clone();
        runtime.callback(() -> observer.onClientReadCharacteristic(clientDeviceId, key, copyCurrent));
    }

    private void handleCharWriteRequest(long gen,
                                        @NonNull BleDeviceId clientDeviceId,
                                        int requestId,
                                        @NonNull BleGattTarget rawTarget,
                                        boolean preparedWrite,
                                        boolean responseNeeded,
                                        int offset,
                                        @NonNull byte[] value,
                                        @NonNull BleGattServerObserver observer) {
        if (released || serverGen != gen) {
            return;
        }
        BleGattTarget key = rawTarget.withoutDescriptor();
        if (!charDefinitions.containsKey(key)) {
            if (responseNeeded) {
                platform.sendResponse(clientDeviceId, requestId,
                        YoBleGattStatus.GATT_ATTRIBUTE_NOT_FOUND, 0, null);
            }
            return;
        }
        byte[] copy = value.clone();
        if (preparedWrite) {
            preparedWrites.computeIfAbsent(clientDeviceId, k -> new ArrayList<>())
                    .add(new PreparedWriteOp(false, key, offset, copy));
            if (responseNeeded) {
                platform.sendResponse(clientDeviceId, requestId,
                        YoBleGattStatus.GATT_SUCCESS, offset, copy);
            }
            return;
        }

        if (serverConfig == null || serverConfig.autoHandleClientValues()) {
            applyCharacteristicWrite(clientDeviceId, key, offset, copy);
        }
        if (responseNeeded) {
            platform.sendResponse(clientDeviceId, requestId,
                    YoBleGattStatus.GATT_SUCCESS, offset, copy);
        }
        runtime.callback(() -> observer.onClientWriteCharacteristic(
                clientDeviceId, key, copy, responseNeeded));
    }

    private void handleDescReadRequest(long gen,
                                       @NonNull BleDeviceId clientDeviceId,
                                       int requestId,
                                       int offset,
                                       @NonNull BleGattTarget rawTarget) {
        if (released || serverGen != gen) {
            return;
        }
        BleGattTarget key = normalizeDescKey(rawTarget);
        byte[] current = readDescriptorValue(clientDeviceId, key);
        if (current == null) {
            platform.sendResponse(clientDeviceId, requestId,
                    YoBleGattStatus.GATT_ATTRIBUTE_NOT_FOUND, 0, null);
            return;
        }
        if (offset < 0 || offset > current.length) {
            platform.sendResponse(clientDeviceId, requestId,
                    YoBleGattStatus.GATT_INVALID_OFFSET, 0, null);
            return;
        }
        byte[] slice = Arrays.copyOfRange(current, offset, current.length);
        platform.sendResponse(clientDeviceId, requestId, YoBleGattStatus.GATT_SUCCESS, offset, slice);
    }

    private void handleDescWriteRequest(long gen,
                                        @NonNull BleDeviceId clientDeviceId,
                                        int requestId,
                                        @NonNull BleGattTarget rawTarget,
                                        boolean preparedWrite,
                                        boolean responseNeeded,
                                        int offset,
                                        @NonNull byte[] value,
                                        @NonNull BleGattServerObserver observer) {
        if (released || serverGen != gen) {
            return;
        }
        BleGattTarget key = normalizeDescKey(rawTarget);
        byte[] copy = value.clone();
        if (preparedWrite) {
            preparedWrites.computeIfAbsent(clientDeviceId, k -> new ArrayList<>())
                    .add(new PreparedWriteOp(true, key, offset, copy));
            if (responseNeeded) {
                platform.sendResponse(clientDeviceId, requestId,
                        YoBleGattStatus.GATT_SUCCESS, offset, copy);
            }
            return;
        }
        applyDescriptorWrite(clientDeviceId, key, offset, copy, observer);
        if (responseNeeded) {
            platform.sendResponse(clientDeviceId, requestId,
                    YoBleGattStatus.GATT_SUCCESS, offset, copy);
        }
    }

    private void handleExecuteWrite(long gen,
                                    @NonNull BleDeviceId clientDeviceId,
                                    int requestId,
                                    boolean execute,
                                    @NonNull BleGattServerObserver observer) {
        if (released || serverGen != gen) {
            return;
        }
        List<PreparedWriteOp> pending = preparedWrites.remove(clientDeviceId);
        if (!execute || pending == null || pending.isEmpty()) {
            platform.sendResponse(clientDeviceId, requestId, YoBleGattStatus.GATT_SUCCESS, 0, null);
            return;
        }

        for (PreparedWriteOp op : pending) {
            if (op.isDescriptor) {
                applyDescriptorWrite(clientDeviceId, op.target, op.offset, op.data, observer);
            } else {
                if (serverConfig == null || serverConfig.autoHandleClientValues()) {
                    applyCharacteristicWrite(clientDeviceId, op.target, op.offset, op.data);
                }
                byte[] updated = readCharacteristicValue(clientDeviceId, op.target);
                byte[] emitted = updated != null ? updated.clone() : op.data.clone();
                runtime.callback(() -> observer.onClientWriteCharacteristic(
                        clientDeviceId, op.target, emitted, true));
            }
        }
        platform.sendResponse(clientDeviceId, requestId, YoBleGattStatus.GATT_SUCCESS, 0, null);
    }

    @Nullable
    private byte[] readCharacteristicValue(@NonNull BleDeviceId clientDeviceId,
                                           @NonNull BleGattTarget key) {
        BleGattServerCharacteristic def = charDefinitions.get(key);
        if (def == null) {
            return null;
        }
        if (def.scope() == BleCharacteristicScope.SHARED) {
            return sharedCharValues.getOrDefault(key, def.initialValue());
        }
        Map<BleGattTarget, byte[]> map = perClientCharValues.get(clientDeviceId);
        if (map != null && map.containsKey(key)) {
            return map.get(key);
        }
        return def.initialValue();
    }

    private void applyCharacteristicWrite(@NonNull BleDeviceId clientDeviceId,
                                          @NonNull BleGattTarget key,
                                          int offset,
                                          @NonNull byte[] value) {
        BleGattServerCharacteristic def = charDefinitions.get(key);
        if (def == null) {
            return;
        }
        byte[] existing = readCharacteristicValue(clientDeviceId, key);
        byte[] merged = patchBytes(existing != null ? existing : new byte[0], offset, value);
        if (def.scope() == BleCharacteristicScope.SHARED) {
            sharedCharValues.put(key, merged);
        } else {
            perClientCharValues.computeIfAbsent(clientDeviceId, k -> new LinkedHashMap<>())
                    .put(key, merged);
        }
    }

    @Nullable
    private byte[] readDescriptorValue(@NonNull BleDeviceId clientDeviceId,
                                       @NonNull BleGattTarget key) {
        BleGattServerDescriptor def = descDefinitions.get(key);
        if (def == null) {
            return null;
        }
        boolean isCccd = BleGattDatabaseSnapshot.CCCD_UUID.equals(key.descriptorUuid());
        BleGattServerCharacteristic parent = charDefinitions.get(key.withoutDescriptor());
        if (!isCccd && (parent == null || parent.scope() == BleCharacteristicScope.SHARED)) {
            return sharedDescValues.getOrDefault(key, def.initialValue());
        }
        Map<BleGattTarget, byte[]> map = perClientDescValues.get(clientDeviceId);
        if (map != null && map.containsKey(key)) {
            return map.get(key);
        }
        return def.initialValue();
    }

    private void applyDescriptorWrite(@NonNull BleDeviceId clientDeviceId,
                                      @NonNull BleGattTarget key,
                                      int offset,
                                      @NonNull byte[] value,
                                      @NonNull BleGattServerObserver observer) {
        byte[] existing = readDescriptorValue(clientDeviceId, key);
        byte[] merged = patchBytes(existing != null ? existing : new byte[0], offset, value);

        boolean isCccd = BleGattDatabaseSnapshot.CCCD_UUID.equals(key.descriptorUuid());
        BleGattServerCharacteristic parent = charDefinitions.get(key.withoutDescriptor());
        if (!isCccd && parent != null && parent.scope() == BleCharacteristicScope.SHARED) {
            sharedDescValues.put(key, merged);
        } else {
            perClientDescValues.computeIfAbsent(clientDeviceId, k -> new LinkedHashMap<>())
                    .put(key, merged);
        }

        if (isCccd && merged.length >= 2) {
            boolean notify = (merged[0] & 0x01) != 0;
            boolean indicate = (merged[0] & 0x02) != 0;
            BleGattTarget charTarget = key.withoutDescriptor();
            runtime.callback(() -> observer.onClientSubscriptionChanged(
                    clientDeviceId, charTarget, notify, indicate));
        }
    }

    @NonNull
    private static byte[] patchBytes(@NonNull byte[] existing, int offset, @NonNull byte[] chunk) {
        if (offset <= 0) {
            return chunk.clone();
        }
        int newLen = Math.max(existing.length, offset + chunk.length);
        byte[] out = Arrays.copyOf(existing, newLen);
        System.arraycopy(chunk, 0, out, offset, chunk.length);
        return out;
    }

    @NonNull
    private static BleGattTarget normalizeDescKey(@NonNull BleGattTarget raw) {
        UUID descUuid = raw.descriptorUuid();
        if (descUuid == null) {
            return BleGattTarget.characteristic(raw.serviceUuid(), raw.characteristicUuid());
        }
        return BleGattTarget.descriptor(raw.serviceUuid(), raw.characteristicUuid(), descUuid);
    }

    private void stopGattServerInternal(boolean notifyObserver) {
        BleGattServerObserver obs = serverObserver;
        boolean wasRunning = serverRunning;
        boolean wasOpened = serverOpened;
        serverRunning = false;
        serverOpened = false;
        serverConfig = null;
        serverObserver = null;
        charDefinitions.clear();
        descDefinitions.clear();
        sharedCharValues.clear();
        perClientCharValues.clear();
        sharedDescValues.clear();
        perClientDescValues.clear();
        preparedWrites.clear();
        if (wasOpened) {
            try {
                platform.stopGattServer();
            } catch (Throwable ignored) {
            }
            if (wasRunning) {
                timeline.record(MODULE, "GATT server stopped");
                if (notifyObserver && obs != null) {
                    runtime.callback(obs::onServerStopped);
                }
            }
        }
    }
}
