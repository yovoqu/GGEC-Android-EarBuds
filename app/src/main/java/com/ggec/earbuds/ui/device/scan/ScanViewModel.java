package com.ggec.earbuds.ui.device.scan;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.usecase.ConnectDevice;
import com.ggec.earbuds.domain.device.usecase.ScanDevices;

import java.util.ArrayList;
import java.util.List;

/** 扫描页状态机：领域结果只在本类内部保存，对外只发布 ScanItem。 */
public final class ScanViewModel extends ViewModel {

    private final ScanDevices scanDevices;
    private final ConnectDevice connectDevice;

    private final MutableLiveData<ScanUiState> uiState = new MutableLiveData<>(ScanUiState.idle());
    private final List<ScanResult> results = new ArrayList<>();
    private boolean hasScanned;

    public ScanViewModel(@NonNull ScanDevices scanDevices, @NonNull ConnectDevice connectDevice) {
        this.scanDevices = scanDevices;
        this.connectDevice = connectDevice;
    }

    @NonNull
    public LiveData<ScanUiState> uiState() {
        return uiState;
    }

    public void startScan() {
        if (scanDevices.isScanning()) {
            return;
        }
        results.clear();
        hasScanned = true;
        uiState.setValue(new ScanUiState(true, true, List.of()));
        scanDevices.start(result -> {
            if (!contains(result)) {
                results.add(result);
            }
            uiState.postValue(new ScanUiState(true, true, ScanUiMapper.map(results)));
        }, () -> uiState.postValue(new ScanUiState(false, true, ScanUiMapper.map(results))));
    }

    private boolean contains(ScanResult candidate) {
        for (ScanResult existing : results) {
            if (existing.sameDevice(candidate)) {
                return true;
            }
        }
        return false;
    }

    public void stopScan() {
        scanDevices.stop();
        uiState.postValue(new ScanUiState(false, hasScanned, ScanUiMapper.map(results)));
    }

    public void connect(@NonNull ScanItem item) {
        scanDevices.stop();
        for (ScanResult result : results) {
            if (result.mac().equalsIgnoreCase(item.mac())) {
                connectDevice.execute(result.modelId(), result.variantId(), result.mac());
                return;
            }
        }
        connectDevice.execute(item.modelId(), item.variantId(), item.mac());
    }

    @Override
    protected void onCleared() {
        scanDevices.stop();
        super.onCleared();
    }
}
