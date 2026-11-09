package com.yovo.lib_bluetooth.internal.ble.testing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.connection.YoBleConnectionPlatform;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattTransport;
import com.yovo.lib_bluetooth.model.ble.BleConnectionPriority;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattServiceInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BlePhyConfig;
import com.yovo.lib_bluetooth.model.ble.BlePhyOptions;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * 虚拟远端 BLE 外设仿真替身：模拟完整 GATT 数据库、多实例特征、MTU/PHY/Priority 回调、
 * HCI 错误注入、ReliableWrite 回显、ServiceChanged 与丢回调坑位行为。
 */
public final class FakeBleGattDevice {

    public interface WriteHook {
        /** @return 若返回非空 int 数组则替代正常 status 回调（可用于自定义 status 或先发 notify 再完成写）。 */
        void onWrite(@NonNull BleGattTarget target,
                     @NonNull byte[] value,
                     @NonNull BleWriteType writeType,
                     @NonNull FakeBleGattDevice device);
    }

    public static final class RecordedWrite {
        public final BleGattTarget target;
        public final byte[] value;
        public final BleWriteType writeType;

        RecordedWrite(BleGattTarget target, byte[] value, BleWriteType writeType) {
            this.target = target;
            this.value = value.clone();
            this.writeType = writeType;
        }
    }

    private final BleDeviceId deviceId;
    private final List<BleGattServiceInfo> services = new ArrayList<>();
    private final Map<BleGattTarget, byte[]> storage = new LinkedHashMap<>();
    private final Queue<Integer> scriptedConnectStatuses = new ArrayDeque<>();
    private final Queue<Integer> scriptedWriteStatuses = new ArrayDeque<>();
    private final Queue<Integer> scriptedReadStatuses = new ArrayDeque<>();

    private boolean manualConnectMode = false;
    private boolean autoDiscoverSuccess = true;
    private int discoverFailureStatus = 0;
    private boolean discoverReturnsFalse = false;
    private boolean refreshCacheResult = true;
    private boolean readReturnsFalse = false;
    private boolean writeReturnsFalse = false;
    private boolean readDescReturnsFalse = false;
    private boolean writeDescReturnsFalse = false;
    private int defaultDescWriteStatus = 0;
    private int defaultDescReadStatus = 0;

    private int maxSupportedMtu = 247;
    private int mtuFailureStatus = 0;
    private boolean mtuReturnsFalse = false;
    private boolean suppressConnectionPriorityCallback = false;
    private boolean suppressSetPhyCallback = false;
    private BlePhyConfig remotePhy = BlePhyConfig.le1m();
    private int readPhyStatus = 0;
    private int remoteRssi = -55;
    private int rssiStatus = 0;

    private boolean reliableCorruptEcho = false;
    private int reliableExecuteStatus = 0;
    private boolean deferOperationCallbacks = false;

    @Nullable
    private WriteHook writeHook;

    @Nullable
    private YoBleConnectionPlatform.PlatformListener activeListener;
    private boolean closed = false;

    private int connectAttempts = 0;
    private int disconnectCalls = 0;
    private int closeCalls = 0;
    private int discoverCalls = 0;
    private int refreshCacheCalls = 0;
    private int readPhyCalls = 0;
    private int executeReliableCalls = 0;
    private int abortReliableCalls = 0;

    private final List<BleGattTarget> setNotificationTargets = new ArrayList<>();
    private final List<RecordedWrite> charWrites = new ArrayList<>();
    private final List<RecordedWrite> descWrites = new ArrayList<>();

    public FakeBleGattDevice(@NonNull BleDeviceId deviceId) {
        this.deviceId = deviceId;
    }

    // ── 配置 DSL ──────────────────────────────────────────────────

    public FakeBleGattDevice setServices(@NonNull List<BleGattServiceInfo> list) {
        services.clear();
        services.addAll(list);
        return this;
    }

    public FakeBleGattDevice putValue(@NonNull BleGattTarget target, @NonNull byte[] value) {
        storage.put(target, value.clone());
        return this;
    }

    @Nullable
    public byte[] getStoredValue(@NonNull BleGattTarget target) {
        byte[] v = storage.get(target);
        return v == null ? null : v.clone();
    }

    public FakeBleGattDevice enqueueConnectStatus(int status) {
        scriptedConnectStatuses.add(status);
        return this;
    }

