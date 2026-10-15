package com.yovo.lib_bluetooth.internal.ble;

import android.annotation.SuppressLint;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;
import com.yovo.lib_bluetooth.internal.model.ble.BleGattSessionState;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleConnectionState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BLE 客户端内部实现。
 */
@SuppressLint("MissingPermission")
public final class BleClientImpl implements YoBleClient {

    private static final String TAG = "BleClientImpl";
    private static final UUID CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final BleConfig config;
    private final YoBluetoothPermissionChecker permissionChecker =
            new YoBluetoothPermissionChecker();
    private final BleRuntime runtime = new BleRuntime();
    private final Map<BleDeviceId, Session> sessions = new LinkedHashMap<>();
    private final AtomicLong operationSeq = new AtomicLong();
    private ScanCallback scanCallback;
    private boolean released;

    public BleClientImpl(@NonNull Context context, @NonNull BleConfig config) {
        this.context = context.getApplicationContext();
        this.config = config;
    }

    @NonNull
    @Override
    public BleOperationHandle scan(@NonNull BleScanConfig scanConfig,
                                   @NonNull BleScanObserver observer) {
        String operationId = nextOperationId("ble-scan");
        YoBluetoothLogger.i(TAG, "开始 BLE 扫描，操作=" + operationId
                + "，超时=" + scanConfig.timeoutMillis() + "ms");
        runtime.io(() -> startScan(scanConfig, observer));
        return new BleOperationHandle(operationId, this::stopScan);
    }

    @NonNull
    @Override
    public Result<Void> stopScan() {
        BluetoothLeScanner scanner = scanner();
        ScanCallback callback = scanCallback;
        scanCallback = null;
        if (scanner == null || callback == null) {
            YoBluetoothLogger.d(TAG, "停止 BLE 扫描：没有活跃扫描");
            return Result.success(null);
        }
        try {
            scanner.stopScan(callback);
            YoBluetoothLogger.i(TAG, "BLE 扫描已停止");
            return Result.success(null);
        } catch (Throwable e) {
            YoBluetoothLogger.w(TAG, "停止 BLE 扫描失败", e);
            return Result.failure(failure(BleErrorCode.SCAN_STOP_FAILED,
                    "BLE stopScan failed", e, "ble-scan", null, null));
        }
    }

    @NonNull
    @Override
    public BleOperationHandle connect(@NonNull BleDeviceId deviceId,
                                      @NonNull BleConnectionConfig connectionConfig,
                                      @NonNull BleConnectionObserver observer) {
        String operationId = nextOperationId("ble-connect");
        YoBluetoothLogger.i(TAG, "开始 BLE 连接，设备=" + deviceId.value()
                + "，操作=" + operationId);
        runtime.io(() -> startConnect(deviceId, connectionConfig, observer));
        return new BleOperationHandle(operationId, () -> disconnect(deviceId));
    }

    @NonNull
    @Override
    public Result<Void> disconnect(@NonNull BleDeviceId deviceId) {
        Session session;
        synchronized (sessions) {
            session = sessions.remove(deviceId);
        }
        if (session == null) {
            YoBluetoothLogger.d(TAG, "断开 BLE 连接：设备无活跃会话 " + deviceId.value());
            return Result.success(null);
        }
        YoBluetoothLogger.i(TAG, "断开 BLE 连接，设备=" + deviceId.value());
        session.close(BleConnectionState.DISCONNECTED);
        return Result.success(null);
    }

    @NonNull
    @Override
    public Result<BleConnectionSnapshot> getConnectionSnapshot(@NonNull BleDeviceId deviceId) {
        Session session;
        synchronized (sessions) {
            session = sessions.get(deviceId);
        }
        if (session == null) {
            YoBluetoothLogger.w(TAG, "BLE 会话不存在，设备=" + deviceId.value());
            return Result.failure(failure(BleErrorCode.DEVICE_INVALID,
                    "BLE session not found", null, "ble-gatt", deviceId, null));
        }
        return Result.success(session.snapshot());
    }

