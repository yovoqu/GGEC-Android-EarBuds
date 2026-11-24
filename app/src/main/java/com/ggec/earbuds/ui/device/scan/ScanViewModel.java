package com.ggec.earbuds.ui.device.scan;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceIdentity;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.ScanPermissionPort;
import com.ggec.earbuds.domain.device.usecase.OpenDevice;
import com.ggec.earbuds.domain.device.usecase.ScanDevices;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 扫描页状态机。只通过扫描与打开设备用例表达意图，不调用项目 SDK，也不另存一套设备表。
 *
 * <p><b>单一阶段字段：</b>{@link #phase} 取代过去的 {@code scanning}/{@code hasScanned}/
 * {@code connectPhase} 三源；代际丢弃仍由 {@code scanToken}/{@code connectEpoch} 保证。</p>
 *
 * <p><b>机型筛选来自目录：</b>默认筛选是目录首项，目录为空时不限制（空集）。切换筛选只把
 * 新规则下发给端口，<b>不</b>停止或重启扫描。</p>
 */
public final class ScanViewModel extends ViewModel {

    private final ScanDevices scanDevices;
    private final OpenDevice openDevice;
    private final ScanPermissionPort scanPermission;
    private final Set<String> recordLaunchModelIds;
    private final List<ScanFilterOption> filterOptions;

    private final Object lock = new Object();
    private final MutableLiveData<ScanUiState> uiState;
    private final OneShotLiveData<ScanEvent> events = new OneShotLiveData<>();
    private final OneShotLiveData<ScanConnectOutcome> connectOutcomes = new OneShotLiveData<>();

    private List<ScanResult> visible = List.of();
    private ScanPhase phase = new ScanPhase.Idle();
    private long scanToken;
    private int connectEpoch;
    private String selectedFilterModelId;

    public ScanViewModel(@NonNull ScanDevices scanDevices,
                         @NonNull OpenDevice openDevice,
                         @NonNull DeviceCapabilityCatalog catalog,
                         @NonNull ScanPermissionPort scanPermission,
                         @NonNull Set<String> recordLaunchModelIds) {
        this.scanDevices = scanDevices;
        this.openDevice = openDevice;
        this.scanPermission = scanPermission;
        this.recordLaunchModelIds = Set.copyOf(
                Objects.requireNonNull(recordLaunchModelIds, "recordLaunchModelIds"));
        this.filterOptions = buildFilterOptions(catalog);
        this.selectedFilterModelId = defaultModelId(this.filterOptions);
        this.scanDevices.filterModels(selectedFilterSet());
        this.uiState = new MutableLiveData<>(buildStateLocked());
    }

    @NonNull
    public LiveData<ScanUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<ScanEvent> events() {
        return events;
    }

    public LiveData<ScanConnectOutcome> connectOutcomes() {
        return connectOutcomes;
    }

    public boolean isConnecting() {
        synchronized (lock) {
            return phase.connecting();
        }
    }

    public void toggleScan() {
        boolean scanning;
        synchronized (lock) {
            if (phase.connecting()) {
                return;
            }
            scanning = phase.scanning();
        }
        if (scanning) {
            stopScan();
            return;
        }
        if (!scanPermission.isGranted()) {
            events.postValue(ScanEvent.PERMISSION_REQUIRED);
            return;
        }
        startScan();
    }

    /** 进页调用：本轮还没开始过才开扫；正在扫/已扫过/连接中都不重复开。 */
    public void enterScan() {
        synchronized (lock) {
            if (!(phase instanceof ScanPhase.Idle)) {
                return;
            }
        }
        if (!scanPermission.isGranted()) {
            events.postValue(ScanEvent.PERMISSION_REQUIRED);
            return;
        }
        startScan();
    }

    public void onPermissionAnswer(boolean granted) {
        if (granted) {
            startScan();
        }
    }

    /**
     * 切换机型筛选：只把新规则下发到端口，扫描继续跑，不重启。
     *
     * @param modelId 目录里的机型 id，不接受空串（默认值由构造函数按目录首项决定）
     */
    public void selectFilter(@NonNull String modelId) {
        String normalized = Objects.requireNonNull(modelId, "modelId").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("selectFilter requires a model id");
        }
        ScanUiState snapshot;
        synchronized (lock) {
            if (selectedFilterModelId.equals(normalized)) {
                return;
            }
            selectedFilterModelId = normalized;
            visible = List.of();
            snapshot = buildStateLocked();
        }
        scanDevices.filterModels(Set.of(normalized));
        publish(snapshot);
    }

    private void startScan() {
        final long token;
        ScanUiState startingState;
        synchronized (lock) {
            if (phase.scanning() || scanDevices.isScanning()) {
                return;
            }
            phase = new ScanPhase.Scanning();
            token = ++scanToken;
            startingState = buildStateLocked();
        }
        publish(startingState);
        scanDevices.startBatch(
                batch -> onScanBatch(token, batch),
                failure -> onScanFailure(token, failure),
                () -> onScanFinished(token),
                removed -> onScanRemoved(token, removed));
    }

    private void stopScan() {
        ScanUiState stoppedState;
        synchronized (lock) {
            scanToken++;
            phase = new ScanPhase.Stopped();
            stoppedState = buildStateLocked();
        }
        scanDevices.stop();
        publish(stoppedState);
    }

    public void connect(@NonNull ScanItem item) {
        if (recordLaunchModelIds.contains(item.modelId())) {
            ScanUiState idle;
            synchronized (lock) {
                if (phase.connecting()) {
                    return;
                }
                scanToken++;
                phase = new ScanPhase.Idle();
                idle = buildStateLocked();
            }
            scanDevices.stop();
            publish(idle);
            publishOutcome(new ScanConnectOutcome.OpenRecord(item.address(), item.name()));
            return;
        }
        final int epoch;
        ScanUiState connecting;
        synchronized (lock) {
            if (phase.connecting()) {
                return;
            }
            scanToken++;
            epoch = ++connectEpoch;
            phase = new ScanPhase.Connecting(item.address());
            connecting = buildStateLocked();
        }
        scanDevices.stop();
        publish(connecting);
        openDevice.execute(targetOf(item), settlement -> onConnectSettled(epoch, settlement));
    }

    @NonNull
    private static DeviceTarget targetOf(@NonNull ScanItem item) {
        return new DeviceTarget(
                new DeviceId(item.modelId(), item.address()),
                new DeviceIdentity(item.name(), item.address(), item.name()));
    }

    private void onConnectSettled(int epoch, ConnectSettlement settlement) {
        ScanUiState next;
        synchronized (lock) {
            if (epoch != connectEpoch || !phase.connecting()) {
                return;
            }
            phase = new ScanPhase.Idle();
            next = buildStateLocked();
        }
        publish(next);
        if (settlement instanceof ConnectSettlement.Ready) {
            publishOutcome(new ScanConnectOutcome.Succeeded());
            return;
        }
        if (settlement instanceof ConnectSettlement.Failed failed) {
            publishOutcome(new ScanConnectOutcome.Failed(failed.failure()));
            if (scanPermission.isGranted()) {
                startScan();
            }
        }
    }

    @Override
    protected void onCleared() {
        synchronized (lock) {
            scanToken++;
            connectEpoch++;
            phase = new ScanPhase.Idle();
        }
        scanDevices.stop();
        super.onCleared();
    }

    private void onScanBatch(long token, @NonNull List<ScanResult> batch) {
        ScanUiState snapshot;
        synchronized (lock) {
            if (token != scanToken || !phase.scanning()) {
                return;
            }
            visible = List.copyOf(batch);
            snapshot = buildStateLocked();
        }
        uiState.postValue(snapshot);
    }

    /**
     * 设备过期（{@code onLost}）：按地址把它从当前列表里摘掉并重发状态。
     * 摘不到说明这一条早就不在列表里，此时不改状态、也不多发一次。
     */
    private void onScanRemoved(long token, @NonNull ScanResult removed) {
        ScanUiState snapshot;
        synchronized (lock) {
            if (token != scanToken || !phase.scanning()) {
                return;
            }
            List<ScanResult> remaining = new ArrayList<>(visible.size());
            for (ScanResult candidate : visible) {
                if (!candidate.address().equalsIgnoreCase(removed.address())) {
                    remaining.add(candidate);
                }
            }
            if (remaining.size() == visible.size()) {
                return;
            }
            visible = List.copyOf(remaining);
            snapshot = buildStateLocked();
        }
        uiState.postValue(snapshot);
    }

    private void onScanFailure(long token, @NonNull DeviceFailure failure) {
        ScanUiState nextState;
        synchronized (lock) {
            if (token != scanToken) {
                return;
            }
            phase = new ScanPhase.Failed(failure);
            nextState = buildStateLocked();
        }
        uiState.postValue(nextState);
        events.postValue(toScanEvent(failure));
    }

    @NonNull
    private static ScanEvent toScanEvent(@NonNull DeviceFailure failure) {
        return switch (failure) {
            case BLUETOOTH_DISABLED -> ScanEvent.BLUETOOTH_DISABLED;
            case PERMISSION_DENIED -> ScanEvent.PERMISSION_DENIED;
            case LOCATION_DISABLED -> ScanEvent.LOCATION_DISABLED;
            case BLE_UNSUPPORTED -> ScanEvent.BLE_UNSUPPORTED;
            case SCAN_THROTTLED -> ScanEvent.SCAN_THROTTLED;
            case SCAN_FAILED, NOT_READY, TIMEOUT, REJECTED, UNSUPPORTED_CAPABILITY,
                 DEVICE_ERROR, INTERNAL_ERROR, INCOMPLETE_IDENTITY, STORE_FAILED,
                 DEVICE_NOT_FOUND, CLASSIC_NOT_CONNECTED -> ScanEvent.FAILED;
        };
    }

    private void onScanFinished(long token) {
        ScanUiState nextState;
        synchronized (lock) {
            if (token != scanToken || !phase.scanning()) {
                return;
            }
            phase = new ScanPhase.Stopped();
            nextState = buildStateLocked();
        }
        uiState.postValue(nextState);
    }

    @NonNull
    private ScanUiState buildStateLocked() {
        return ScanUiMapper.mapState(phase, selectedFilterModelId, filterOptions, visible);
    }

    /** 一律 postValue：状态可能从扫描回调线程来，ViewModel 不判断自己在哪个线程上。 */
    private void publish(ScanUiState state) {
        uiState.postValue(state);
    }

    private void publishOutcome(ScanConnectOutcome outcome) {
        connectOutcomes.postValue(outcome);
    }

    /** 当前筛选对应的下发集合：空串（目录为空）表示不限。 */
    @NonNull
    private Set<String> selectedFilterSet() {
        return selectedFilterModelId.isEmpty() ? Set.of() : Set.of(selectedFilterModelId);
    }

    @NonNull
    private static List<ScanFilterOption> buildFilterOptions(@NonNull DeviceCapabilityCatalog catalog) {
        List<ScanFilterOption> options = new ArrayList<>();
        for (DeviceProfileView model : catalog.models()) {
            options.add(new ScanFilterOption(model.modelId(), model.displayName()));
        }
        return List.copyOf(options);
    }

    /** 默认筛选 = 目录首项；目录为空时没有筛选值（空集 = 不限）。 */
    @NonNull
    private static String defaultModelId(@NonNull List<ScanFilterOption> options) {
        return options.isEmpty() ? "" : options.get(0).modelId();
    }
}
