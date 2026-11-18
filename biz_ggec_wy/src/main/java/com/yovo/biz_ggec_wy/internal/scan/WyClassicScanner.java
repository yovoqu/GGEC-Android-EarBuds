package com.yovo.biz_ggec_wy.internal.scan;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.api.WyScan;
import com.yovo.biz_ggec_wy.internal.center.port.WyClassicRadioPort;
import com.yovo.biz_ggec_wy.provider.lib_common.log.WyLog;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 经典蓝牙扫描器：按 {@code WY01} 前缀过滤设备，维护附近设备表并派发视图与事件。
 */
public final class WyClassicScanner {

    private static final String TAG = "WyClassicScanner";
    public static final String DEVICE_PREFIX = "WY01";

    public interface MainDispatcher {
        void post(@NonNull Runnable r);
    }

    private final WyClassicRadioPort radio;
    private final MainDispatcher mainDispatcher;
    private final WyScanSession scanSession = new WyScanSession();
    private final WyNearbyRegistry registry = new WyNearbyRegistry();

    @Nullable
    private volatile WyScan.Listener currentListener;
    private volatile WyScan.Rule currentRule = WyScan.Rule.none();
    private volatile long lastFrameAtMillis;

    public WyClassicScanner(@NonNull WyClassicRadioPort radio) {
        this(radio, createDefaultDispatcher());
    }

    public WyClassicScanner(@NonNull WyClassicRadioPort radio,
                            @NonNull MainDispatcher mainDispatcher) {
        this.radio = Objects.requireNonNull(radio, "radio");
        this.mainDispatcher = Objects.requireNonNull(mainDispatcher, "mainDispatcher");
    }

    private static MainDispatcher createDefaultDispatcher() {
        if (Looper.getMainLooper() != null) {
            Handler handler = new Handler(Looper.getMainLooper());
            return handler::post;
        }
        return Runnable::run;
    }

    public boolean isScanning() {
        return scanSession.isScanning();
    }

    public void start(@NonNull WyScan.Listener listener) {
        Objects.requireNonNull(listener, "listener");
        stop();
        this.currentListener = listener;
        registry.clear();
        long generation = scanSession.begin();
        WyLog.i(TAG, "启动经典蓝牙扫描，generation=" + generation);

        // 仅在当前规则接受 WY01 机型时，才上报系统已配对设备（属于 SPP 协议）
        reportBondedDevices(generation);

        radio.startDiscovery(new WyClassicRadioPort.DiscoveryListener() {
            @Override
            public void onDeviceDiscovered(@NonNull String name, @NonNull String address,
                                           int rssi, long timestampMillis) {
                if (!scanSession.isCurrent(generation)) {
                    return;
                }
                // 过滤设备：前缀为 WY01（忽略大小写），不以 WY01 开头的设备被忽略
                if (name == null || !name.trim().toUpperCase(Locale.US).startsWith(DEVICE_PREFIX)) {
                    return;
                }
                lastFrameAtMillis = timestampMillis;
                registry.update(DEVICE_PREFIX, address, name.trim(), rssi, timestampMillis);
                List<WyScan.Found> foundList = view().devices();
                mainDispatcher.post(() -> {
                    WyScan.Listener l = currentListener;
                    if (l != null && scanSession.isCurrent(generation)) {
                        l.onUpdated(foundList);
                    }
                });
            }

            @Override
            public void onDiscoveryFinished() {
                if (scanSession.end(generation)) {
                    WyLog.i(TAG, "扫描完成，generation=" + generation);
                    mainDispatcher.post(() -> {
                        WyScan.Listener l = currentListener;
                        if (l != null) {
                            l.onFinished();
                        }
                    });
                }
            }

            @Override
            public void onDiscoveryFailed(@NonNull WyControl.Failure failure) {
                if (scanSession.end(generation)) {
                    WyLog.e(TAG, "扫描失败，generation=" + generation + ", failure=" + failure);
                    mainDispatcher.post(() -> {
                        WyScan.Listener l = currentListener;
                        if (l != null) {
                            l.onFailed(failure);
                        }
                    });
                }
            }
        });
    }

    public void stop() {
        if (scanSession.isScanning()) {
            WyLog.i(TAG, "停止经典蓝牙扫描");
        }
        scanSession.stop();
        radio.stopDiscovery();
    }

    private boolean isModelAccepted(@NonNull String modelId) {
        WyScan.Rule rule = currentRule;
        if (rule == null || rule.modelIds() == null || rule.modelIds().isEmpty()) {
            return true;
        }
        return rule.modelIds().contains(modelId);
    }

    private void reportBondedDevices(long generation) {
        if (!isModelAccepted(DEVICE_PREFIX)) {
            return;
        }
        List<WyClassicRadioPort.BondedDevice> bondedList = radio.getBondedDevices();
        if (bondedList == null || bondedList.isEmpty()) {
            return;
        }
        boolean hasUpdate = false;
        long now = System.currentTimeMillis();
        for (WyClassicRadioPort.BondedDevice dev : bondedList) {
            String name = dev.name();
            String address = dev.address();
            if (name == null || !name.trim().toUpperCase(Locale.US).startsWith(DEVICE_PREFIX)) {
                continue;
            }
            // 已配对设备（不论已连接或未连接）均上报，便于发起 SPP 连接
            registry.update(DEVICE_PREFIX, address, name.trim(), 0, now);
            hasUpdate = true;
        }
        if (hasUpdate) {
            List<WyScan.Found> foundList = view().devices();
            mainDispatcher.post(() -> {
                WyScan.Listener l = currentListener;
                if (l != null && scanSession.isCurrent(generation)) {
                    l.onUpdated(foundList);
                }
            });
        }
    }

    public void setFilterRule(@NonNull WyScan.Rule rule) {
        this.currentRule = Objects.requireNonNull(rule, "rule");
        WyScan.Listener l = currentListener;
        if (l != null) {
            if (scanSession.isScanning()) {
                reportBondedDevices(scanSession.currentGeneration());
            }
            List<WyScan.Found> found = view().devices();
            mainDispatcher.post(() -> l.onUpdated(found));
        }
    }

    @NonNull
    public WyScan.View view() {
        return registry.buildView(isScanning(), currentRule);
    }

    @NonNull
    public WyScan.Health health() {
        return new WyScan.Health(isScanning(), false, false, lastFrameAtMillis);
    }
}
