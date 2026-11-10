package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.internal.contract.HsCommandLink;
import com.yovo.lib_bluetooth.YoBluetoothSDK;
import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleConnectionState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleNotificationObserver;
import com.yovo.lib_bluetooth.model.ble.BleNotificationSetupMode;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

import java.util.UUID;

/**
 * 把 HS 命令链路落到 {@link YoBleClient}。
 * 这是 biz_ggec_hs 里唯一允许引用 lib_bluetooth 的类型。
 */
public final class YoBleCommandLink implements HsCommandLink {

    private static final UUID SERVICE_UUID =
            UUID.fromString("65786365-6c70-6f69-6e74-2e636f820100");
    private static final UUID WRITE_UUID =
            UUID.fromString("65786365-6c70-6f69-6e74-2e636f820104");
    private static final UUID NOTIFY_UUID =
            UUID.fromString("65786365-6c70-6f69-6e74-2e636f820105");

    private static final BleGattTarget WRITE_TARGET =
            BleGattTarget.characteristic(SERVICE_UUID, WRITE_UUID);
    private static final BleGattTarget NOTIFY_TARGET =
            BleGattTarget.characteristic(SERVICE_UUID, NOTIFY_UUID);
    private static final BleConnectionConfig CONNECTION =
            BleConnectionConfig.builder().build();

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
    private Listener listener;

    public YoBleCommandLink(@NonNull YoBleClient client) {
        this.client = client;
    }

    @NonNull
    public static YoBleCommandLink open(@NonNull Context context) {
        Context application = context.getApplicationContext();
        return new YoBleCommandLink(YoBluetoothSDK.ble(application == null ? context : application));
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
        BleOperationHandle handle = client.connect(target, CONNECTION, new ConnectionWatcher(currentGeneration));
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
        try {
            client.write(target, WRITE_TARGET, copy, BleWriteType.WITHOUT_RESPONSE,
                    new BleGattObserver() {
                        @Override
                        public void onWrite(@NonNull BleDeviceId deviceId, @NonNull BleGattTarget target) {
                            if (isCurrent(currentGeneration)) {
                                callback.onCompleted(0);
                            }
                        }

                        @Override
                        public void onFailed(@NonNull BleFailure failure) {
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
                    });
        } catch (RuntimeException ignored) {
            return false;
        }
        callback.onStarted();
        return true;
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
                target, NOTIFY_TARGET, BleNotificationSetupMode.DEFAULT,
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
        if (changed) {
            notifyReady(false);
        }
        if (error != null) {
            Listener current = listener;
            if (current != null) {
                current.onLinkError(error);
            }
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
