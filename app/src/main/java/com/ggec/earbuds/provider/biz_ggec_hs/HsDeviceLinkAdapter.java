package com.ggec.earbuds.provider.biz_ggec_hs;

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
import com.ggec.earbuds.domain.device.model.DualConnectionView;
import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.yovo.biz_ggec_hs.YoHsSdk;
import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsScan;

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
 * 把 {@code biz_ggec_hs} 的扫描订阅与会话投影成 APP 的设备链路端口。
 *
 * <p>本类只做「biz 事件 → {@link DeviceSnapshot} / {@link ScanResult}」的投影与
 * SDK 生命周期持有：扫描参数、广播识别、协议帧、能力路由、连接就绪超时都在 biz 内部。
 * 这里不解释协议字节，不持有「机型 → 能力」表，不做重试，也不判定"谁被取代"
 * （换设备的仲裁在 {@code domain/device/session/DeviceSession}）。</p>
 *
 * <p>会话事实（电量、设备信息、控制值、双连接、录音列表）只在<b>本会话</b>内有效：
 * 连接与断开都把它们清空，绝不把上一台设备的值带到新会话。</p>
 */
public final class HsDeviceLinkAdapter implements DeviceLinkPort {

    private final YoHsSdk sdk;
    private final CopyOnWriteArrayList<Consumer<DeviceSnapshot>> listeners =
            new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private final Observer observer = new Observer();

    private volatile DeviceSnapshot current = DeviceSnapshot.idle();

    /** 本会话最近一次 biz 快照；未连接时为 null，表示「本会话没有任何设备事实」。 */
    @Nullable
    private HsDevice.Snapshot sessionSnapshot;
    private String targetModelId = "";
    private HsDevice.Radios targetRadios = HsDevice.Radios.none();
    /** 当前连接尝试是否已经看到 CONNECTING。用来区分掉线与"还没开始"。 */
    private boolean connectSawLink;
    private Consumer<ConnectSettlement> connectSettlement;
    private Optional<DualConnectionView> dualConnection = Optional.empty();
    private List<RecordFileView> recordFiles = List.of();

