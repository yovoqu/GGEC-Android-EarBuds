package com.ggec.earbuds.wiring.device;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.DeviceTransport;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.RecordOutcome;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.record.RecordFailure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 设备网关：所有端点的唯一路由者。
 *
 * <p><b>路由状态只有一个：</b>{@link #connectedTransport}。{@code connect} 设置它，
 * {@code disconnect} 与连接失败清除它；写入、动作、快照与录音能力都按它选端点。
 * 没有「活跃驱动」这类并行状态，也没有 {@code get(0)} 兜底——查不到端点就是
 * {@link DeviceFailure#DEVICE_NOT_FOUND}。</p>
 *
 * <p><b>扫描是全集行为：</b>批量扫描启动全部端点，结果按地址去重后合并成一批下发；
 * 结束回调只在所有端点都结束时转发一次。{@code filterModels} 把同一份筛选集合原样下发给
 * 每个端点（provider 侧热替换），因此切换筛选<b>不</b>停止或重启扫描。</p>
 *
 * <p><b>快照只转发当前传输：</b>构造期订阅全部端点，但只把 {@code connectedTransport}
 * 对应端点的快照转给上层，另一条链路的上报不会污染当前会话。</p>
 *
 * <p>本类同时是 {@link RecordPort} 的实现：录音能力跟着当前连接设备走。未连接时
 * {@code supportedModes()} 是空集、其余调用以 {@link RecordFailure#NOT_CONNECTED} 结束——
 * 这是「没有当前设备」的显式结果，不是兜底成功。</p>
 */
public final class DeviceGateway implements DeviceLinkPort, RecordPort {

    private final DeviceEndpoints endpoints;
    private final List<Subscription> subscriptions;
    private final CopyOnWriteArrayList<Consumer<DeviceSnapshot>> linkListeners =
            new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private final Object scanLock = new Object();

    /** 唯一连接路由状态。 */
    @Nullable
    private volatile DeviceTransport connectedTransport;

    /** 处于同一批次时各端点最新一批结果的缓存；合并顺序固定按端点装配顺序。 */
    private final Map<DeviceTransport, List<ScanResult>> latestBatches = new LinkedHashMap<>();
    private boolean batchActive;
    private int finishedEndpoints;

    @Nullable
    private Consumer<List<ScanResult>> scanOnResults;
    @Nullable
    private Consumer<DeviceFailure> scanOnFailed;
    @Nullable
    private Runnable scanOnFinished;
    @Nullable
    private Consumer<ScanResult> scanOnRemoved;
    private Set<String> currentFilter = Set.of();

    public DeviceGateway(@NonNull DeviceEndpoints endpoints) {
        this.endpoints = Objects.requireNonNull(endpoints, "endpoints");
        List<Subscription> registered = new ArrayList<>(this.endpoints.all().size());
        for (DeviceEndpoint endpoint : this.endpoints.all()) {
            registered.add(endpoint.link().observe(snapshot -> onEndpointSnapshot(endpoint, snapshot)));
        }
        this.subscriptions = List.copyOf(registered);
    }

    // ==================== DeviceLinkPort ====================

    @NonNull
    @Override
    public DeviceSnapshot snapshot() {
        DeviceEndpoint endpoint = connectedEndpoint();
        return endpoint == null ? DeviceSnapshot.idle() : endpoint.link().snapshot();
    }

    @NonNull
    @Override
    public Subscription observe(@NonNull Consumer<DeviceSnapshot> listener) {
        Objects.requireNonNull(listener, "listener");
        linkListeners.add(listener);
        listener.accept(snapshot());
        return () -> linkListeners.remove(listener);
    }

    @Override
    public void connect(@NonNull DeviceTarget target, @NonNull Consumer<ConnectSettlement> onSettled) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(onSettled, "onSettled");
        DeviceEndpoint endpoint = endpoints.of(target.id().modelId()).orElse(null);
        if (endpoint == null) {
            onSettled.accept(new ConnectSettlement.Failed(DeviceFailure.DEVICE_NOT_FOUND));
            return;
        }
        DeviceEndpoint replaced;
        synchronized (lock) {
            DeviceTransport previous = connectedTransport;
            replaced = previous == null || previous == endpoint.transport()
                    ? null
                    : endpoints.of(previous).orElse(null);
            connectedTransport = endpoint.transport();
        }
        if (replaced != null) {
            // 换传输：先把上一条链路的会话收干净，再建新链路。
            replaced.link().disconnect();
        }
        endpoint.link().connect(target, settlement -> {
            if (settlement instanceof ConnectSettlement.Failed) {
                synchronized (lock) {
                    if (connectedTransport == endpoint.transport()) {
                        connectedTransport = null;
                    }
                }
            }
            onSettled.accept(settlement);
        });
    }

    @Override
    public void disconnect() {
        DeviceEndpoint endpoint;
        synchronized (lock) {
            endpoint = connectedEndpointLocked();
            connectedTransport = null;
        }
        if (endpoint != null) {
            endpoint.link().disconnect();
        }
    }

    @Override
    public void filterModels(@NonNull Set<String> modelIds) {
        Set<String> filter = Set.copyOf(Objects.requireNonNull(modelIds, "modelIds"));
        List<DeviceEndpoint> toStart = new ArrayList<>();
        List<DeviceEndpoint> toStop = new ArrayList<>();
        List<ScanResult> merged = null;
        Consumer<List<ScanResult>> resultsConsumer = null;

        synchronized (scanLock) {
            this.currentFilter = filter;
            for (DeviceEndpoint endpoint : endpoints.all()) {
                boolean active = isEndpointActiveForFilter(endpoint, filter);
                if (active) {
                    endpoint.link().filterModels(filter);
                    if (batchActive && !endpoint.link().isScanning()) {
                        toStart.add(endpoint);
                    }
                } else {
                    if (endpoint.link().isScanning()) {
                        toStop.add(endpoint);
                    }
                    if (latestBatches.remove(endpoint.transport()) != null) {
                        merged = mergeBatchesLocked();
                        resultsConsumer = scanOnResults;
                    }
                }
            }
        }

        for (DeviceEndpoint endpoint : toStop) {
            endpoint.link().stopScan();
        }
        for (DeviceEndpoint endpoint : toStart) {
            Consumer<List<ScanResult>> onResults = scanOnResults;
            Consumer<DeviceFailure> onFailed = scanOnFailed;
            Runnable onFinished = scanOnFinished;
            Consumer<ScanResult> onRemoved = scanOnRemoved;
            if (onResults != null && onFailed != null && onFinished != null && onRemoved != null) {
                endpoint.link().startScanBatch(
                        batch -> onEndpointResults(endpoint.transport(), batch, onResults),
                        onFailed,
                        () -> onEndpointFinished(onFinished),
                        onRemoved);
            }
        }
        if (resultsConsumer != null && merged != null) {
            resultsConsumer.accept(merged);
        }
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
        List<DeviceEndpoint> activeEndpoints = new ArrayList<>();
        synchronized (scanLock) {
            this.scanOnResults = onResults;
            this.scanOnFailed = onFailed;
            this.scanOnFinished = onFinished;
            this.scanOnRemoved = onRemoved;
            this.batchActive = true;
            this.finishedEndpoints = 0;
            this.latestBatches.clear();
            for (DeviceEndpoint endpoint : endpoints.all()) {
                if (isEndpointActiveForFilter(endpoint, currentFilter)) {
                    activeEndpoints.add(endpoint);
                } else {
                    if (endpoint.link().isScanning()) {
                        endpoint.link().stopScan();
                    }
                }
            }
        }
        if (activeEndpoints.isEmpty()) {
            onFinished.run();
            return;
        }
        for (DeviceEndpoint endpoint : activeEndpoints) {
            endpoint.link().startScanBatch(
                    batch -> onEndpointResults(endpoint.transport(), batch, onResults),
                    onFailed,
                    () -> onEndpointFinished(onFinished),
                    onRemoved);
        }
    }

    @Override
    public void stopScan() {
        synchronized (scanLock) {
            batchActive = false;
            scanOnResults = null;
            scanOnFailed = null;
            scanOnFinished = null;
            scanOnRemoved = null;
        }
        for (DeviceEndpoint endpoint : endpoints.all()) {
            endpoint.link().stopScan();
        }
    }

    @Override
    public boolean isScanning() {
        for (DeviceEndpoint endpoint : endpoints.all()) {
            if (endpoint.link().isScanning()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void write(@NonNull String capabilityId, @NonNull ControlValue value,
                      @NonNull Consumer<DeviceWriteResult> callback) {
        Objects.requireNonNull(callback, "callback");
        DeviceEndpoint endpoint = connectedEndpoint();
        if (endpoint == null) {
            callback.accept(new DeviceWriteResult.Failed(DeviceFailure.NOT_READY));
            return;
        }
        endpoint.link().write(capabilityId, value, callback);
    }

    @Override
    public void writeWithProgress(@NonNull String capabilityId, @NonNull ControlValue value,
                                  @NonNull Runnable onStarted,
                                  @NonNull Consumer<DeviceWriteResult> callback) {
        Objects.requireNonNull(onStarted, "onStarted");
        Objects.requireNonNull(callback, "callback");
        DeviceEndpoint endpoint = connectedEndpoint();
        if (endpoint == null) {
            callback.accept(new DeviceWriteResult.Failed(DeviceFailure.NOT_READY));
            return;
        }
        // 把「发送/处理中」中间阶段透到当前端点，生产链路才能驱动 SENDING→PROCESSING→终态。
        endpoint.link().writeWithProgress(capabilityId, value, onStarted, callback);
    }

    @Override
    public void perform(@NonNull DeviceAction action,
                        @NonNull Consumer<DeviceWriteResult> callback) {
        Objects.requireNonNull(callback, "callback");
        DeviceEndpoint endpoint = connectedEndpoint();
        if (endpoint == null) {
            callback.accept(new DeviceWriteResult.Failed(DeviceFailure.NOT_READY));
            return;
        }
        endpoint.link().perform(action, callback);
    }

    @Override
    public void close() {
        stopScan();
        for (Subscription subscription : subscriptions) {
            subscription.unsubscribe();
        }
        linkListeners.clear();
        endpoints.close();
    }

    // ==================== RecordPort ====================

    @NonNull
    @Override
    public Set<RecordMode> supportedModes() {
        RecordPort record = recordPort();
        return record == null ? Set.of() : record.supportedModes();
    }

    @Override
    public void startStreamRecord(@NonNull StreamRecordObserver observer) {
        Objects.requireNonNull(observer, "observer");
        RecordPort record = recordPort();
        if (record == null) {
            observer.onError(RecordFailure.NOT_CONNECTED);
            return;
        }
        record.startStreamRecord(observer);
    }

    @Override
    public void startStreamAudio(@NonNull StreamAudioObserver observer) {
        Objects.requireNonNull(observer, "observer");
        RecordPort record = recordPort();
        if (record == null) {
            observer.onError(RecordFailure.NOT_CONNECTED);
            return;
        }
        record.startStreamAudio(observer);
    }

    @Override
    public void stopStreamRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        RecordPort record = recordPort();
        if (record == null) {
            onComplete.accept(RecordOutcome.failed(RecordFailure.NOT_CONNECTED));
            return;
        }
        record.stopStreamRecord(onComplete);
    }

    @Override
    public boolean isStreamRecording() {
        RecordPort record = recordPort();
        return record != null && record.isStreamRecording();
    }

    @Override
    public void startDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        RecordPort record = recordPort();
        if (record == null) {
            onComplete.accept(RecordOutcome.failed(RecordFailure.NOT_CONNECTED));
            return;
        }
        record.startDeviceRecord(onComplete);
    }

    @Override
    public void stopDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        RecordPort record = recordPort();
        if (record == null) {
            onComplete.accept(RecordOutcome.failed(RecordFailure.NOT_CONNECTED));
            return;
        }
        record.stopDeviceRecord(onComplete);
    }

    @Override
    public void refreshDeviceRecords(@NonNull Consumer<RecordOutcome> onComplete) {
        RecordPort record = recordPort();
        if (record == null) {
            onComplete.accept(RecordOutcome.failed(RecordFailure.NOT_CONNECTED));
            return;
        }
        record.refreshDeviceRecords(onComplete);
    }

    @Override
    public void syncDeviceRecord(@NonNull String fileId, @NonNull SyncProgressObserver observer) {
        Objects.requireNonNull(observer, "observer");
        RecordPort record = recordPort();
        if (record == null) {
            observer.onError(RecordFailure.NOT_CONNECTED);
            return;
        }
        record.syncDeviceRecord(fileId, observer);
    }

    @Override
    public void cancelSyncDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        RecordPort record = recordPort();
        if (record == null) {
            onComplete.accept(RecordOutcome.failed(RecordFailure.NOT_CONNECTED));
            return;
        }
        record.cancelSyncDeviceRecord(onComplete);
    }

    @Override
    public void deleteDeviceRecord(@NonNull String fileId,
                                   @NonNull Consumer<RecordOutcome> onComplete) {
        RecordPort record = recordPort();
        if (record == null) {
            onComplete.accept(RecordOutcome.failed(RecordFailure.NOT_CONNECTED));
            return;
        }
        record.deleteDeviceRecord(fileId, onComplete);
    }

    @NonNull
    @Override
    public List<SavedRecordItem> listLocalSavedFiles() {
        RecordPort record = recordPort();
        // 没有当前设备就没有属于它的已存文件：显式空清单，不是兜底结果。
        return record == null ? List.of() : record.listLocalSavedFiles();
    }

    @NonNull
    @Override
    public RecordOutcome deleteLocalSavedFile(@NonNull String filePath) {
        RecordPort record = recordPort();
        return record == null
                ? RecordOutcome.failed(RecordFailure.NOT_CONNECTED)
                : record.deleteLocalSavedFile(filePath);
    }

    // ==================== 内部 ====================

    private boolean isEndpointActiveForFilter(@NonNull DeviceEndpoint endpoint,
                                              @NonNull Set<String> filter) {
        if (filter.isEmpty()) {
            return true;
        }
        for (DeviceProfileView profile : endpoint.catalog().models()) {
            if (filter.contains(profile.modelId())) {
                return true;
            }
        }
        return false;
    }

    /** 扫描结果合并回调：缓存该端点最新一批，合并后整批下发。 */
    private void onEndpointResults(@NonNull DeviceTransport transport,
                                   @NonNull List<ScanResult> batch,
                                   @NonNull Consumer<List<ScanResult>> onResults) {
        List<ScanResult> merged;
        synchronized (scanLock) {
            if (!batchActive) {
                return;
            }
            DeviceEndpoint ep = endpoints.of(transport).orElse(null);
            if (ep != null && !isEndpointActiveForFilter(ep, currentFilter)) {
                return;
            }
            latestBatches.put(transport, batch);
            merged = mergeBatchesLocked();
        }
        onResults.accept(merged);
    }

    /** 所有活跃端点都结束才算整批结束：上层只收到一次 finished。 */
    private void onEndpointFinished(@NonNull Runnable onFinished) {
        boolean allFinished;
        synchronized (scanLock) {
            if (!batchActive) {
                return;
            }
            finishedEndpoints++;
            int expected = 0;
            for (DeviceEndpoint endpoint : endpoints.all()) {
                if (isEndpointActiveForFilter(endpoint, currentFilter)) {
                    expected++;
                }
            }
            allFinished = finishedEndpoints >= expected;
            if (allFinished) {
                batchActive = false;
            }
        }
        if (allFinished) {
            onFinished.run();
        }
    }

    /** 按端点装配顺序拼接各批结果，同一地址只保留第一次出现的条目。 */
    @NonNull
    private List<ScanResult> mergeBatchesLocked() {
        Map<String, ScanResult> byAddress = new LinkedHashMap<>();
        for (DeviceEndpoint endpoint : endpoints.all()) {
            List<ScanResult> batch = latestBatches.get(endpoint.transport());
            if (batch == null) {
                continue;
            }
            for (ScanResult result : batch) {
                byAddress.putIfAbsent(result.address(), result);
            }
        }
        return List.copyOf(byAddress.values());
    }

    private void onEndpointSnapshot(@NonNull DeviceEndpoint endpoint,
                                    @NonNull DeviceSnapshot snapshot) {
        synchronized (lock) {
            ConnectionState connection = snapshot.connection();
            if (connectedTransport == null
                    && (connection == ConnectionState.CONNECTING || connection == ConnectionState.READY)) {
                connectedTransport = endpoint.transport();
            }
            if (connectedTransport != endpoint.transport()) {
                return;
            }
        }
        for (Consumer<DeviceSnapshot> listener : linkListeners) {
            listener.accept(snapshot);
        }
        if (snapshot.connection() == ConnectionState.DISCONNECTED) {
            synchronized (lock) {
                if (connectedTransport == endpoint.transport()) {
                    connectedTransport = null;
                }
            }
        }
    }

    @Nullable
    private DeviceEndpoint connectedEndpoint() {
        synchronized (lock) {
            return connectedEndpointLocked();
        }
    }

    @Nullable
    private DeviceEndpoint connectedEndpointLocked() {
        DeviceTransport transport = connectedTransport;
        return transport == null ? null : endpoints.of(transport).orElse(null);
    }

    @Nullable
    private RecordPort recordPort() {
        DeviceEndpoint endpoint = connectedEndpoint();
        return endpoint == null ? null : endpoint.record();
    }
}
