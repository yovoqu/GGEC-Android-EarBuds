package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.internal.center.port.HsCommandLink;
import com.yovo.lib_bluetooth.YoBluetoothSDK;
import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleConnectionState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleNotificationObserver;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleTransferProgressObserver;

/**
 * 把 HS 命令链路落到 {@link YoBleClient}。
 * 蓝牙库只允许被 {@code provider/lib_bluetooth} 引用。特征、写入类型和连接参数见 {@link HsBleSettings}。
 */
public final class YoBleCommandLink implements HsCommandLink {

    private static final BleGattTarget WRITE_TARGET =
            BleGattTarget.characteristic(HsBleSettings.SERVICE_UUID, HsBleSettings.WRITE_UUID);
    private static final BleGattTarget NOTIFY_TARGET =
            BleGattTarget.characteristic(HsBleSettings.SERVICE_UUID, HsBleSettings.NOTIFY_UUID);

    private final YoBleClient client;
    private final Object lock = new Object();

    private int generation;
    @Nullable
    private BleDeviceId deviceId;
    @Nullable
    private BleOperationHandle connection;
    @Nullable
    private BleOperationHandle subscription;
    private boolean ready;
    private boolean released;
    @Nullable
    private volatile Listener listener;

    public YoBleCommandLink(@NonNull YoBleClient client) {
        this.client = client;
    }

    @NonNull
    public static YoBleCommandLink open(@NonNull Context context) {
        Context application = context.getApplicationContext();
        Context target = application == null ? context : application;
        return new YoBleCommandLink(YoBluetoothSDK.ble(target, HsBleSettings.CLIENT));
    }

