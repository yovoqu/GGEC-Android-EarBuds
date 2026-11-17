package com.ggec.earbuds.provider.biz_ggec_wy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.yovo.biz_ggec_wy.YoWySdk;
import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.api.WyDevice;
import com.yovo.biz_ggec_wy.api.WyScan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 把 {@code biz_ggec_wy} 的扫描订阅与会话投影成 APP 的设备链路端口。
 */
public final class WyDeviceLinkAdapter implements DeviceLinkPort {

    private final YoWySdk sdk;
    private final CopyOnWriteArrayList<Consumer<DeviceSnapshot>> listeners =
            new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private final Observer observer = new Observer();

    private volatile DeviceSnapshot current = DeviceSnapshot.idle();

    @Nullable
    private WyDevice.Snapshot sessionSnapshot;
    private String targetModelId = "";
    private WyDevice.Radios targetRadios = WyDevice.Radios.none();
    private boolean connectSawLink;
    private Consumer<ConnectSettlement> connectSettlement;

    public WyDeviceLinkAdapter(@NonNull YoWySdk sdk) {
        this.sdk = Objects.requireNonNull(sdk, "sdk");
        this.sdk.watch(observer);
    }

    @NonNull
    @Override
    public DeviceSnapshot snapshot() {
        return current;
    }

