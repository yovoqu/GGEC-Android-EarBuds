package com.ggec.earbuds.provider.biz_ggec_record.device;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.yovo.biz_ggec_record.api.RecordNearbyObserver;
import com.yovo.biz_ggec_record.api.RecordRuntime;
import com.yovo.biz_ggec_record.api.YoRecordNearby;
import com.yovo.biz_ggec_record.model.RecordNearbyDevice;
import com.yovo.biz_ggec_record.model.RecordScanFailure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Record 扫描链路适配：只负责附近发现投影为 {@link ScanResult}。
 * <p>
 * 方案 A：真正的音视频连接由扫描页分流到 Record UI，不经 {@link #connect} /
 * {@code DeviceSession}。本类 {@code connect} 显式失败，防止误走会话路径。
 */
public final class RecordDeviceLinkAdapter implements DeviceLinkPort {

    private final RecordRuntime runtime;
    private final YoRecordNearby nearby;
    private final CopyOnWriteArrayList<Consumer<DeviceSnapshot>> listeners =
            new CopyOnWriteArrayList<>();

    private volatile boolean scanning;
    @Nullable
    private Consumer<List<ScanResult>> resultsSink;
    @Nullable
    private Consumer<DeviceFailure> failureSink;
    @Nullable
    private List<ScanResult> lastBatch = List.of();

    public RecordDeviceLinkAdapter(@NonNull RecordRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.nearby = runtime.nearby();
    }

    @NonNull
    @Override
    public DeviceSnapshot snapshot() {
        return DeviceSnapshot.idle();
    }

    @NonNull
    @Override
    public Subscription observe(@NonNull Consumer<DeviceSnapshot> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        listener.accept(DeviceSnapshot.idle());
        return () -> listeners.remove(listener);
    }

    @Override
    public void connect(@NonNull DeviceTarget target, @NonNull Consumer<ConnectSettlement> onSettled) {
        Objects.requireNonNull(onSettled, "onSettled");
        // 录播连接由扫描页 OpenRecord 分流，不得经 DeviceSession。
        onSettled.accept(new ConnectSettlement.Failed(DeviceFailure.UNSUPPORTED_CAPABILITY));
    }

    @Override
    public void disconnect() {
        // no-op：本端点不持有 DeviceSession 链路
    }

    @Override
    public void filterModels(@NonNull Set<String> modelIds) {
        nearby.filterModels(Objects.requireNonNull(modelIds, "modelIds"));
    }

    @Override
    public void startScanBatch(@NonNull Consumer<List<ScanResult>> onResults,
                               @NonNull Consumer<DeviceFailure> onFailed,
                               @NonNull Runnable onFinished,
                               @NonNull Consumer<ScanResult> onRemoved) {
        Objects.requireNonNull(onResults, "onResults");
        Objects.requireNonNull(onFailed, "onFailed");
        Objects.requireNonNull(onFinished, "onFinished");
        Objects.requireNonNull(onRemoved, "onRemoved");
        if (scanning) {
            return;
        }
        scanning = true;
        resultsSink = onResults;
        failureSink = onFailed;
        lastBatch = List.of();
        nearby.start(new RecordNearbyObserver() {
            @Override
            public void onDevicesChanged(@NonNull List<RecordNearbyDevice> devices) {
                List<ScanResult> batch = mapBatch(devices);
                List<ScanResult> previous = lastBatch;
                lastBatch = batch;
                for (ScanResult old : previous) {
                    boolean stillThere = false;
                    for (ScanResult next : batch) {
                        if (old.sameDevice(next)) {
                            stillThere = true;
                            break;
                        }
                    }
                    if (!stillThere) {
                        onRemoved.accept(old);
                    }
                }
                onResults.accept(batch);
            }

            @Override
            public void onScanFailed(@NonNull RecordScanFailure failure) {
                scanning = false;
                resultsSink = null;
                Consumer<DeviceFailure> sink = failureSink;
                failureSink = null;
                if (sink != null) {
                    sink.accept(mapFailure(failure));
                }
                onFinished.run();
            }
        });
    }

    @Override
    public void stopScan() {
        if (!scanning && !nearby.isScanning()) {
            return;
        }
        nearby.stop();
        scanning = false;
        resultsSink = null;
        failureSink = null;
        lastBatch = List.of();
    }

    @Override
    public boolean isScanning() {
        return scanning || nearby.isScanning();
    }

    @Override
    public void write(@NonNull String capabilityId, @NonNull ControlValue value,
                      @NonNull Consumer<DeviceWriteResult> callback) {
        callback.accept(new DeviceWriteResult.Failed(DeviceFailure.UNSUPPORTED_CAPABILITY));
    }

    @Override
    public void perform(@NonNull DeviceAction action,
                        @NonNull Consumer<DeviceWriteResult> callback) {
        callback.accept(new DeviceWriteResult.Failed(DeviceFailure.UNSUPPORTED_CAPABILITY));
    }

    @Override
    public void close() {
        stopScan();
        listeners.clear();
        runtime.close();
    }

    @NonNull
    private static List<ScanResult> mapBatch(@NonNull List<RecordNearbyDevice> devices) {
        List<ScanResult> batch = new ArrayList<>(devices.size());
        for (RecordNearbyDevice device : devices) {
            batch.add(new ScanResult(
                    device.modelId(),
                    device.name(),
                    device.macAddress(),
                    device.rssi(),
                    ""));
        }
        return List.copyOf(batch);
    }

    @NonNull
    private static DeviceFailure mapFailure(@NonNull RecordScanFailure failure) {
        return switch (failure) {
            case BLUETOOTH_DISABLED -> DeviceFailure.BLUETOOTH_DISABLED;
            case PERMISSION_DENIED -> DeviceFailure.PERMISSION_DENIED;
            case BLE_UNSUPPORTED -> DeviceFailure.BLE_UNSUPPORTED;
            case THROTTLED -> DeviceFailure.SCAN_THROTTLED;
            case FAILED -> DeviceFailure.SCAN_FAILED;
        };
    }
}