    /** 同一条 BLE 客户端供附近扫描复用，避免再创建一个运行时。 */
    @NonNull
    public YoBleClient bleClient() {
        return client;
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void open(@NonNull String macAddress) {
        if (!BleDeviceId.isValid(macAddress)) {
            throw new IllegalArgumentException("无效的设备地址: " + macAddress);
        }
        Retired retired;
        int currentGeneration;
        BleDeviceId target = new BleDeviceId(macAddress);
        synchronized (lock) {
            if (released) {
                throw new IllegalStateException("HS 蓝牙链路已释放");
            }
            retired = retireLocked();
            currentGeneration = generation;
            deviceId = target;
        }
        retired.cancel(client);
        BleOperationHandle handle = client.connect(
                target, HsBleSettings.CONNECTION, new ConnectionWatcher(currentGeneration));
        synchronized (lock) {
            if (currentGeneration != generation) {
                handle.cancel();
                return;
            }
            connection = handle;
        }
    }

    @Override
    public void close() {
        Retired retired;
        synchronized (lock) {
            retired = retireLocked();
        }
        retired.cancel(client);
        notifyReady(false);
    }

    @Override
    public void release() {
        synchronized (lock) {
            released = true;
        }
        close();
        client.release();
    }

    @Override
    public boolean write(@NonNull byte[] payload, @NonNull WriteCallback callback) {
        BleDeviceId target;
        int currentGeneration;
        synchronized (lock) {
            if (!ready || deviceId == null || released) {
                return false;
            }
            target = deviceId;
            currentGeneration = generation;
        }
        byte[] copy = payload.clone();
        int maxPayload = maxWritePayloadBytes(target);
        // 先回调 onStarted，再发起写：某些 BLE 栈会在 client.write() 内同步回调 onWrite/onFailed，
        // 若 onStarted 放在写之后，onCompleted/onTimeout 会先于 onStarted 触发，违反回调顺序契约。
        callback.onStarted();
        // 写入受理是同步决策，结果只会经 BleGattObserver / BleTransferProgressObserver 异步回来：
        // lib 把动作投进自己的状态线程（YoBleRuntime#state），线程内异常由 runStateTask 隔离记录，
        // 受理路径不抛受检或运行时异常。因此这里没有任何可捕获的写失败——失败由回调类型化送达。
        if (copy.length <= maxPayload) {
            client.write(target, WRITE_TARGET, copy, HsBleSettings.WRITE_TYPE,
                    new BleGattObserver() {
                        @Override
                        public void onWrite(@NonNull BleDeviceId deviceId, @NonNull BleGattTarget target) {
                            if (isCurrent(currentGeneration)) {
                                callback.onCompleted(0);
                            }
                        }

                        @Override
                        public void onFailed(@NonNull BleFailure failure) {
                            handleWriteFailure(currentGeneration, failure, callback);
                        }
                    });
        } else {
            // H-04：超过当前 MTU 单包载荷上限的长帧走分片写（默认 splitter 依 MTU 切片）。
            client.writeSplit(target, WRITE_TARGET, copy, HsBleSettings.WRITE_TYPE, null,
                    new BleTransferProgressObserver() {
                        @Override
                        public void onCompleted(@NonNull BleDeviceId deviceId,
                                                @NonNull BleGattTarget gattTarget,
                                                long totalBytes) {
                            if (isCurrent(currentGeneration)) {
                                callback.onCompleted(0);
                            }
                        }

                        @Override
                        public void onFailed(@NonNull BleFailure failure) {
                            handleWriteFailure(currentGeneration, failure, callback);
                        }
                    });
        }
        return true;
    }

    private void handleWriteFailure(int currentGeneration,
                                    @NonNull BleFailure failure,
                                    @NonNull WriteCallback callback) {
        if (!isCurrent(currentGeneration)) {
            return;
        }
        if (failure.errorCode() == BleErrorCode.GATT_OPERATION_TIMEOUT) {
            callback.onTimeout();
            return;
        }
        int status = failure.rawStatusCode();
        callback.onCompleted(status <= 0 ? 1 : status);
    }

    /**
     * 单包写入上限取自 lib 的连接快照。
     *
     * <p>快照读取是进程内的同步存储查询（{@code YoBleConnectionManager#getConnectionSnapshot}
     * 只对非法 MAC 返回失败，而 {@link #open(String)} 已经拒绝非法地址并先行校验），
     * 因此失败意味着 lib 的 {@code Result} 契约被破坏。这里让 {@code requireValue()}
     * 直接抛出，不用默认 MTU 冒充一个"看起来可用"的上限。</p>
     */
    private int maxWritePayloadBytes(@NonNull BleDeviceId target) {
        return client.getConnectionSnapshot(target)
                .requireValue()
                .maxWritePayloadBytes(HsBleSettings.WRITE_TYPE);
    }

    private void ensureSubscribed(int currentGeneration) {
        BleDeviceId target;
        synchronized (lock) {
            if (currentGeneration != generation || deviceId == null || subscription != null || released) {
                return;
            }
            target = deviceId;
        }
        BleOperationHandle handle = client.subscribe(
                target, NOTIFY_TARGET, HsBleSettings.NOTIFY_MODE,
                new NotifyWatcher(currentGeneration));
        synchronized (lock) {
            if (currentGeneration != generation) {
                handle.cancel();
                return;
            }
            subscription = handle;
        }
    }

    private void markNotReady(int currentGeneration, @Nullable String error) {
        boolean changed;
        BleOperationHandle notifyHandle;
        synchronized (lock) {
            if (currentGeneration != generation) {
                return;
            }
            changed = ready;
            ready = false;
            notifyHandle = subscription;
            subscription = null;
        }
        if (notifyHandle != null) {
            notifyHandle.cancel();
        }
        // 顺序即契约：断开原因属于「断开事件」的一部分，必须在状态变化被消费方看到之前就位。
        // 消费方（引擎）把两条通知各自投进自己的单线程队列，跨线程的到达先后由它决定不了——
        // 状态变化先到时会读到尚未落值的断连原因。确定性只能落在本层的发送顺序上：先原因，后状态。
        // 两个分支各管一个事件、各带一个门（error != null / ready 由 true 变 false 的快照），
        // 因此顺序调整既不改变调用次数，也不会产生第二条事件。
        if (error != null) {
            Listener current = listener;
            if (current != null) {
                current.onLinkError(error);
            }
        }
        if (changed) {
            notifyReady(false);
        }
    }

    private void markReady(int currentGeneration) {
        synchronized (lock) {
            if (currentGeneration != generation || deviceId == null) {
                return;
            }
            ready = true;
        }
        notifyReady(true);
    }

    private void notifyReady(boolean ready) {
        Listener current = listener;
        if (current != null) {
            current.onReady(ready);
        }
    }

    private boolean isCurrent(int currentGeneration) {
        synchronized (lock) {
            return currentGeneration == generation && !released;
        }
    }

    @NonNull
    private Retired retireLocked() {
        generation++;
        ready = false;
        Retired retired = new Retired(deviceId, connection, subscription);
        deviceId = null;
        connection = null;
        subscription = null;
        return retired;
    }

    private final class ConnectionWatcher implements BleConnectionObserver {
        private final int currentGeneration;

        private ConnectionWatcher(int currentGeneration) {
            this.currentGeneration = currentGeneration;
        }

        @Override
        public void onConnectionStateChanged(@NonNull BleConnectionSnapshot snapshot) {
            if (snapshot.isReady()) {
                ensureSubscribed(currentGeneration);
                return;
            }
            BleConnectionState state = snapshot.state();
            if (state == BleConnectionState.DISCONNECTED
                    || state == BleConnectionState.DISCONNECTING
                    || state == BleConnectionState.FAILED
                    || state == BleConnectionState.IDLE) {
                markNotReady(currentGeneration, null);
            }
        }

        @Override
        public void onConnectionFailed(@NonNull BleFailure failure) {
            markNotReady(currentGeneration, failure.message());
        }
    }

    private final class NotifyWatcher implements BleNotificationObserver {
        private final int currentGeneration;

        private NotifyWatcher(int currentGeneration) {
            this.currentGeneration = currentGeneration;
        }

        @Override
        public void onNotificationEnabled(@NonNull BleDeviceId deviceId, @NonNull BleGattTarget target) {
            markReady(currentGeneration);
        }

        @Override
        public void onCharacteristicChanged(@NonNull BleDeviceId deviceId,
                                            @NonNull BleGattTarget target,
                                            @NonNull byte[] value) {
            if (!isCurrent(currentGeneration) || value.length == 0) {
                return;
            }
            Listener current = listener;
            if (current != null) {
                current.onInbound(value);
            }
        }

        @Override
        public void onNotificationFailed(@NonNull BleFailure failure) {
            markNotReady(currentGeneration, failure.message());
        }
    }

    private static final class Retired {
        @Nullable
        private final BleDeviceId deviceId;
        @Nullable
        private final BleOperationHandle connection;
        @Nullable
        private final BleOperationHandle subscription;

        private Retired(@Nullable BleDeviceId deviceId,
                        @Nullable BleOperationHandle connection,
                        @Nullable BleOperationHandle subscription) {
            this.deviceId = deviceId;
            this.connection = connection;
            this.subscription = subscription;
        }

        private void cancel(@NonNull YoBleClient client) {
            if (subscription != null) {
                subscription.cancel();
            }
            if (connection != null) {
                connection.cancel();
            }
            if (deviceId != null) {
                client.disconnect(deviceId);
            }
        }
    }
}