    @NonNull
    @Override
    public BleOperationHandle read(@NonNull BleDeviceId deviceId,
                                   @NonNull BleGattTarget target,
                                   @NonNull BleGattObserver observer) {
        String operationId = nextOperationId("ble-read");
        runtime.io(() -> startRead(deviceId, target, observer, operationId));
        return new BleOperationHandle(operationId, () -> { });
    }

    @NonNull
    @Override
    public BleOperationHandle write(@NonNull BleDeviceId deviceId,
                                    @NonNull BleGattTarget target,
                                    @NonNull byte[] value,
                                    @NonNull BleWriteType writeType,
                                    @NonNull BleGattObserver observer) {
        String operationId = nextOperationId("ble-write");
        runtime.io(() -> startWrite(deviceId, target, value, writeType, observer, operationId));
        return new BleOperationHandle(operationId, () -> { });
    }

    @NonNull
    @Override
    public BleOperationHandle setNotify(@NonNull BleDeviceId deviceId,
                                        @NonNull BleGattTarget target,
                                        boolean enabled,
                                        @NonNull BleGattObserver observer) {
        String operationId = nextOperationId("ble-notify");
        runtime.io(() -> startNotify(deviceId, target, enabled, observer, operationId));
        return new BleOperationHandle(operationId, () -> { });
    }

    @Override
    public void release() {
        if (released) {
            return;
        }
        released = true;
        YoBluetoothLogger.i(TAG, "正在释放 BLE 客户端");
        stopScan();
        synchronized (sessions) {
            for (Session session : sessions.values()) {
                session.close(BleConnectionState.DISCONNECTED);
            }
            sessions.clear();
        }
        runtime.release();
        YoBluetoothLogger.i(TAG, "BLE 客户端释放完成");
    }

    private void startScan(BleScanConfig scanConfig, BleScanObserver observer) {
        if (!permissionChecker.has(context, YoBluetoothPermission.SCAN)) {
            YoBluetoothLogger.w(TAG, "开始 BLE 扫描失败：缺少扫描权限");
            runtime.callback(() -> observer.onScanFailed(failure(
                    BleErrorCode.PERMISSION_SCAN_DENIED,
                    "BLE scan permission denied", null, "ble-scan", null, null)));
            return;
        }
        BluetoothLeScanner scanner = scanner();
        if (scanner == null) {
            YoBluetoothLogger.w(TAG, "开始 BLE 扫描失败：扫描器不可用");
            runtime.callback(() -> observer.onScanFailed(failure(
                    BleErrorCode.BLUETOOTH_NOT_SUPPORTED,
                    "BLE scanner unavailable", null, "ble-scan", null, null)));
            return;
        }
        stopScan();
        ScanCallback callback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result == null ? null : result.getDevice();
                BleDeviceIdentity identity = identity(device);
                if (identity == null) {
                    return;
                }
                if (scanConfig.requireConnectable()
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        && !result.isConnectable()) {
                    return;
                }
                runtime.callback(() -> observer.onDeviceFound(
                        new BleScanResult(identity, result.getRssi(), System.currentTimeMillis())));
            }

