package com.ggec.earbuds.wiring.device;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.data.ai.voice.BluetoothStreamAudioSource;
import com.ggec.earbuds.domain.ai.port.AiAudioSourceCatalog;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.ui.device.DeviceCapabilityIds;

import java.util.Objects;

/**
 * 设备连接态 → AI 音频输入源目录的桥接器：
 * 当活动设备就绪且支持 {@link RecordMode#STREAM}（随录随传）时，自动向
 * {@link AiAudioSourceCatalog} 注册 {@link BluetoothStreamAudioSource}；
 * 设备断开或切换为不支持流式录音的机型时自动注销，触发通话会话无缝回退手机 MIC。
 */
public final class DeviceBluetoothAudioSourceBridge {

    private final ObserveDevice observeDevice;
    private final RecordPort recordPort;
    private final AiAudioSourceCatalog audioSourceCatalog;
    @Nullable
    private final DeviceCapabilityCatalog capabilityCatalog;
    private final Object lock = new Object();

    @Nullable
    private Subscription subscription;
    @Nullable
    private String registeredKey;

    public DeviceBluetoothAudioSourceBridge(@NonNull ObserveDevice observeDevice,
                                            @NonNull RecordPort recordPort,
                                            @NonNull AiAudioSourceCatalog audioSourceCatalog) {
        this(observeDevice, recordPort, audioSourceCatalog, null);
    }

    public DeviceBluetoothAudioSourceBridge(@NonNull ObserveDevice observeDevice,
                                            @NonNull RecordPort recordPort,
                                            @NonNull AiAudioSourceCatalog audioSourceCatalog,
                                            @Nullable DeviceCapabilityCatalog capabilityCatalog) {
        this.observeDevice = Objects.requireNonNull(observeDevice, "observeDevice");
        this.recordPort = Objects.requireNonNull(recordPort, "recordPort");
        this.audioSourceCatalog = Objects.requireNonNull(audioSourceCatalog, "audioSourceCatalog");
        this.capabilityCatalog = capabilityCatalog;
    }

    public void start() {
        synchronized (lock) {
            if (subscription != null) {
                return;
            }
            subscription = observeDevice.execute(this::onSnapshot);
        }
    }

    public void stop() {
        Subscription active;
        synchronized (lock) {
            active = subscription;
            subscription = null;
            registeredKey = null;
        }
        if (active != null) {
            active.unsubscribe();
        }
        audioSourceCatalog.unregisterExternal(BluetoothStreamAudioSource.SOURCE_ID);
    }

    private void onSnapshot(@NonNull DeviceSnapshot snapshot) {
        boolean ready = snapshot.connection() == ConnectionState.READY
                && recordPort.supportedModes().contains(RecordMode.STREAM);
        if (!ready) {
            boolean wasRegistered;
            synchronized (lock) {
                wasRegistered = registeredKey != null;
                registeredKey = null;
            }
            if (wasRegistered) {
                audioSourceCatalog.unregisterExternal(BluetoothStreamAudioSource.SOURCE_ID);
            }
            return;
        }
        String label = resolveLabel(snapshot);
        boolean activeDuplex = capabilityCatalog != null
                && snapshot.modelId() != null
                && capabilityCatalog.model(snapshot.modelId())
                        .map(p -> p.supports(DeviceCapabilityIds.ACTIVE_DUPLEX))
                        .orElse(false);
        String key = label + "|" + activeDuplex;
        synchronized (lock) {
            if (key.equals(registeredKey)) {
                return;
            }
            registeredKey = key;
        }
        audioSourceCatalog.registerExternal(
                new BluetoothStreamAudioSource(recordPort, label, activeDuplex));
    }

    @NonNull
    private static String resolveLabel(@NonNull DeviceSnapshot snapshot) {
        if (!snapshot.classicName().isBlank()) {
            return snapshot.classicName().strip();
        }
        if (!snapshot.bleName().isBlank()) {
            return snapshot.bleName().strip();
        }
        String modelId = snapshot.modelId();
        return modelId == null || modelId.isBlank() ? "Bluetooth Device" : modelId;
    }
}
