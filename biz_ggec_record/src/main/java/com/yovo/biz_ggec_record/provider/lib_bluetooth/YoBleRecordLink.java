package com.yovo.biz_ggec_record.provider.lib_bluetooth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.YoBluetoothSDK;
import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionPriority;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleConnectionState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleNotificationObserver;
import com.yovo.lib_bluetooth.model.ble.BleNotificationSetupMode;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BlePhyConfig;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Record 音视频 BLE 链路适配。
 * UUID 与 GATT 细节只允许出现在本包；会话层只拿到字节流与就绪状态。
 * <p>
 * 连接后由 {@link BleConnectionConfig} 协商 HIGH priority / MTU 517 / 2M PHY 并自动服务发现；
 * 就绪后<strong>串行</strong>订阅视频 TX、音频 TX，全部完成才 {@link Listener#onReady()}。
 */
public final class YoBleRecordLink {

    public static final UUID SERVICE_UUID =
            UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e5450");
    public static final UUID RX_UUID =
            UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e5401");
    public static final UUID VIDEO_TX_UUID =
            UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e5402");
    public static final UUID AUDIO_TX_UUID =
            UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e5403");

    private static final BleGattTarget VIDEO_TX =
            BleGattTarget.characteristic(SERVICE_UUID, VIDEO_TX_UUID);
    private static final BleGattTarget AUDIO_TX =
            BleGattTarget.characteristic(SERVICE_UUID, AUDIO_TX_UUID);
    private static final BleGattTarget RX =
            BleGattTarget.characteristic(SERVICE_UUID, RX_UUID);

    private static final BleConnectionConfig CONNECTION = BleConnectionConfig.builder()
            .autoConnect(false)
            .requestMtu(517)
            .preferredPhy(BlePhyConfig.le2m())
            .preferredConnectionPriority(BleConnectionPriority.HIGH)
            .autoDiscoverServices(true)
            .maxRetryCount(3)
            .retryInitialDelayMillis(1_000L)
            .retryMaxDelayMillis(15_000L)
            .build();

    public interface Listener {
        void onConnected();

        void onReady();

        void onDisconnected();

        void onFailed(@NonNull String message);

        void onVideoBytes(@NonNull byte[] payload);

        void onAudioBytes(@NonNull byte[] payload);
    }

    private final YoBleClient client;
    private final Object lock = new Object();
    private final AtomicLong videoRxBytes = new AtomicLong();
    private final AtomicLong audioRxBytes = new AtomicLong();

    private int generation;
    @Nullable
    private BleDeviceId deviceId;
    @Nullable
    private BleOperationHandle connection;
    @Nullable
    private BleOperationHandle videoSubscription;
    @Nullable
    private BleOperationHandle audioSubscription;
    private boolean ready;
    private boolean released;
    private boolean videoSubscribed;
    private boolean audioSubscribed;
    private boolean videoSubscribeAttempted;
    private boolean audioSubscribeAttempted;
    @Nullable
    private volatile Listener listener;

    public YoBleRecordLink(@NonNull YoBleClient client) {
        this.client = client;
    }

    @NonNull
    public static YoBleRecordLink open(@NonNull Context context) {
        Context application = context.getApplicationContext();
        Context target = application == null ? context : application;
        BleConfig config = BleConfig.builder().callbackOnMainThread(false).build();
        return new YoBleRecordLink(YoBluetoothSDK.ble(target, config));
    }

    @NonNull
    public YoBleClient bleClient() {
        return client;
    }

    public long videoRxBytes() {
        return videoRxBytes.get();
    }

    public long audioRxBytes() {
        return audioRxBytes.get();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void connect(@NonNull String macAddress) {
        if (!BleDeviceId.isValid(macAddress)) {
            notifyFailed("无效的设备地址: " + macAddress);
            return;
        }
        Retired retired;
        int currentGeneration;
        BleDeviceId target = new BleDeviceId(macAddress);
        synchronized (lock) {
            if (released) {
                notifyFailed("Record 蓝牙链路已释放");
                return;
            }
            retired = retireLocked();
            currentGeneration = generation;
            deviceId = target;
            videoRxBytes.set(0L);
            audioRxBytes.set(0L);
        }
        retired.cancel(client);
        BleOperationHandle handle = client.connect(
                target, CONNECTION, new ConnectionWatcher(currentGeneration));
        synchronized (lock) {
            if (currentGeneration != generation) {
                handle.cancel();
                return;
            }
            connection = handle;
        }
    }

    public void disconnect() {
        Retired retired;
        synchronized (lock) {
            retired = retireLocked();
        }
        retired.cancel(client);
        Listener current = listener;
        if (current != null) {
            current.onDisconnected();
        }
    }

    public boolean isReady() {
        synchronized (lock) {
            return ready && !released;
        }
    }

    public void writeRxText(@NonNull String text) {
        BleDeviceId target;
        int currentGeneration;
        synchronized (lock) {
            if (!ready || deviceId == null || released) {
                return;
            }
            target = deviceId;
            currentGeneration = generation;
        }
        byte[] payload = text.getBytes(StandardCharsets.US_ASCII);
        if (payload.length == 0) {
            return;
        }
        client.write(target, RX, payload, BleWriteType.WITH_RESPONSE, new BleGattObserver() {
            @Override
            public void onFailed(@NonNull BleFailure failure) {
                if (isCurrent(currentGeneration)) {
                    notifyFailed("RX 写入失败: " + failure.message());
                }
            }
        });
    }

    public void close() {
        synchronized (lock) {
            if (released) {
                return;
            }
            released = true;
        }
        disconnect();
        client.release();
    }

    private void beginSubscriptions(int currentGeneration) {
        BleDeviceId target;
        synchronized (lock) {
            if (currentGeneration != generation || deviceId == null || released) {
                return;
            }
            if (videoSubscribeAttempted || audioSubscribeAttempted) {
                return;
            }
            target = deviceId;
            videoSubscribeAttempted = true;
        }
        subscribeVideo(currentGeneration, target);
    }

    private void subscribeVideo(int currentGeneration, @NonNull BleDeviceId target) {
        BleOperationHandle handle = client.subscribe(
                target, VIDEO_TX, BleNotificationSetupMode.DEFAULT,
                new NotifyWatcher(currentGeneration, true));
        synchronized (lock) {
            if (currentGeneration != generation) {
                handle.cancel();
                return;
            }
            videoSubscription = handle;
        }
    }

    private void subscribeAudio(int currentGeneration, @NonNull BleDeviceId target) {
        synchronized (lock) {
            if (currentGeneration != generation || released || audioSubscribeAttempted) {
                return;
            }
            audioSubscribeAttempted = true;
        }
        BleOperationHandle handle = client.subscribe(
                target, AUDIO_TX, BleNotificationSetupMode.DEFAULT,
                new NotifyWatcher(currentGeneration, false));
        synchronized (lock) {
            if (currentGeneration != generation) {
                handle.cancel();
                return;
            }
            audioSubscription = handle;
        }
    }

    private void onSubscribeFinished(int currentGeneration, boolean videoChannel, boolean success) {
        BleDeviceId target;
        boolean shouldSubscribeAudio = false;
        boolean shouldMarkReady = false;
        boolean shouldFail = false;
        synchronized (lock) {
            if (currentGeneration != generation || released) {
                return;
            }
            if (videoChannel) {
                videoSubscribed = success;
                if (!audioSubscribeAttempted) {
                    shouldSubscribeAudio = true;
                    target = deviceId;
                } else {
                    target = deviceId;
                    shouldMarkReady = videoSubscribed || audioSubscribed;
                    shouldFail = !shouldMarkReady;
                }
            } else {
                audioSubscribed = success;
                target = deviceId;
                shouldMarkReady = videoSubscribed || audioSubscribed;
                shouldFail = !shouldMarkReady;
            }
        }
        if (shouldSubscribeAudio && target != null) {
            subscribeAudio(currentGeneration, target);
            return;
        }
        if (shouldFail) {
            markNotReady(currentGeneration, "未找到可用的视频/音频 TX 特征或订阅失败");
            return;
        }
        if (shouldMarkReady) {
            markReady(currentGeneration);
        }
    }

    private void markReady(int currentGeneration) {
        synchronized (lock) {
            if (currentGeneration != generation || deviceId == null || released) {
                return;
            }
            ready = true;
        }
        Listener current = listener;
        if (current != null) {
            current.onReady();
        }
    }

    private void markNotReady(int currentGeneration, @Nullable String error) {
        BleOperationHandle videoHandle;
        BleOperationHandle audioHandle;
        boolean wasReady;
        synchronized (lock) {
            if (currentGeneration != generation) {
                return;
            }
            wasReady = ready;
            ready = false;
            videoHandle = videoSubscription;
            audioHandle = audioSubscription;
            videoSubscription = null;
            audioSubscription = null;
            videoSubscribed = false;
            audioSubscribed = false;
            videoSubscribeAttempted = false;
            audioSubscribeAttempted = false;
        }
        if (videoHandle != null) {
            videoHandle.cancel();
        }
        if (audioHandle != null) {
            audioHandle.cancel();
        }
        if (error != null) {
            notifyFailed(error);
        } else if (wasReady) {
            Listener current = listener;
            if (current != null) {
                current.onDisconnected();
            }
        }
    }

    private void notifyFailed(@NonNull String message) {
        Listener current = listener;
        if (current != null) {
            current.onFailed(message);
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
        videoSubscribed = false;
        audioSubscribed = false;
        videoSubscribeAttempted = false;
        audioSubscribeAttempted = false;
        Retired retired = new Retired(deviceId, connection, videoSubscription, audioSubscription);
        deviceId = null;
        connection = null;
        videoSubscription = null;
        audioSubscription = null;
        return retired;
    }

    private final class ConnectionWatcher implements BleConnectionObserver {
        private final int currentGeneration;

        private ConnectionWatcher(int currentGeneration) {
            this.currentGeneration = currentGeneration;
        }

        @Override
        public void onConnectionStateChanged(@NonNull BleConnectionSnapshot snapshot) {
            if (!isCurrent(currentGeneration)) {
                return;
            }
            if (snapshot.isReady()) {
                Listener current = listener;
                if (current != null) {
                    current.onConnected();
                }
                beginSubscriptions(currentGeneration);
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
        private final boolean videoChannel;

        private NotifyWatcher(int currentGeneration, boolean videoChannel) {
            this.currentGeneration = currentGeneration;
            this.videoChannel = videoChannel;
        }

        @Override
        public void onNotificationEnabled(@NonNull BleDeviceId deviceId, @NonNull BleGattTarget target) {
            onSubscribeFinished(currentGeneration, videoChannel, true);
        }

        @Override
        public void onCharacteristicChanged(@NonNull BleDeviceId deviceId,
                                            @NonNull BleGattTarget target,
                                            @NonNull byte[] value) {
            if (!isCurrent(currentGeneration) || value.length == 0) {
                return;
            }
            Listener current = listener;
            if (current == null) {
                return;
            }
            if (videoChannel) {
                videoRxBytes.addAndGet(value.length);
                current.onVideoBytes(value);
            } else {
                audioRxBytes.addAndGet(value.length);
                current.onAudioBytes(value);
            }
        }

        @Override
        public void onNotificationFailed(@NonNull BleFailure failure) {
            // 缺特征时仍尝试另一路；两路都失败才整体失败。
            onSubscribeFinished(currentGeneration, videoChannel, false);
        }
    }

    private static final class Retired {
        @Nullable
        private final BleDeviceId deviceId;
        @Nullable
        private final BleOperationHandle connection;
        @Nullable
        private final BleOperationHandle videoSubscription;
        @Nullable
        private final BleOperationHandle audioSubscription;

        private Retired(@Nullable BleDeviceId deviceId,
                        @Nullable BleOperationHandle connection,
                        @Nullable BleOperationHandle videoSubscription,
                        @Nullable BleOperationHandle audioSubscription) {
            this.deviceId = deviceId;
            this.connection = connection;
            this.videoSubscription = videoSubscription;
            this.audioSubscription = audioSubscription;
        }

        private void cancel(@NonNull YoBleClient client) {
            if (videoSubscription != null) {
                videoSubscription.cancel();
            }
            if (audioSubscription != null) {
                audioSubscription.cancel();
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