    public HsDeviceLinkAdapter(@NonNull YoHsSdk sdk) {
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
        HsDevice.Radios radios = new HsDevice.Radios(
                target.id().bleAddress(),
                target.identity().bleName(),
                target.identity().classicAddress(),
                target.identity().classicName());
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
        sdk.startScan(new HsScan.Listener() {
            @Override
            public void onUpdated(@NonNull List<HsScan.Found> devices) {
                List<ScanResult> mapped = new ArrayList<>(devices.size());
                for (HsScan.Found device : devices) {
                    mapped.add(toScanResult(device));
                }
                onResults.accept(List.copyOf(mapped));
            }

            @Override
            public void onLost(@NonNull HsScan.Found device) {
                onRemoved.accept(toScanResult(device));
            }

            @Override
            public void onFailed(@NonNull HsControl.Failure failure) {
                onFailed.accept(HsValueMapper.toFailure(failure));
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
            sdk.filter(HsScan.Rule.none());
            return;
        }
        sdk.filter(new HsScan.Rule(Set.of(), Set.of(), Set.copyOf(modelIds),
                false, HsScan.Rule.MIN_RSSI_UNRESTRICTED));
    }

    @Override
    public void write(String capabilityId, ControlValue value,
                      Consumer<DeviceWriteResult> callback) {
        HsDevice.Capability capability = HsValueMapper.capabilityOf(capabilityId);
        sdk.write(capability, HsValueMapper.toHsControlValue(value),
                result -> callback.accept(HsValueMapper.toWriteResult(result)));
    }

    @Override
    public void perform(DeviceAction action, Consumer<DeviceWriteResult> callback) {
        sdk.perform(HsValueMapper.toHsAction(action),
                result -> callback.accept(HsValueMapper.toWriteResult(result)));
    }

    @Override
    public void close() {
        sdk.unwatch(observer);
        sdk.stopScan();
        listeners.clear();
    }

    @NonNull
    private static ScanResult toScanResult(@NonNull HsScan.Found device) {
        HsDevice.Radios radios = device.radios();
        return new ScanResult(device.modelId(), radios.bleAddress(), radios.bleName(),
                radios.classicAddress(), radios.classicName(), device.rssi());
    }

    /**
     * 由耳机在仓状态推导佩戴状态，唯一规则。
     *
     * <p>推导方向只有「不在耳朵上」这一侧成立：{@link HsDevice.Ear#BOTH} 时两耳都在充电盒里，
     * 确知未佩戴。反方向不成立——有耳出仓只说明它离开了充电盒，也可能放在桌上，协议没有任何一帧
     * 能证明「戴在耳朵上」，所以 {@code NONE}/{@code LEFT}/{@code RIGHT}/{@code UNKNOWN} 一律投未知。
     * 把缺失的数据补成结论，就是替协议编造事实。</p>
     */
    @NonNull
    private static WearState wearStateOf(@NonNull HsDevice.Ear earInCase) {
        return switch (earInCase) {
            case BOTH -> WearState.NOT_WORN;
            case UNKNOWN, NONE, LEFT, RIGHT -> WearState.UNKNOWN;
        };
    }

    @NonNull
    private static ConnectionState toConnectionState(@Nullable HsDevice.Snapshot snapshot) {
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
    private static BatterySnapshot toBattery(@Nullable HsDevice.Snapshot snapshot) {
        if (snapshot == null) {
            return BatterySnapshot.unknown();
        }
        HsDevice.Battery battery = snapshot.battery();
        return new BatterySnapshot(battery.leftLevel(), battery.rightLevel(),
                battery.caseLevel(), battery.caseCharging());
    }

    @NonNull
    private static HsDevice.Info deviceInfoOf(@Nullable HsDevice.Snapshot snapshot) {
        return snapshot == null
                ? new HsDevice.Info(HsDevice.Info.UNKNOWN_FIRMWARE, HsDevice.Info.UNKNOWN_EAR_COLOR,
                        HsDevice.Ear.UNKNOWN)
                : snapshot.deviceInfo();
    }

    @NonNull
    private static DeviceState toDeviceState(@Nullable HsDevice.Snapshot snapshot) {
        if (snapshot == null) {
            return new DeviceState(Map.of());
        }
        Map<String, ControlValue> values = new LinkedHashMap<>();
        for (Map.Entry<HsDevice.Capability, HsControl.Value> entry
                : snapshot.values().entrySet()) {
            values.put(entry.getKey().name(), HsValueMapper.toControlValue(entry.getValue()));
        }
        return new DeviceState(values);
    }

    private void publish() {
        DeviceSnapshot next;
        synchronized (lock) {
            HsDevice.Snapshot snapshot = sessionSnapshot;
            HsDevice.Info info = deviceInfoOf(snapshot);
            next = new DeviceSnapshot(
                    toConnectionState(snapshot),
                    targetModelId.isEmpty() ? null : targetModelId,
                    toBattery(snapshot),
                    wearStateOf(info.earInCase()),
                    info.firmware(),
                    targetRadios.bleAddress(),
                    targetRadios.bleName(),
                    targetRadios.classicAddress(),
                    targetRadios.classicName(),
                    info.earColor(),
                    toDeviceState(snapshot),
                    dualConnection,
                    recordFiles);
        }
        current = next;
        for (Consumer<DeviceSnapshot> listener : listeners) {
            listener.accept(next);
        }
    }

    /** 调用方须持锁。新会话 / 断开后回到「本会话没有任何设备事实」。 */
    private void clearSessionFactsLocked() {
        sessionSnapshot = null;
        dualConnection = Optional.empty();
        recordFiles = List.of();
    }

    private final class Observer implements HsControl.Listener {

        @Override
        public void onConnectionChanged(@NonNull HsControl.State state, @Nullable HsControl.Failure failure) {
            Consumer<ConnectSettlement> callback = null;
            ConnectSettlement outcome = null;
            synchronized (lock) {
                applyConnectionLocked(state);
                if (connectSettlement != null) {
                    if (state == HsControl.State.CONNECTING) {
                        connectSawLink = true;
                    } else if (state == HsControl.State.READY) {
                        callback = connectSettlement;
                        connectSettlement = null;
                        outcome = new ConnectSettlement.Ready();
                    } else if (state == HsControl.State.DISCONNECTED && failure != null) {
                        callback = connectSettlement;
                        connectSettlement = null;
                        outcome = new ConnectSettlement.Failed(HsValueMapper.toFailure(failure));
                    } else if (state == HsControl.State.DISCONNECTED && connectSawLink) {
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
        public void onSnapshotChanged(@NonNull HsDevice.Snapshot snapshot) {
            synchronized (lock) {
                sessionSnapshot = snapshot;
            }
            publish();
        }

        @Override
        public void onDeviceEvent(@NonNull HsControl.Event event) {
            switch (event) {
                case HsControl.Event.DualConnectionChanged changed -> {
                    synchronized (lock) {
                        dualConnection = Optional.of(
                                HsValueMapper.toDualConnectionView(changed.state()));
                    }
                    publish();
                }
                case HsControl.Event.PairedDevicesChanged changed -> {
                    synchronized (lock) {
                        // 只有拿过完整双连接状态之后才更新列表：单独一份列表填不满开关与优先设备字段，
                        // 用假值补齐就是替设备编造状态。
                        if (dualConnection.isPresent()) {
                            DualConnectionView previous = dualConnection.get();
                            dualConnection = Optional.of(new DualConnectionView(
                                    previous.dualEnabled(), previous.priorityAddress(),
                                    HsValueMapper.toPairedDeviceViews(changed.devices())));
                        }
                    }
                    publish();
                }
                case HsControl.Event.RecordFilesChanged changed -> {
                    synchronized (lock) {
                        recordFiles = HsValueMapper.toRecordFileViews(changed.files());
                    }
                    publish();
                }
                case HsControl.Event.DeviceInfoChanged changed -> {
                    synchronized (lock) {
                        HsDevice.Snapshot snapshot = sessionSnapshot;
                        sessionSnapshot = new HsDevice.Snapshot(
                                snapshot == null ? HsControl.State.IDLE : snapshot.connection(),
                                snapshot == null ? unknownBattery() : snapshot.battery(),
                                changed.info(),
                                snapshot == null ? Map.of() : snapshot.values());
                    }
                    publish();
                }
                // 其余事件（服务状态、远程停止录音、查找耳机、新手模式、换耳、语音助手）
                // 在 app 契约里由能力控制值承载，随 onSnapshotChanged 的 values 一起到达；
                // 它们没有独立的快照字段，因此不在这里另存一份状态。
                case HsControl.Event.ServiceStatusChanged ignored -> {
                }
                case HsControl.Event.RemoteStreamStop ignored -> {
                }
                case HsControl.Event.FindAlertChanged ignored -> {
                }
                case HsControl.Event.BeginnerModeChanged ignored -> {
                }
                case HsControl.Event.EarExchangeChanged ignored -> {
                }
                case HsControl.Event.VoiceAssistantChanged ignored -> {
                }
            }
        }
    }

    /**
     * 调用方须持锁。连接态变化只改连接态本身；连接结束（{@link HsControl.State#DISCONNECTED}）
     * 同时失效本会话的设备事实，避免断开后仍显示上一台设备的双连接列表与录音列表。
     */
    private void applyConnectionLocked(@NonNull HsControl.State state) {
        HsDevice.Snapshot previous = sessionSnapshot;
        sessionSnapshot = new HsDevice.Snapshot(
                state,
                previous == null ? unknownBattery() : previous.battery(),
                deviceInfoOf(previous),
                previous == null ? Map.of() : previous.values());
        if (state == HsControl.State.DISCONNECTED) {
            dualConnection = Optional.empty();
            recordFiles = List.of();
        }
    }

    @NonNull
    private static HsDevice.Battery unknownBattery() {
        return new HsDevice.Battery(HsDevice.Battery.UNKNOWN_LEVEL, HsDevice.Battery.UNKNOWN_LEVEL,
                HsDevice.Battery.UNKNOWN_LEVEL, false, HsDevice.Battery.UNKNOWN_LEVEL);
    }
}
