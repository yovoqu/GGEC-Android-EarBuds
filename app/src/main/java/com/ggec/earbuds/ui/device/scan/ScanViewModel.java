package com.ggec.earbuds.ui.device.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.device.model.DeviceModel;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DevicePort;
import com.ggec.earbuds.domain.device.port.ScanPermissionPort;
import com.ggec.earbuds.domain.device.scan.DiscoveredDevices;
import com.ggec.earbuds.domain.device.usecase.ConnectDevice;
import com.ggec.earbuds.domain.device.usecase.LoadDeviceProfiles;
import com.ggec.earbuds.domain.device.usecase.ScanDevices;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 扫描页状态机。设备事实在 {@link DiscoveredDevices}：每一帧 BLE 广播都写入该表。
 * 本类持有扫描生命周期的全部决策（是否开扫、开扫前的权限门、停止、连接前停扫），
 * 视图只转发用户动作与权限结果。
 */
public final class ScanViewModel extends ViewModel {

    private final ScanDevices scanDevices;
    private final ConnectDevice connectDevice;
    private final List<ScanFilterOption> filterOptions;
    private final ScanPermissionPort scanPermission;

    private final Object lock = new Object();
    private final MutableLiveData<ScanUiState> uiState;
    private final OneShotLiveData<ScanEvent> events = new OneShotLiveData<>();
    private final DiscoveredDevices devices = new DiscoveredDevices();

    private boolean scanning;
    private boolean hasScanned;
    private long scanToken;
    private String selectedFilterModelId = ScanFilterOption.ALL_MODELS;

    public ScanViewModel(@NonNull ScanDevices scanDevices,
                         @NonNull ConnectDevice connectDevice,
                         @Nullable LoadDeviceProfiles loadProfiles,
                         @NonNull ScanPermissionPort scanPermission) {
        this.scanDevices = Objects.requireNonNull(scanDevices, "scanDevices");
        this.connectDevice = Objects.requireNonNull(connectDevice, "connectDevice");
        this.scanPermission = Objects.requireNonNull(scanPermission, "scanPermission");
        this.filterOptions = buildFilterOptions(loadProfiles);
        this.uiState = new MutableLiveData<>(new ScanUiState(
                false,
                false,
                selectedFilterModelId,
                this.filterOptions,
                List.of(),
                0));
    }

    @NonNull
    public LiveData<ScanUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<ScanEvent> events() {
        return events;
    }

    /**
     * 切换扫描：扫描中则停止；未扫描且权限已授予则开扫；
     * 未扫描且权限未授予则发 {@link ScanEvent#PERMISSION_REQUIRED}，由宿主申请权限，
     * 结果经 {@link #onPermissionAnswer(boolean)} 回到本层。
     */
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

    /**
     * 进页语义：本次页面实例还没有走过扫描流程时才开扫；已走过则原样返回。
     *
     * <p>不能复用 {@link #toggleScan()} 的翻转语义：配置变更重建 Activity 时 ViewModel 存活、
     * 扫描仍在进行，翻转会把正在跑的扫描<b>停掉</b>。这里只做幂等开扫，按钮的停止语义仍归
     * {@link #toggleScan()}。权限缺失时只请求权限，不开扫。</p>
     */
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

    /** 权限请求结果：授予才开扫；拒绝保持未扫描，等下一次 {@link #toggleScan()}。 */
    public void onPermissionAnswer(boolean granted) {
        if (!granted) {
            return;
        }
        startScan();
    }

    public void selectFilter(@Nullable String modelId) {
        String normalized = normalizeFilter(modelId);
        ScanUiState snapshot;
        synchronized (lock) {
            if (selectedFilterModelId.equals(normalized)) {
                return;
            }
            selectedFilterModelId = normalized;
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
            devices.clear();
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
                removed -> onScanRemoved(token, removed));
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
        ScanResult matched;
        synchronized (lock) {
            scanToken++;
            scanning = false;
            matched = devices.find(item.modelId(), item.bleMac());
        }
        scanDevices.stop();
        if (matched != null) {
            connectDevice.execute(matched.modelId(), matched.variantId(),
                    matched.bleMac(), matched.bleName());
        } else {
            connectDevice.execute(item.modelId(), item.variantId(), item.bleMac(), item.bleName());
        }
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
        if (batch == null || batch.isEmpty()) {
            return;
        }
        ScanUiState snapshot = null;
        synchronized (lock) {
            if (token != scanToken || !scanning) {
                return;
            }
            boolean changed = false;
            for (ScanResult result : batch) {
                changed |= devices.accept(result);
            }
            if (changed) {
                snapshot = buildStateLocked();
            }
        }
        if (snapshot != null) {
            uiState.postValue(snapshot);
        }
    }

    private void onScanFailure(long token, @Nullable DevicePort.ScanFailure failure) {
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
    private static ScanEvent toScanEvent(@NonNull DevicePort.ScanFailure failure) {
        return switch (failure) {
            case BLUETOOTH_DISABLED -> ScanEvent.BLUETOOTH_DISABLED;
            case PERMISSION_DENIED -> ScanEvent.PERMISSION_DENIED;
            case LOCATION_DISABLED -> ScanEvent.LOCATION_DISABLED;
            case BLE_UNSUPPORTED -> ScanEvent.BLE_UNSUPPORTED;
            case SCAN_THROTTLED -> ScanEvent.SCAN_THROTTLED;
            case FAILED -> ScanEvent.FAILED;
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

    private void onScanRemoved(long token, @Nullable ScanResult removed) {
        ScanUiState snapshot = null;
        synchronized (lock) {
            if (token != scanToken) {
                return;
            }
            if (devices.remove(removed)) {
                snapshot = buildStateLocked();
            }
        }
        if (snapshot != null) {
            uiState.postValue(snapshot);
        }
    }

    @NonNull
    private ScanUiState buildStateLocked() {
        return new ScanUiState(
                scanning,
                hasScanned,
                selectedFilterModelId,
                filterOptions,
                ScanUiMapper.map(devices.snapshot(), selectedFilterModelId),
                devices.size());
    }

    @NonNull
    private static String normalizeFilter(@Nullable String candidate) {
        return candidate == null || candidate.isEmpty()
                ? ScanFilterOption.ALL_MODELS : candidate;
    }

    @NonNull
    private static List<ScanFilterOption> buildFilterOptions(@Nullable LoadDeviceProfiles loadProfiles) {
        if (loadProfiles == null) {
            return ScanUiState.defaultFilterOptions();
        }
        List<ScanFilterOption> options = new ArrayList<>();
        options.add(new ScanFilterOption(ScanFilterOption.ALL_MODELS, ""));
        for (DeviceModel model : loadProfiles.models()) {
            options.add(new ScanFilterOption(model.id(), model.id()));
        }
        return List.copyOf(options);
    }
}