    public FakeBleGattDevice enqueueWriteStatus(int status) {
        scriptedWriteStatuses.add(status);
        return this;
    }

    public FakeBleGattDevice enqueueReadStatus(int status) {
        scriptedReadStatuses.add(status);
        return this;
    }

    public FakeBleGattDevice setManualConnectMode(boolean manualConnectMode) {
        this.manualConnectMode = manualConnectMode;
        return this;
    }

    public FakeBleGattDevice setDiscoverFailure(boolean returnsFalse, int status) {
        this.discoverReturnsFalse = returnsFalse;
        this.discoverFailureStatus = status;
        this.autoDiscoverSuccess = !returnsFalse && status == 0;
        return this;
    }

    public FakeBleGattDevice setRefreshCacheResult(boolean refreshCacheResult) {
        this.refreshCacheResult = refreshCacheResult;
        return this;
    }

    public FakeBleGattDevice setReadReturnsFalse(boolean readReturnsFalse) {
        this.readReturnsFalse = readReturnsFalse;
        return this;
    }

    public FakeBleGattDevice setWriteReturnsFalse(boolean writeReturnsFalse) {
        this.writeReturnsFalse = writeReturnsFalse;
        return this;
    }

    public FakeBleGattDevice setDefaultDescWriteStatus(int defaultDescWriteStatus) {
        this.defaultDescWriteStatus = defaultDescWriteStatus;
        return this;
    }

    public FakeBleGattDevice setDefaultDescReadStatus(int defaultDescReadStatus) {
        this.defaultDescReadStatus = defaultDescReadStatus;
        return this;
    }

    public FakeBleGattDevice setMaxSupportedMtu(int maxSupportedMtu) {
        this.maxSupportedMtu = maxSupportedMtu;
        return this;
    }

    public FakeBleGattDevice setMtuFailure(boolean returnsFalse, int status) {
        this.mtuReturnsFalse = returnsFalse;
        this.mtuFailureStatus = status;
        return this;
    }

    public FakeBleGattDevice setSuppressConnectionPriorityCallback(boolean suppress) {
        this.suppressConnectionPriorityCallback = suppress;
        return this;
    }

    public FakeBleGattDevice setSuppressSetPhyCallback(boolean suppress) {
        this.suppressSetPhyCallback = suppress;
        return this;
    }

    public FakeBleGattDevice setRemotePhy(@NonNull BlePhyConfig remotePhy) {
        this.remotePhy = remotePhy;
        return this;
    }

    public FakeBleGattDevice setRemoteRssi(int remoteRssi) {
        this.remoteRssi = remoteRssi;
        return this;
    }

    public FakeBleGattDevice setReliableCorruptEcho(boolean reliableCorruptEcho) {
        this.reliableCorruptEcho = reliableCorruptEcho;
        return this;
    }

    public FakeBleGattDevice setReliableExecuteStatus(int reliableExecuteStatus) {
        this.reliableExecuteStatus = reliableExecuteStatus;
        return this;
    }

    public FakeBleGattDevice setDeferOperationCallbacks(boolean deferOperationCallbacks) {
        this.deferOperationCallbacks = deferOperationCallbacks;
        return this;
    }

    public FakeBleGattDevice setWriteHook(@Nullable WriteHook writeHook) {
        this.writeHook = writeHook;
        return this;
    }

    // ── 外部触发远端事件 ──────────────────────────────────────────

    public void simulateConnectComplete(int status) {
        if (activeListener != null && !closed) {
            int newState = status == 0
                    ? YoBleConnectionPlatform.STATE_CONNECTED
                    : YoBleConnectionPlatform.STATE_DISCONNECTED;
            activeListener.onConnectionStateChange(status, newState);
        }
    }

    public void simulateRemoteDisconnect(int hciStatus) {
        if (activeListener != null && !closed) {
            activeListener.onConnectionStateChange(hciStatus, YoBleConnectionPlatform.STATE_DISCONNECTED);
        }
    }

    public void simulateNotification(@NonNull BleGattTarget target, @NonNull byte[] value) {
        if (activeListener != null && !closed) {
            activeListener.onCharacteristicChanged(target, value.clone());
        }
    }

    public void simulateServiceChangedApi31() {
        if (activeListener != null && !closed) {
            activeListener.onServiceChanged();
        }
    }

