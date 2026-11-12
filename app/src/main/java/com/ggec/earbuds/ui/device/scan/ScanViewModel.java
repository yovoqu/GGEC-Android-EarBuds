package com.ggec.earbuds.ui.device.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.ScanPermissionPort;
import com.ggec.earbuds.domain.device.usecase.ConnectDevice;
import com.ggec.earbuds.domain.device.usecase.ScanDevices;
import com.ggec.earbuds.ui.OneShotLiveData;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描页状态机。只通过扫描和连接用例表达意图，不调用项目 SDK，也不另存一套设备表。
 */
public final class ScanViewModel extends ViewModel {

    private final ScanDevices scanDevices;
    private final ConnectDevice connectDevice;
    private final DeviceCapabilityCatalog catalog;
    private final ScanPermissionPort scanPermission;
    private final List<ScanFilterOption> filterOptions;

    private final Object lock = new Object();
    private final MutableLiveData<ScanUiState> uiState;
    private final OneShotLiveData<ScanEvent> events = new OneShotLiveData<>();
    private List<ScanResult> visible = List.of();

    private boolean scanning;
    private boolean hasScanned;
    private long scanToken;
    private String selectedFilterModelId = ScanFilterOption.ALL_MODELS;

    public ScanViewModel(@NonNull ScanDevices scanDevices,
                         @NonNull ConnectDevice connectDevice,
                         @NonNull DeviceCapabilityCatalog catalog,
                         @NonNull ScanPermissionPort scanPermission) {
        this.scanDevices = scanDevices;
        this.connectDevice = connectDevice;
        this.catalog = catalog;
        this.scanPermission = scanPermission;
        this.filterOptions = buildFilterOptions(catalog);
        this.uiState = new MutableLiveData<>(new ScanUiState(
                false, false, selectedFilterModelId, this.filterOptions, List.of(), 0));
    }

    @NonNull
    public LiveData<ScanUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<ScanEvent> events() {
        return events;
    }

    public void toggleScan() {
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
        scanDevices.filterModels(normalized.isEmpty() ? java.util.Set.of() : java.util.Set.of(normalized));
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
        uiState.setValue(startingState);
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
        uiState.postValue(stoppedState);
    }

    public void connect(@NonNull ScanItem item) {
        synchronized (lock) {
            scanToken++;
            scanning = false;
        }
        scanDevices.stop();
        connectDevice.execute(item.modelId(), item.bleAddress(), item.bleName(),
                item.classicAddress(), item.classicName());
    }

    @Override
    protected void onCleared() {
        synchronized (lock) {
            scanToken++;
            scanning = false;
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
                 DEVICE_ERROR, INTERNAL_ERROR -> ScanEvent.FAILED;
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
                visible.size());
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
