package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.internal.center.port.HsAudioDataLink;
import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleNotificationObserver;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 录音音频通道（UUID 820000 / 820003）的 BLE 订阅实现。
 */
public final class YoBleAudioDataLink implements HsAudioDataLink {

    private static final BleGattTarget AUDIO_NOTIFY_TARGET =
            BleGattTarget.characteristic(HsBleSettings.AUDIO_SERVICE_UUID, HsBleSettings.AUDIO_NOTIFY_UUID);

    private final YoBleClient client;
    private final Object lock = new Object();

    @Nullable
    private BleDeviceId deviceId;
    @Nullable
    private BleOperationHandle subscription;
    @Nullable
    private volatile Consumer<byte[]> audioConsumer;
    private boolean listening;
    private boolean released;

    public YoBleAudioDataLink(@NonNull YoBleClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public void bindDevice(@Nullable String macAddress) {
        synchronized (lock) {
            if (macAddress == null || macAddress.isEmpty()) {
                this.deviceId = null;
            } else if (BleDeviceId.isValid(macAddress)) {
                this.deviceId = new BleDeviceId(macAddress);
            }
        }
    }

    @Override
    public void startAudioNotify(@Nullable ReadyCallback onReady,
                                 @NonNull Consumer<byte[]> onAudioChunk) {
        Objects.requireNonNull(onAudioChunk, "onAudioChunk");
        BleDeviceId target;
        synchronized (lock) {
            if (released) {
                if (onReady != null) {
                    onReady.onError("AudioDataLink 已释放");
                }
                return;
            }
            this.audioConsumer = onAudioChunk;
            this.listening = true;
            target = this.deviceId;
            if (target == null) {
                if (onReady != null) {
                    onReady.onError("设备尚未绑定或未连接");
                }
                return;
            }
            if (subscription != null) {
                subscription.cancel();
                subscription = null;
            }
        }

        BleOperationHandle handle = client.subscribe(
                target, AUDIO_NOTIFY_TARGET, HsBleSettings.NOTIFY_MODE,
                new BleNotificationObserver() {
                    @Override
                    public void onNotificationEnabled(@NonNull BleDeviceId devId,
                                                      @NonNull BleGattTarget gattTarget) {
                        if (onReady != null) {
                            onReady.onReady();
                        }
                    }

                    @Override
                    public void onNotificationFailed(@NonNull BleFailure failure) {
                        if (onReady != null) {
                            onReady.onError(failure.message());
                        }
                    }

                    @Override
                    public void onCharacteristicChanged(@NonNull BleDeviceId devId,
                                                         @NonNull BleGattTarget gattTarget,
                                                         @NonNull byte[] value) {
                        Consumer<byte[]> consumer = audioConsumer;
                        if (consumer != null && value.length > 0) {
                            consumer.accept(value);
                        }
                    }
                });

        synchronized (lock) {
            if (!listening || released) {
                handle.cancel();
            } else {
                subscription = handle;
            }
        }
    }

    @Override
    public void stopAudioNotify() {
        BleOperationHandle handle;
        synchronized (lock) {
            listening = false;
            audioConsumer = null;
            handle = subscription;
            subscription = null;
        }
        if (handle != null) {
            handle.cancel();
        }
    }

    @Override
    public boolean isListening() {
        synchronized (lock) {
            return listening && !released;
        }
    }

    @Override
    public void release() {
        stopAudioNotify();
        synchronized (lock) {
            released = true;
            deviceId = null;
        }
    }
}