    public void simulateServicesDiscovered(int status) {
        if (activeListener != null && !closed) {
            activeListener.onServicesDiscovered(status);
        }
    }

    public void simulateReadComplete(@NonNull BleGattTarget target, @NonNull byte[] value, int status) {
        if (activeListener != null && !closed) {
            if (target.isDescriptorTarget()) {
                activeListener.onDescriptorRead(target, value.clone(), status);
            } else {
                activeListener.onCharacteristicRead(target, value.clone(), status);
            }
        }
    }

    public void simulateWriteComplete(@NonNull BleGattTarget target, @Nullable byte[] echoed, int status) {
        if (activeListener != null && !closed) {
            if (target.isDescriptorTarget()) {
                activeListener.onDescriptorWrite(target, status);
            } else {
                activeListener.onCharacteristicWrite(target, echoed, status);
            }
        }
    }

    // ── 观测计数 ──────────────────────────────────────────────────

    public int connectAttempts() {
        return connectAttempts;
    }

    public int disconnectCalls() {
        return disconnectCalls;
    }

    public int closeCalls() {
        return closeCalls;
    }

    public int discoverCalls() {
        return discoverCalls;
    }

    public int refreshCacheCalls() {
        return refreshCacheCalls;
    }

    public int readPhyCalls() {
        return readPhyCalls;
    }

    public int executeReliableCalls() {
        return executeReliableCalls;
    }

    public int abortReliableCalls() {
        return abortReliableCalls;
    }

    public boolean isClosed() {
        return closed;
    }

    @NonNull
    public List<BleGattTarget> setNotificationTargets() {
        return Collections.unmodifiableList(new ArrayList<>(setNotificationTargets));
    }

    @NonNull
    public List<RecordedWrite> charWrites() {
        return Collections.unmodifiableList(new ArrayList<>(charWrites));
    }

    @NonNull
    public List<RecordedWrite> descWrites() {
        return Collections.unmodifiableList(new ArrayList<>(descWrites));
    }

    // ── 连接工厂（由 FakeYoBleConnectionPlatform 调用） ─────────────

    YoBleConnectionPlatform.BleGattHandle acceptConnect(@NonNull YoBleConnectionPlatform.PlatformListener listener) {
        connectAttempts++;
        closed = false;
        activeListener = listener;
        TransportImpl transport = new TransportImpl();
        YoBleConnectionPlatform.BleGattHandle handle = new YoBleConnectionPlatform.BleGattHandle() {
            @NonNull
            @Override
            public YoBleGattTransport transport() {
                return transport;
            }

            @Override
            public void disconnect() {
                disconnectCalls++;
            }

            @Override
            public void close() {
                closeCalls++;
                closed = true;
            }

            @Override
            public boolean isClosed() {
                return closed;
            }
        };

        if (!manualConnectMode) {
            Integer nextStatus = scriptedConnectStatuses.poll();
            int status = nextStatus != null ? nextStatus : 0;
            simulateConnectComplete(status);
        }
        return handle;
    }

    private final class TransportImpl implements YoBleGattTransport {

        @Override
        public boolean discoverServices() {
            discoverCalls++;
            if (discoverReturnsFalse) {
                return false;
            }
            if (!deferOperationCallbacks && activeListener != null && !closed) {
                activeListener.onServicesDiscovered(autoDiscoverSuccess ? 0 : discoverFailureStatus);
            }
            return true;
        }

        @Override
        public boolean refreshDeviceCache() {
            refreshCacheCalls++;
            return refreshCacheResult;
        }

        @Override
        public boolean readCharacteristic(@NonNull BleGattTarget target) {
            if (readReturnsFalse) {
                return false;
            }
            if (!deferOperationCallbacks && activeListener != null && !closed) {
                Integer nextStatus = scriptedReadStatuses.poll();
                int status = nextStatus != null ? nextStatus : 0;
                byte[] val = storage.getOrDefault(target.withoutDescriptor(), new byte[0]);
                activeListener.onCharacteristicRead(target, val.clone(), status);
            }
            return true;
        }

