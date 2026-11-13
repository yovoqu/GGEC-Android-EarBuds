package com.ggec.earbuds.ui.device.scan;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.util.Set;

/**
 * 扫描页状态机。只通过扫描与打开设备用例表达意图，不调用项目 SDK，也不另存一套设备表。
 */
public final class ScanViewModel extends ViewModel {

    private final ScanDevices scanDevices;
    private final OpenDevice openDevice;
    private final DeviceCapabilityCatalog catalog;
    private final ScanPermissionPort scanPermission;
    private final List<ScanFilterOption> filterOptions;

    private final Object lock = new Object();
    private final MutableLiveData<ScanUiState> uiState;
    private final OneShotLiveData<ScanEvent> events = new OneShotLiveData<>();
    private final OneShotLiveData<ScanConnectOutcome> connectOutcomes = new OneShotLiveData<>();
    private List<ScanResult> visible = List.of();

    private boolean scanning;
    private boolean hasScanned;
    private long scanToken;
    private int connectEpoch;
    private ScanConnectPhase connectPhase = ScanConnectPhase.IDLE;
    private String selectedFilterModelId = ScanFilterOption.ALL_MODELS;

    public ScanViewModel(@NonNull ScanDevices scanDevices,
                         @NonNull OpenDevice openDevice,
                         @NonNull DeviceCapabilityCatalog catalog,
                         @NonNull ScanPermissionPort scanPermission) {
        this.scanDevices = scanDevices;
        this.openDevice = openDevice;
        this.catalog = catalog;
        this.scanPermission = scanPermission;
        this.filterOptions = buildFilterOptions(catalog);
        this.uiState = new MutableLiveData<>(new ScanUiState(
                false, false, selectedFilterModelId, this.filterOptions, List.of(), 0,
                ScanConnectPhase.IDLE));
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
            return connectPhase == ScanConnectPhase.CONNECTING;
        }
    }

    public void toggleScan() {
        if (isConnecting()) {
            return;
        }
        boolean currentlyScanning;
        synchronized (lock) {
            currentlyScanning = scanning;
        }
        if (currentlyScanning) {
            stopScan();
            return;
        }
        if (!scanPermission.isGranted()) {
            events.postValue(ScanEvent.PERMISSION_REQUIRED);
            return;
        }
        startScan();
    }

    public void enterScan() {
        synchronized (lock) {
            if (hasScanned) {
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

    public void selectFilter(@Nullable String modelId) {
        String normalized = modelId == null || modelId.isEmpty()
                ? ScanFilterOption.ALL_MODELS : modelId;
        synchronized (lock) {
            if (selectedFilterModelId.equals(normalized)) {
                return;
            }
            selectedFilterModelId = normalized;
        }
        scanDevices.filterModels(normalized.isEmpty() ? Set.of() : Set.of(normalized));
        ScanUiState snapshot;
        synchronized (lock) {
            snapshot = buildStateLocked();
        }
        uiState.postValue(snapshot);
    }

    private void startScan() {
        final long token;
        ScanUiState startingState;
        synchronized (lock) {
            if (scanning || scanDevices.isScanning()) {
                return;
            }
            hasScanned = true;
            scanning = true;
            scanToken++;
            token = scanToken;
            startingState = buildStateLocked();
        }
        publish(startingState);
        scanDevices.startBatch(
                batch -> onScanBatch(token, batch),
                failure -> onScanFailure(token, failure),
                () -> onScanFinished(token),
                removed -> {
                });
    }

    private void stopScan() {
        ScanUiState stoppedState;
        synchronized (lock) {
            scanToken++;
            scanning = false;
            stoppedState = buildStateLocked();
        }
        scanDevices.stop();
        publish(stoppedState);
    }

    public void connect(@NonNull ScanItem item) {
        final int epoch;
        ScanUiState connecting;
        synchronized (lock) {
            scanToken++;
            scanning = false;
            epoch = ++connectEpoch;
            connectPhase = ScanConnectPhase.CONNECTING;
            connecting = buildStateLocked();
        }
        scanDevices.stop();
        publish(connecting);
        openDevice.execute(targetOf(item), settlement -> onConnectSettled(epoch, settlement));
    }

    @NonNull
    private static DeviceTarget targetOf(@NonNull ScanItem item) {
        return new DeviceTarget(
                new DeviceId(item.modelId(), item.bleAddress()),
                new DeviceIdentity(item.bleName(), item.classicAddress(), item.classicName()));
    }

    private void onConnectSettled(int epoch, ConnectSettlement settlement) {
        ScanUiState next;
        synchronized (lock) {
            if (epoch != connectEpoch || connectPhase != ScanConnectPhase.CONNECTING) {
                return;
            }
            connectPhase = ScanConnectPhase.IDLE;
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
            scanning = false;
            connectEpoch++;
            connectPhase = ScanConnectPhase.IDLE;
        }
        scanDevices.stop();
        super.onCleared();
    }

    private void onScanBatch(long token, @Nullable List<ScanResult> batch) {
        if (batch == null) {
            return;
        }
        ScanUiState snapshot;
        synchronized (lock) {
            if (token != scanToken || !scanning) {
                return;
            }
            visible = List.copyOf(batch);
            snapshot = buildStateLocked();
        }
        uiState.postValue(snapshot);
    }

    private void onScanFailure(long token, @Nullable DeviceFailure failure) {
        if (failure == null) {
            return;
        }
        ScanUiState nextState;
        synchronized (lock) {
            if (token != scanToken) {
                return;
            }
            scanning = false;
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
                 DEVICE_NOT_FOUND -> ScanEvent.FAILED;
        };
    }

    private void onScanFinished(long token) {
        ScanUiState nextState;
        synchronized (lock) {
            if (token != scanToken) {
                return;
            }
            scanning = false;
            nextState = buildStateLocked();
        }
        uiState.postValue(nextState);
    }

    @NonNull
    private ScanUiState buildStateLocked() {
        return new ScanUiState(
                scanning,
                hasScanned,
                selectedFilterModelId,
                filterOptions,
                ScanUiMapper.map(visible),
                visible.size(),
                connectPhase);
    }

    /** 一律 postValue：状态可能从扫描回调线程来，ViewModel 不判断自己在哪个线程上。 */
    private void publish(ScanUiState state) {
        uiState.postValue(state);
    }

    private void publishOutcome(ScanConnectOutcome outcome) {
        connectOutcomes.postValue(outcome);
    }

    @NonNull
    private static List<ScanFilterOption> buildFilterOptions(@NonNull DeviceCapabilityCatalog catalog) {
        List<ScanFilterOption> options = new ArrayList<>();
        options.add(new ScanFilterOption(ScanFilterOption.ALL_MODELS, ""));
        for (DeviceProfileView model : catalog.models()) {
            options.add(new ScanFilterOption(model.modelId(), model.displayName()));
        }
        return List.copyOf(options);
    }
}