    @NonNull
    @Override
    public Subscription observe(Consumer<DeviceSnapshot> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("observe requires a listener");
        }
        listeners.add(listener);
        listener.accept(current);
        return () -> listeners.remove(listener);
    }

    @Override
    public void connect(DeviceTarget target, Consumer<ConnectSettlement> onSettled) {
        Objects.requireNonNull(target, "target");
        if (onSettled == null) {
            throw new IllegalArgumentException("connect requires a settlement callback");
        }
        String classicAddr = target.id().address();
        String classicName = !target.identity().classicName().isEmpty()
                ? target.identity().classicName()
                : (!target.name().isEmpty() ? target.name() : target.id().modelId());
        WyDevice.Radios radios = new WyDevice.Radios(classicAddr, classicName);
        synchronized (lock) {
            targetModelId = target.id().modelId();
            targetRadios = radios;
            clearSessionFactsLocked();
            connectSawLink = false;
            connectSettlement = onSettled;
        }
        sdk.connect(targetModelId, radios);
        synchronized (lock) {
            applyConnectionLocked(sdk.connection());
        }
        publish();
    }

    @Override
    public void disconnect() {
        synchronized (lock) {
            connectSettlement = null;
            connectSawLink = false;
            clearSessionFactsLocked();
        }
        sdk.disconnect();
        synchronized (lock) {
            applyConnectionLocked(sdk.connection());
        }
        publish();
    }

    @Override
    public void startScanBatch(@NonNull Consumer<List<ScanResult>> onResults,
                               @NonNull Consumer<DeviceFailure> onFailed,
                               @NonNull Runnable onFinished,
                               @NonNull Consumer<ScanResult> onRemoved) {
        sdk.startScan(new WyScan.Listener() {
            @Override
            public void onUpdated(@NonNull List<WyScan.Found> devices) {
                List<ScanResult> mapped = new ArrayList<>(devices.size());
                for (WyScan.Found device : devices) {
                    mapped.add(toScanResult(device));
                }
                onResults.accept(List.copyOf(mapped));
            }

            @Override
            public void onLost(@NonNull WyScan.Found device) {
                onRemoved.accept(toScanResult(device));
            }

            @Override
            public void onFailed(@NonNull WyControl.Failure failure) {
                onFailed.accept(WyValueMapper.toFailure(failure));
            }

            @Override
            public void onFinished() {
                onFinished.run();
            }
        });
    }

    @Override
    public void stopScan() {
        sdk.stopScan();
    }

    @Override
    public boolean isScanning() {
        return sdk.scanning();
    }

    @Override
    public void filterModels(Set<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            sdk.filter(WyScan.Rule.none());
            return;
        }
        sdk.filter(new WyScan.Rule(Set.of(), Set.of(), Set.copyOf(modelIds),
                WyScan.Rule.MIN_RSSI_UNRESTRICTED));
    }

    @Override
    public void write(String capabilityId, ControlValue value,
                      Consumer<DeviceWriteResult> callback) {
        WyDevice.Capability capability = WyValueMapper.capabilityOf(capabilityId);
        sdk.write(capability, WyValueMapper.toWyControlValue(value),
                result -> callback.accept(WyValueMapper.toWriteResult(result)));
    }

    @Override
    public void perform(DeviceAction action, Consumer<DeviceWriteResult> callback) {
        sdk.perform(WyValueMapper.toWyAction(action),
                result -> callback.accept(WyValueMapper.toWriteResult(result)));
    }

    @Override
    public void close() {
        sdk.unwatch(observer);
        sdk.stopScan();
        listeners.clear();
    }

    @NonNull
    private static ScanResult toScanResult(@NonNull WyScan.Found device) {
        WyDevice.Radios radios = device.radios();
        String name = !radios.classicName().isEmpty() ? radios.classicName() : device.modelId();
        return new ScanResult(device.modelId(), name, radios.classicAddress(), device.rssi());
    }

    @NonNull
    private static ConnectionState toConnectionState(@Nullable WyDevice.Snapshot snapshot) {
        if (snapshot == null) {
            return ConnectionState.IDLE;
        }
        return switch (snapshot.connection()) {
            case IDLE -> ConnectionState.IDLE;
            case CONNECTING -> ConnectionState.CONNECTING;
            case READY -> ConnectionState.READY;
            case DISCONNECTED -> ConnectionState.DISCONNECTED;
        };
    }

    @NonNull
    private static BatterySnapshot toBattery(@Nullable WyDevice.Snapshot snapshot) {
        if (snapshot == null) {
            return BatterySnapshot.unknown();
        }
        WyDevice.Battery battery = snapshot.battery();
        return new BatterySnapshot(battery.leftLevel(), battery.rightLevel(),
                battery.caseLevel(), battery.caseCharging());
    }

    @NonNull
    private static WyDevice.Info deviceInfoOf(@Nullable WyDevice.Snapshot snapshot) {
        return snapshot == null ? WyDevice.Info.unknown() : snapshot.deviceInfo();
    }

    @NonNull
    private static DeviceState toDeviceState(@Nullable WyDevice.Snapshot snapshot) {
        if (snapshot == null) {
            return new DeviceState(Map.of());
        }
        Map<String, ControlValue> values = new LinkedHashMap<>();
        return new DeviceState(values);
    }

    private void publish() {
        DeviceSnapshot next;
        synchronized (lock) {
            WyDevice.Snapshot snapshot = sessionSnapshot;
            WyDevice.Info info = deviceInfoOf(snapshot);
            next = new DeviceSnapshot(
                    toConnectionState(snapshot),
                    targetModelId.isEmpty() ? null : targetModelId,
                    toBattery(snapshot),
                    WearState.UNKNOWN,
                    info.firmware(),
                    "",
                    "",
                    targetRadios.classicAddress(),
                    targetRadios.classicName(),
                    -1,
                    toDeviceState(snapshot),
                    Optional.empty(),
                    List.of());
        }
        current = next;
        for (Consumer<DeviceSnapshot> listener : listeners) {
            listener.accept(next);
        }
    }

    private void clearSessionFactsLocked() {
        sessionSnapshot = null;
    }

    private void applyConnectionLocked(WyControl.State state) {
        WyDevice.Snapshot prev = sessionSnapshot;
        WyDevice.Battery battery = prev == null ? WyDevice.Battery.unknown() : prev.battery();
        WyDevice.Info info = prev == null ? WyDevice.Info.unknown() : prev.deviceInfo();
        Map<WyDevice.Capability, WyControl.Value> values = prev == null ? Map.of() : prev.values();
        sessionSnapshot = new WyDevice.Snapshot(state, battery, info, values);
    }

    private final class Observer implements WyControl.Listener {

        @Override
        public void onConnectionChanged(@NonNull WyControl.State state, @Nullable WyControl.Failure failure) {
            Consumer<ConnectSettlement> callback = null;
            ConnectSettlement outcome = null;
            synchronized (lock) {
                applyConnectionLocked(state);
                if (connectSettlement != null) {
                    if (state == WyControl.State.CONNECTING) {
                        connectSawLink = true;
                    } else if (state == WyControl.State.READY) {
                        callback = connectSettlement;
                        connectSettlement = null;
                        outcome = new ConnectSettlement.Ready();
                    } else if (state == WyControl.State.DISCONNECTED && failure != null) {
                        callback = connectSettlement;
                        connectSettlement = null;
                        outcome = new ConnectSettlement.Failed(WyValueMapper.toFailure(failure));
                    } else if (state == WyControl.State.DISCONNECTED && connectSawLink) {
                        callback = connectSettlement;
                        connectSettlement = null;
                        outcome = new ConnectSettlement.Failed(DeviceFailure.DEVICE_ERROR);
                    }
                }
            }
            publish();
            if (callback != null) {
                callback.accept(outcome);
            }
        }

        @Override
        public void onSnapshotChanged(@NonNull WyDevice.Snapshot snapshot) {
            synchronized (lock) {
                sessionSnapshot = snapshot;
            }
            publish();
        }

        @Override
        public void onDeviceEvent(@NonNull WyControl.Event event) {
            publish();
        }
    }
}
