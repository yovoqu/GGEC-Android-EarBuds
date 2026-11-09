package com.ggec.earbuds.platform.ai.voice;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.port.AiAudioSource;
import com.ggec.earbuds.domain.ai.port.AiAudioSourceCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Android 音频输入源目录实现：
 * <ul>
 *   <li>实时发现系统可用录音设备（蓝牙 BLE / SCO、USB、有线耳机、内置麦克风）；</li>
 *   <li>支持注册与注销外部自定义 PCM 输入源（如耳机自定义私有传输流或调试测试源）；</li>
 *   <li>仅在存在活动订阅者时挂载 {@link AudioDeviceCallback}，通话结束后零常驻系统监听器泄漏。</li>
 * </ul>
 */
public final class AndroidAudioSourceCatalog implements AiAudioSourceCatalog {

    private static final String DEVICE_ID_PREFIX = "android:device:";

    private final Context context;
    @Nullable
    private final AudioManager audioManager;
    private final Map<String, AiAudioSource> externalSources = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<Observer> observers = new CopyOnWriteArraySet<>();
    private final Object callbackLock = new Object();
    @Nullable
    private Handler mainHandler;
    private boolean deviceCallbackRegistered;

    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            notifyObservers();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            notifyObservers();
        }
    };

    public AndroidAudioSourceCatalog(@NonNull Context context) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    @NonNull
    @Override
    public List<AiAudioSource.Descriptor> availableSources() {
        List<AiAudioSource.Descriptor> result = new ArrayList<>();
        for (AiAudioSource external : externalSources.values()) {
            result.add(external.descriptor());
        }
        boolean hasBuiltin = false;
        for (SystemEntry entry : systemEntries()) {
            result.add(entry.descriptor());
            if (entry.descriptor().kind() == AiAudioSource.Kind.BUILTIN_MIC) {
                hasBuiltin = true;
            }
        }
        if (!hasBuiltin) {
            result.add(new AndroidMicAudioSource(context).descriptor());
        }
        result.sort(Comparator
                .comparingInt((AiAudioSource.Descriptor d) -> priorityOf(d.kind()))
                .thenComparing(AiAudioSource.Descriptor::id));
        return List.copyOf(result);
    }

    @Nullable
    @Override
    public AiAudioSource defaultSource() {
        List<AiAudioSource.Descriptor> all = availableSources();
        for (AiAudioSource.Descriptor candidate : all) {
            AiAudioSource resolved = resolve(candidate.id());
            if (resolved != null) {
                return resolved;
            }
        }
        return new AndroidMicAudioSource(context);
    }

    @Nullable
    @Override
    public AiAudioSource resolve(@NonNull String sourceId) {
        Objects.requireNonNull(sourceId, "sourceId");
        AiAudioSource external = externalSources.get(sourceId);
        if (external != null) {
            return external;
        }
        if (AndroidMicAudioSource.DEFAULT_BUILTIN_ID.equals(sourceId)) {
            return new AndroidMicAudioSource(context);
        }
        for (SystemEntry entry : systemEntries()) {
            if (entry.descriptor().id().equals(sourceId)) {
                return new AndroidMicAudioSource(context, entry.descriptor(), entry.device());
            }
        }
        return null;
    }

    @Override
    public void registerExternal(@NonNull AiAudioSource source) {
        Objects.requireNonNull(source, "source");
        externalSources.put(source.descriptor().id(), source);
        notifyObservers();
    }

    @Override
    public void unregisterExternal(@NonNull String sourceId) {
        Objects.requireNonNull(sourceId, "sourceId");
        if (externalSources.remove(sourceId) != null) {
            notifyObservers();
        }
    }

    @Override
    public void addObserver(@NonNull Observer observer) {
        Objects.requireNonNull(observer, "observer");
        boolean first;
        synchronized (callbackLock) {
            observers.add(observer);
            first = !deviceCallbackRegistered;
            if (first && audioManager != null) {
                try {
                    if (mainHandler == null) {
                        mainHandler = new Handler(Looper.getMainLooper());
                    }
                    audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler);
                    deviceCallbackRegistered = true;
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    @Override
    public void removeObserver(@NonNull Observer observer) {
        Objects.requireNonNull(observer, "observer");
        synchronized (callbackLock) {
            observers.remove(observer);
            if (observers.isEmpty() && deviceCallbackRegistered && audioManager != null) {
                try {
                    audioManager.unregisterAudioDeviceCallback(deviceCallback);
                } catch (RuntimeException ignored) {
                }
                deviceCallbackRegistered = false;
            }
        }
    }

    private void notifyObservers() {
        if (observers.isEmpty()) {
            return;
        }
        List<AiAudioSource.Descriptor> snapshot = availableSources();
        for (Observer observer : observers) {
            try {
                observer.onSourcesChanged(snapshot);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @NonNull
    private List<SystemEntry> systemEntries() {
        if (audioManager == null) {
            return List.of();
        }
        AudioDeviceInfo[] devices;
        try {
            devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        } catch (RuntimeException failed) {
            return List.of();
        }
        if (devices == null || devices.length == 0) {
            return List.of();
        }
        List<SystemEntry> entries = new ArrayList<>(devices.length);
        for (AudioDeviceInfo device : devices) {
            if (device == null) {
                continue;
            }
            AiAudioSource.Kind kind = classify(device);
            if (kind == null) {
                continue;
            }
            String label = labelOf(device, kind);
            int preferredRate = preferredRateOf(device, kind);
            AiAudioSource.Descriptor descriptor = new AiAudioSource.Descriptor(
                    DEVICE_ID_PREFIX + device.getId(), kind, label, preferredRate);
            entries.add(new SystemEntry(descriptor, device));
        }
        return entries;
    }

    @Nullable
    private static AiAudioSource.Kind classify(@NonNull AudioDeviceInfo device) {
        int type = device.getType();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
            return AiAudioSource.Kind.BLUETOOTH_BLE;
        }
        return switch (type) {
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AiAudioSource.Kind.BLUETOOTH_SCO;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET -> AiAudioSource.Kind.WIRED_HEADSET;
            case AudioDeviceInfo.TYPE_USB_HEADSET,
                 AudioDeviceInfo.TYPE_USB_DEVICE,
                 AudioDeviceInfo.TYPE_USB_ACCESSORY -> AiAudioSource.Kind.USB_AUDIO;
            case AudioDeviceInfo.TYPE_BUILTIN_MIC -> AiAudioSource.Kind.BUILTIN_MIC;
            default -> null;
        };
    }

    @NonNull
    private static String labelOf(@NonNull AudioDeviceInfo device, @NonNull AiAudioSource.Kind kind) {
        CharSequence name = device.getProductName();
        String product = name == null ? "" : name.toString().strip();
        if (!product.isEmpty()) {
            return product;
        }
        return switch (kind) {
            case BLUETOOTH_BLE -> "Bluetooth LE Headset";
            case BLUETOOTH_SCO -> "Bluetooth SCO Headset";
            case USB_AUDIO -> "USB Audio Input";
            case WIRED_HEADSET -> "Wired Headset";
            case BUILTIN_MIC -> "Built-in Mic";
            case EXTERNAL_PCM -> "External PCM";
        };
    }

    private static int preferredRateOf(@NonNull AudioDeviceInfo device,
                                       @NonNull AiAudioSource.Kind kind) {
        int fallback = switch (kind) {
            case WIRED_HEADSET, USB_AUDIO -> 48_000;
            default -> 16_000;
        };
        int[] rates = device.getSampleRates();
        if (rates == null || rates.length == 0) {
            return fallback;
        }
        for (int r : rates) {
            if (r == fallback) {
                return r;
            }
        }
        for (int r : rates) {
            if (r > 0) {
                return r;
            }
        }
        return fallback;
    }

    private static int priorityOf(@NonNull AiAudioSource.Kind kind) {
        return switch (kind) {
            case EXTERNAL_PCM -> 0;
            case BLUETOOTH_BLE -> 1;
            case BLUETOOTH_SCO -> 2;
            case USB_AUDIO -> 3;
            case WIRED_HEADSET -> 4;
            case BUILTIN_MIC -> 5;
        };
    }

    private record SystemEntry(AiAudioSource.Descriptor descriptor, AudioDeviceInfo device) {
    }
}