        @Override
        public boolean writeCharacteristic(@NonNull BleGattTarget target,
                                           @NonNull byte[] value,
                                           @NonNull BleWriteType writeType) {
            if (writeReturnsFalse) {
                return false;
            }
            byte[] copy = value.clone();
            charWrites.add(new RecordedWrite(target, copy, writeType));
            storage.put(target.withoutDescriptor(), copy);

            if (writeHook != null) {
                writeHook.onWrite(target, copy, writeType, FakeBleGattDevice.this);
            }
            if (!deferOperationCallbacks && activeListener != null && !closed) {
                Integer nextStatus = scriptedWriteStatuses.poll();
                int status = nextStatus != null ? nextStatus : 0;
                byte[] echo = copy.clone();
                if (reliableCorruptEcho && echo.length > 0) {
                    echo[0] = (byte) (echo[0] ^ 0xFF);
                }
                activeListener.onCharacteristicWrite(target, echo, status);
            }
            return true;
        }

        @Override
        public boolean readDescriptor(@NonNull BleGattTarget target) {
            if (readDescReturnsFalse) {
                return false;
            }
            if (!deferOperationCallbacks && activeListener != null && !closed) {
                byte[] val = storage.getOrDefault(target, new byte[]{0x00, 0x00});
                activeListener.onDescriptorRead(target, val.clone(), defaultDescReadStatus);
            }
            return true;
        }

        @Override
        public boolean writeDescriptor(@NonNull BleGattTarget target, @NonNull byte[] value) {
            if (writeDescReturnsFalse) {
                return false;
            }
            byte[] copy = value.clone();
            descWrites.add(new RecordedWrite(target, copy, BleWriteType.WITH_RESPONSE));
            storage.put(target, copy);
            if (!deferOperationCallbacks && activeListener != null && !closed) {
                activeListener.onDescriptorWrite(target, defaultDescWriteStatus);
            }
            return true;
        }

        @Override
        public boolean setCharacteristicNotification(@NonNull BleGattTarget target, boolean enabled) {
            if (enabled) {
                setNotificationTargets.add(target.withoutDescriptor());
            }
            return true;
        }

        @Override
        public boolean requestMtu(int mtu) {
            if (mtuReturnsFalse) {
                return false;
            }
            if (!deferOperationCallbacks && activeListener != null && !closed) {
                int granted = Math.min(mtu, maxSupportedMtu);
                activeListener.onMtuChanged(granted, mtuFailureStatus);
            }
            return true;
        }

        @Override
        public boolean requestConnectionPriority(@NonNull BleConnectionPriority priority) {
            if (!suppressConnectionPriorityCallback && !deferOperationCallbacks
                    && activeListener != null && !closed) {
                int interval = priority == BleConnectionPriority.HIGH ? 12 : 40;
                activeListener.onConnectionUpdated(interval, 0, 500, 0);
            }
            return true;
        }

        @Override
        public boolean setPreferredPhy(@NonNull BlePhy txPhy,
                                       @NonNull BlePhy rxPhy,
                                       @NonNull BlePhyOptions options) {
            remotePhy = new BlePhyConfig(txPhy, rxPhy, options);
            if (!suppressSetPhyCallback && !deferOperationCallbacks
                    && activeListener != null && !closed) {
                activeListener.onPhyUpdate(remotePhy, 0);
            }
            return true;
        }

        @Override
        public boolean readPhy() {
            readPhyCalls++;
            if (!deferOperationCallbacks && activeListener != null && !closed) {
                activeListener.onPhyRead(remotePhy, readPhyStatus);
            }
            return true;
        }

        @Override
        public boolean readRemoteRssi() {
            if (!deferOperationCallbacks && activeListener != null && !closed) {
                activeListener.onReadRemoteRssi(remoteRssi, rssiStatus);
            }
            return true;
        }

        @Override
        public boolean beginReliableWrite() {
            return true;
        }

        @Override
        public boolean executeReliableWrite() {
            executeReliableCalls++;
            if (!deferOperationCallbacks && activeListener != null && !closed) {
                activeListener.onReliableWriteCompleted(reliableExecuteStatus);
            }
            return true;
        }

        @Override
        public void abortReliableWrite() {
            abortReliableCalls++;
            if (!deferOperationCallbacks && activeListener != null && !closed) {
                activeListener.onReliableWriteCompleted(0);
            }
        }

        @NonNull
        @Override
        public BleGattDatabaseSnapshot captureDatabaseSnapshot(long timestampMillis) {
            return new BleGattDatabaseSnapshot(deviceId, new ArrayList<>(services), timestampMillis);
        }
    }
}