            @Override
            public void onScanFailed(int errorCode) {
                YoBluetoothLogger.w(TAG, "BLE 扫描失败，错误码=" + errorCode);
                runtime.callback(() -> observer.onScanFailed(failure(
                        BleErrorCode.SCAN_START_FAILED,
                        "BLE scan failed: " + errorCode, null, "ble-scan", null, null)));
            }
        };
        try {
            scanCallback = callback;
            scanner.startScan(callback);
            YoBluetoothLogger.i(TAG, "BLE 扫描已启动");
            runtime.callback(observer::onScanStarted);
            runtime.scheduler().schedule(() -> {
                stopScan();
                runtime.callback(observer::onScanStopped);
            }, scanConfig.timeoutMillis());
        } catch (Throwable e) {
            scanCallback = null;
            YoBluetoothLogger.e(TAG, "启动 BLE 扫描异常", e);
            runtime.callback(() -> observer.onScanFailed(failure(
                    BleErrorCode.SCAN_START_FAILED,
                    "BLE startScan failed", e, "ble-scan", null, null)));
        }
    }

    private void startRead(BleDeviceId deviceId,
                           BleGattTarget target,
                           BleGattObserver observer,
                           String operationId) {
        Session session = session(deviceId);
        if (session == null) {
            runtime.callback(() -> observer.onFailed(failure(BleErrorCode.DEVICE_INVALID,
                    "BLE session not found", null, "ble-gatt", deviceId, target)));
            return;
        }
        session.read(target, observer, operationId);
    }

    private void startWrite(BleDeviceId deviceId,
                            BleGattTarget target,
                            byte[] value,
                            BleWriteType writeType,
                            BleGattObserver observer,
                            String operationId) {
        Session session = session(deviceId);
        if (session == null) {
            runtime.callback(() -> observer.onFailed(failure(BleErrorCode.DEVICE_INVALID,
                    "BLE session not found", null, "ble-gatt", deviceId, target)));
            return;
        }
        session.write(target, value, writeType, observer, operationId);
    }

    private void startNotify(BleDeviceId deviceId,
                             BleGattTarget target,
                             boolean enabled,
                             BleGattObserver observer,
                             String operationId) {
        Session session = session(deviceId);
        if (session == null) {
            runtime.callback(() -> observer.onFailed(failure(BleErrorCode.DEVICE_INVALID,
                    "BLE session not found", null, "ble-gatt", deviceId, target)));
            return;
        }
        session.setNotify(target, enabled, observer, operationId);
    }

    private void startConnect(BleDeviceId deviceId,
                              BleConnectionConfig connectionConfig,
                              BleConnectionObserver observer) {
        if (!permissionChecker.has(context, YoBluetoothPermission.CONNECT)) {
            YoBluetoothLogger.w(TAG, "BLE 连接失败：缺少连接权限，设备=" + deviceId.value());
            runtime.callback(() -> observer.onConnectionFailed(failure(
                    BleErrorCode.PERMISSION_CONNECT_DENIED,
                    "BLE connect permission denied", null, "ble-gatt", deviceId, null)));
            return;
        }
        BluetoothAdapter adapter = adapter();
        if (adapter == null) {
            YoBluetoothLogger.w(TAG, "BLE 连接失败：蓝牙适配器不可用，设备=" + deviceId.value());
            runtime.callback(() -> observer.onConnectionFailed(failure(
                    BleErrorCode.BLUETOOTH_NOT_SUPPORTED,
                    "Bluetooth adapter unavailable", null, "ble-gatt", deviceId, null)));
            return;
        }
        if (!adapter.isEnabled()) {
            YoBluetoothLogger.w(TAG, "BLE 连接失败：蓝牙未开启，设备=" + deviceId.value());
            runtime.callback(() -> observer.onConnectionFailed(failure(
                    BleErrorCode.BLUETOOTH_DISABLED,
                    "Bluetooth is disabled", null, "ble-gatt", deviceId, null)));
            return;
        }
        try {
            BluetoothDevice device = adapter.getRemoteDevice(deviceId.value());
            Session session = new Session(deviceId, observer, connectionConfig.operationTimeoutMillis());
            synchronized (sessions) {
                Session old = sessions.put(deviceId, session);
                if (old != null) {
                    old.close(BleConnectionState.DISCONNECTED);
                }
            }
            session.transition(BleConnectionState.CONNECTING);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                session.gatt = device.connectGatt(context, connectionConfig.autoConnect(),
                        session.callback, BluetoothDevice.TRANSPORT_LE);
            } else {
                session.gatt = device.connectGatt(context, connectionConfig.autoConnect(),
                        session.callback);
            }
            if (session.gatt == null) {
                YoBluetoothLogger.e(TAG, "BLE connectGatt 返回 null，设备=" + deviceId.value());
                session.fail(BleErrorCode.CONNECT_FAILED, "connectGatt returned null", null);
            } else {
                YoBluetoothLogger.i(TAG, "BLE connectGatt 已创建，设备=" + deviceId.value());
                session.armConnectTimeout();
            }
        } catch (Throwable e) {
            YoBluetoothLogger.e(TAG, "启动 BLE 连接异常，设备=" + deviceId.value(), e);
            runtime.callback(() -> observer.onConnectionFailed(failure(
                    BleErrorCode.DEVICE_INVALID,
                    "Invalid BLE device", e, "ble-gatt", deviceId, null)));
        }
    }

    @Nullable
    private Session session(BleDeviceId deviceId) {
        synchronized (sessions) {
            return sessions.get(deviceId);
        }
    }

    @Nullable
    private BluetoothAdapter adapter() {
        BluetoothManager manager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    @Nullable
    private BluetoothLeScanner scanner() {
        BluetoothAdapter adapter = adapter();
        return adapter == null || !adapter.isEnabled() ? null : adapter.getBluetoothLeScanner();
    }

    @Nullable
    private static BleDeviceIdentity identity(@Nullable BluetoothDevice device) {
        if (device == null) {
            return null;
        }
        String address = device.getAddress();
        if (address == null || address.trim().isEmpty()) {
            return null;
        }
        String name = device.getName();
        return new BleDeviceIdentity(
                new BleDeviceId(address),
                name == null ? "" : name,
                System.currentTimeMillis());
    }

    private String nextOperationId(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + operationSeq.incrementAndGet();
    }

    @NonNull
    private static BleFailure failure(BleErrorCode code,
                                      String message,
                                      Throwable cause,
                                      String sourceModule,
                                      @Nullable BleDeviceId deviceId,
                                      @Nullable BleGattTarget target) {
        return new BleFailure(
                code,
                message,
                cause,
                true,
                false,
                sourceModule,
                deviceId,
                target == null ? null : target.serviceUuid(),
                target == null ? null : target.characteristicUuid());
    }

    private final class Session {

        private enum GattOpKind {
            READ, WRITE, NOTIFY
        }

        private final class PendingGattOp {
            @NonNull
            final GattOpKind kind;
            @NonNull
            final BleGattTarget target;
            @NonNull
            final BleGattObserver observer;
            final boolean notifyEnabled;
            @Nullable
            YoTimeoutHandle timeout;

            PendingGattOp(@NonNull GattOpKind kind,
                          @NonNull BleGattTarget target,
                          @NonNull BleGattObserver observer,
                          boolean notifyEnabled) {
                this.kind = kind;
                this.target = target;
                this.observer = observer;
                this.notifyEnabled = notifyEnabled;
            }
        }

        @NonNull
        private final BleDeviceId deviceId;
        @NonNull
        private final BleConnectionObserver observer;
        @NonNull
        private final BleGattSessionState state;
        private final long operationTimeoutMillis;
        @Nullable
        private BluetoothGatt gatt;
        @Nullable
        private YoTimeoutHandle connectTimeout;
        @Nullable
        private PendingGattOp pendingGattOp;
        private boolean closed;

        private final BluetoothGattCallback callback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    YoBluetoothLogger.e(TAG, "BLE 连接状态异常，设备=" + deviceId.value()
                            + "，状态码=" + status);
                    fail(BleErrorCode.CONNECT_FAILED,
                            "BLE connection status failed: " + status, null);
                    return;
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    YoBluetoothLogger.i(TAG, "BLE 已连接，设备=" + deviceId.value());
                    transition(BleConnectionState.CONNECTED);
                    transition(BleConnectionState.DISCOVERING_SERVICES);
                    if (!gatt.discoverServices()) {
                        YoBluetoothLogger.e(TAG, "BLE discoverServices 返回 false，设备=" + deviceId.value());
                        fail(BleErrorCode.SERVICE_DISCOVERY_FAILED,
                                "discoverServices returned false", null);
                    }
                    return;
                }
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    YoBluetoothLogger.i(TAG, "BLE 已断开，设备=" + deviceId.value());
                    close(BleConnectionState.DISCONNECTED);
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    YoBluetoothLogger.i(TAG, "BLE 服务发现完成，设备=" + deviceId.value());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        gatt.requestMtu(config.defaultMtu());
                    } else {
                        markReady();
                    }
                } else {
                    YoBluetoothLogger.e(TAG, "BLE 服务发现失败，设备=" + deviceId.value()
                            + "，状态码=" + status);
                    fail(BleErrorCode.SERVICE_DISCOVERY_FAILED,
                            "BLE service discovery status failed: " + status, null);
                }
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                YoBluetoothLogger.d(TAG, "BLE MTU 变更，设备=" + deviceId.value()
                        + "，MTU=" + mtu + "，状态码=" + status);
                markReady();
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt,
                                              BluetoothGattCharacteristic characteristic,
                                              int status) {
                PendingGattOp pending = takePendingIfMatches(GattOpKind.READ, characteristic);
                if (pending == null) {
                    return;
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    YoBluetoothLogger.w(TAG, "BLE 特征读取失败，设备=" + deviceId.value()
                            + "，状态码=" + status);
                    completeGattFailure(pending, BleErrorCode.READ_FAILED,
                            "BLE characteristic read failed: " + status);
                    return;
                }
                byte[] value = copyValue(characteristic);
                runtime.callback(() -> pending.observer.onRead(deviceId, pending.target, value));
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt,
                                               BluetoothGattCharacteristic characteristic,
                                               int status) {
                PendingGattOp pending = takePendingIfMatches(GattOpKind.WRITE, characteristic);
                if (pending == null) {
                    return;
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    YoBluetoothLogger.w(TAG, "BLE 特征写入失败，设备=" + deviceId.value()
                            + "，状态码=" + status);
                    completeGattFailure(pending, BleErrorCode.WRITE_FAILED,
                            "BLE characteristic write failed: " + status);
                    return;
                }
                runtime.callback(() -> pending.observer.onWrite(deviceId, pending.target));
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt,
                                           BluetoothGattDescriptor descriptor,
                                           int status) {
                PendingGattOp pending = takePendingIfMatches(GattOpKind.NOTIFY,
                        descriptor == null ? null : descriptor.getCharacteristic());
                if (pending == null) {
                    return;
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    YoBluetoothLogger.w(TAG, "BLE 描述符写入失败，设备=" + deviceId.value()
                            + "，状态码=" + status);
                    completeGattFailure(pending, BleErrorCode.NOTIFY_FAILED,
                            "BLE descriptor write failed: " + status);
                    return;
                }
                runtime.callback(() -> pending.observer.onNotifyChanged(
                        deviceId, pending.target, pending.notifyEnabled));
            }
        };

        Session(@NonNull BleDeviceId deviceId,
                @NonNull BleConnectionObserver observer,
                long operationTimeoutMillis) {
            this.deviceId = deviceId;
            this.observer = observer;
            this.state = new BleGattSessionState(deviceId);
            this.operationTimeoutMillis = operationTimeoutMillis;
        }

        void armConnectTimeout() {
            cancelConnectTimeout();
            connectTimeout = runtime.scheduler().schedule(() -> {
                if (closed || state.state() == BleConnectionState.READY) {
                    return;
                }
                fail(BleErrorCode.GATT_OPERATION_TIMEOUT, "BLE connect timeout", null);
            }, operationTimeoutMillis);
        }

        void read(@NonNull BleGattTarget target,
                  @NonNull BleGattObserver observer,
                  @NonNull String operationId) {
            BluetoothGattCharacteristic characteristic = beginGatt(
                    GattOpKind.READ, target, observer, false);
            if (characteristic == null) {
                return;
            }
            YoBluetoothLogger.i(TAG, "读取 BLE 特征，设备=" + deviceId.value()
                    + "，目标=" + target + "，操作=" + operationId);
            try {
                if (!gatt.readCharacteristic(characteristic)) {
                    failPending(BleErrorCode.READ_FAILED, "readCharacteristic returned false");
                }
            } catch (Throwable e) {
                failPending(BleErrorCode.READ_FAILED, "BLE read failed", e);
            }
        }

        void write(@NonNull BleGattTarget target,
                   @NonNull byte[] value,
                   @NonNull BleWriteType writeType,
                   @NonNull BleGattObserver observer,
                   @NonNull String operationId) {
            BluetoothGattCharacteristic characteristic = beginGatt(
                    GattOpKind.WRITE, target, observer, false);
            if (characteristic == null) {
                return;
            }
            YoBluetoothLogger.i(TAG, "写入 BLE 特征，设备=" + deviceId.value()
                    + "，目标=" + target + "，长度=" + value.length + "，操作=" + operationId);
            try {
                characteristic.setWriteType(writeType == BleWriteType.WITHOUT_RESPONSE
                        ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                characteristic.setValue(value);
                if (!gatt.writeCharacteristic(characteristic)) {
                    failPending(BleErrorCode.WRITE_FAILED, "writeCharacteristic returned false");
                }
            } catch (Throwable e) {
                failPending(BleErrorCode.WRITE_FAILED, "BLE write failed", e);
            }
        }

        void setNotify(@NonNull BleGattTarget target,
                       boolean enabled,
                       @NonNull BleGattObserver observer,
                       @NonNull String operationId) {
            BluetoothGattCharacteristic characteristic = beginGatt(
                    GattOpKind.NOTIFY, target, observer, enabled);
            if (characteristic == null) {
                return;
            }
            YoBluetoothLogger.i(TAG, "设置 BLE 通知，设备=" + deviceId.value()
                    + "，目标=" + target + "，启用=" + enabled + "，操作=" + operationId);
            try {
                if (!gatt.setCharacteristicNotification(characteristic, enabled)) {
                    failPending(BleErrorCode.NOTIFY_FAILED,
                            "setCharacteristicNotification returned false");
                    return;
                }
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
                if (descriptor == null) {
                    PendingGattOp pending = takePending();
                    if (pending != null) {
                        runtime.callback(() -> pending.observer.onNotifyChanged(
                                deviceId, pending.target, enabled));
                    }
                    return;
                }
                descriptor.setValue(enabled
                        ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                if (!gatt.writeDescriptor(descriptor)) {
                    failPending(BleErrorCode.NOTIFY_FAILED, "writeDescriptor returned false");
                }
            } catch (Throwable e) {
                failPending(BleErrorCode.NOTIFY_FAILED, "BLE notify failed", e);
            }
        }

        void transition(BleConnectionState next) {
            state.transition(next);
            YoBluetoothLogger.d(TAG, "BLE 状态变更，设备=" + deviceId.value() + "，状态=" + next);
            runtime.callback(() -> observer.onConnectionStateChanged(state.snapshot()));
        }

        void fail(BleErrorCode code, String message, Throwable cause) {
            YoBluetoothLogger.e(TAG, "BLE 会话失败，设备=" + deviceId.value()
                    + "，错误码=" + code + "，信息=" + message, cause);
            if (!closed) {
                transition(BleConnectionState.FAILED);
                BleFailure failure = failure(code, message, cause, "ble-gatt", deviceId, null);
                runtime.callback(() -> observer.onConnectionFailed(failure));
            }
            close(BleConnectionState.FAILED);
        }

        void close(BleConnectionState finalState) {
            if (closed) {
                return;
            }
            closed = true;
            cancelConnectTimeout();
            failPending(BleErrorCode.DEVICE_INVALID, "BLE session closed");
            synchronized (sessions) {
                sessions.remove(deviceId, this);
            }
            YoBluetoothLogger.d(TAG, "关闭 BLE 会话，设备=" + deviceId.value()
                    + "，终态=" + finalState);
            if (state.state() != BleConnectionState.DISCONNECTING
                    && state.state() != BleConnectionState.DISCONNECTED
                    && state.state() != BleConnectionState.FAILED) {
                transition(BleConnectionState.DISCONNECTING);
            }
            BluetoothGatt target = gatt;
            gatt = null;
            if (target != null) {
                try {
                    target.disconnect();
                } catch (Throwable ignored) {
                }
                try {
                    target.close();
                } catch (Throwable ignored) {
                }
            }
            if (state.state() != finalState) {
                transition(finalState);
            }
        }

        @NonNull
        BleConnectionSnapshot snapshot() {
            return state.snapshot();
        }

        private void markReady() {
            cancelConnectTimeout();
            transition(BleConnectionState.READY);
        }

        @Nullable
        private BluetoothGattCharacteristic beginGatt(@NonNull GattOpKind kind,
                                                      @NonNull BleGattTarget target,
                                                      @NonNull BleGattObserver observer,
                                                      boolean notifyEnabled) {
            if (closed || gatt == null || state.state() != BleConnectionState.READY) {
                runtime.callback(() -> observer.onFailed(failure(BleErrorCode.DEVICE_INVALID,
                        "BLE GATT is not ready", null, "ble-gatt", deviceId, target)));
                return null;
            }
            if (pendingGattOp != null) {
                runtime.callback(() -> observer.onFailed(failure(BleErrorCode.GATT_BUSY,
                        "BLE GATT operation already in progress", null, "ble-gatt", deviceId, target)));
                return null;
            }
            BluetoothGattCharacteristic characteristic = characteristic(target);
            if (characteristic == null) {
                runtime.callback(() -> observer.onFailed(failure(BleErrorCode.CHARACTERISTIC_NOT_FOUND,
                        "BLE characteristic not found", null, "ble-gatt", deviceId, target)));
                return null;
            }
            PendingGattOp pending = new PendingGattOp(kind, target, observer, notifyEnabled);
            pendingGattOp = pending;
            pending.timeout = runtime.scheduler().schedule(() -> {
                PendingGattOp timedOut = takePending();
                if (timedOut == null) {
                    return;
                }
                completeGattFailure(timedOut, BleErrorCode.GATT_OPERATION_TIMEOUT,
                        "BLE GATT operation timeout");
            }, operationTimeoutMillis);
            return characteristic;
        }

        @Nullable
        private BluetoothGattCharacteristic characteristic(@NonNull BleGattTarget target) {
            BluetoothGatt localGatt = gatt;
            if (localGatt == null) {
                return null;
            }
            BluetoothGattService service = localGatt.getService(target.serviceUuid());
            return service == null ? null
                    : service.getCharacteristic(target.characteristicUuid());
        }

        @Nullable
        private PendingGattOp takePendingIfMatches(@NonNull GattOpKind kind,
                                                   @Nullable BluetoothGattCharacteristic characteristic) {
            PendingGattOp pending = pendingGattOp;
            if (pending == null || pending.kind != kind || !matches(pending.target, characteristic)) {
                return null;
            }
            return takePending();
        }

        @Nullable
        private PendingGattOp takePending() {
            PendingGattOp pending = pendingGattOp;
            pendingGattOp = null;
            if (pending != null && pending.timeout != null) {
                pending.timeout.cancel();
                pending.timeout = null;
            }
            return pending;
        }

        private void failPending(@NonNull BleErrorCode code, @NonNull String message) {
            failPending(code, message, null);
        }

        private void failPending(@NonNull BleErrorCode code,
                                 @NonNull String message,
                                 @Nullable Throwable cause) {
            PendingGattOp pending = takePending();
            if (pending == null) {
                return;
            }
            BleFailure failure = new BleFailure(
                    code, message, cause, true, false, "ble-gatt", deviceId,
                    pending.target.serviceUuid(), pending.target.characteristicUuid());
            runtime.callback(() -> pending.observer.onFailed(failure));
        }

        private void completeGattFailure(@NonNull PendingGattOp pending,
                                         @NonNull BleErrorCode code,
                                         @NonNull String message) {
            BleFailure failure = failure(code, message, null, "ble-gatt", deviceId, pending.target);
            runtime.callback(() -> pending.observer.onFailed(failure));
        }

        private void cancelConnectTimeout() {
            YoTimeoutHandle handle = connectTimeout;
            connectTimeout = null;
            if (handle != null) {
                handle.cancel();
            }
        }

        private static boolean matches(@NonNull BleGattTarget target,
                                       @Nullable BluetoothGattCharacteristic characteristic) {
            return characteristic != null
                    && target.characteristicUuid().equals(characteristic.getUuid())
                    && characteristic.getService() != null
                    && target.serviceUuid().equals(characteristic.getService().getUuid());
        }

        @NonNull
        private static byte[] copyValue(@NonNull BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            return value == null ? new byte[0] : Arrays.copyOf(value, value.length);
        }
    }
}
