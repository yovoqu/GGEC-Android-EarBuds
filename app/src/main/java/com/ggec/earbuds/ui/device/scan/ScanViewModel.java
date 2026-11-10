package com.ggec.earbuds.ui.device.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.DeviceModel;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DevicePort;
import com.ggec.earbuds.domain.device.usecase.ConnectDevice;
import com.ggec.earbuds.domain.device.usecase.LoadDeviceProfiles;
import com.ggec.earbuds.domain.device.usecase.ScanDevices;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 扫描页状态机：领域结果只在本类内部保存，按所选设备类型投影后对外发布 ScanItem。
 *
 * <p>批量接收底层 BLE 扫描帧，过滤小于 3 dBm 的信号抖动，并对纯信号强度变化做 300ms 节流合并发布；
 * 新设备发现、设备名补全、筛选切换与停扫终态则立即发布，兼顾流畅度与交互即时性。</p>
 */
public final class ScanViewModel extends ViewModel {

    static final long PUBLISH_INTERVAL_MILLIS = 300L;
    static final int RSSI_CHANGE_THRESHOLD_DBM = 3;

    interface PublishScheduler {
        interface Cancellable {
            void cancel();
        }

        @NonNull
        Cancellable schedule(@NonNull Runnable task, long delayMillis);

        default void shutdown() {
        }
    }

    private final ScanDevices scanDevices;
    private final ConnectDevice connectDevice;
    private final List<ScanFilterOption> filterOptions;
    private final PublishScheduler publishScheduler;

    private final Object lock = new Object();
    private final MutableLiveData<ScanUiState> uiState;
    private final OneShotLiveData<Integer> events = new OneShotLiveData<>();
    private final List<ScanResult> results = new ArrayList<>();

    private boolean scanning;
    private boolean hasScanned;
    private long scanToken;
    private String selectedFilterModelId = ScanFilterOption.ALL_MODELS;
    private boolean pendingPublish;
    @Nullable
    private PublishScheduler.Cancellable pendingPublishHandle;

    public ScanViewModel(@NonNull ScanDevices scanDevices, @NonNull ConnectDevice connectDevice) {
        this(scanDevices, connectDevice, null);
    }

    public ScanViewModel(@NonNull ScanDevices scanDevices,
                         @NonNull ConnectDevice connectDevice,
                         @Nullable LoadDeviceProfiles loadProfiles) {
        this(scanDevices, connectDevice, loadProfiles, new DefaultPublishScheduler());
    }

    ScanViewModel(@NonNull ScanDevices scanDevices,
                  @NonNull ConnectDevice connectDevice,
                  @Nullable LoadDeviceProfiles loadProfiles,
                  @NonNull PublishScheduler publishScheduler) {
        this.scanDevices = Objects.requireNonNull(scanDevices, "scanDevices");
        this.connectDevice = Objects.requireNonNull(connectDevice, "connectDevice");
        this.filterOptions = buildFilterOptions(loadProfiles);
        this.publishScheduler = Objects.requireNonNull(publishScheduler, "publishScheduler");
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
    public LiveData<Integer> events() {
        return events;
    }

    public void selectFilter(@Nullable String modelId) {
        String normalized = normalizeFilter(modelId);
        ScanUiState snapshot;
        synchronized (lock) {
            if (selectedFilterModelId.equals(normalized)) {
                return;
            }
            selectedFilterModelId = normalized;
            cancelPendingPublishLocked();
            snapshot = buildStateLocked();
        }
        uiState.postValue(snapshot);
    }

    public void startScan() {
        final long token;
        ScanUiState startingState;
        synchronized (lock) {
            if (scanning || scanDevices.isScanning()) {
                return;
            }
            results.clear();
            cancelPendingPublishLocked();
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
                () -> onScanFinished(token));
    }

    public void stopScan() {
        ScanUiState stoppedState;
        synchronized (lock) {
            scanToken++;
            scanning = false;
            cancelPendingPublishLocked();
            stoppedState = buildStateLocked();
        }
        scanDevices.stop();
        uiState.postValue(stoppedState);
    }

    public void connect(@NonNull ScanItem item) {
        ScanResult matched = null;
        synchronized (lock) {
            scanToken++;
            scanning = false;
            cancelPendingPublishLocked();
            for (ScanResult result : results) {
                if (result.mac().equalsIgnoreCase(item.mac())
                        && result.modelId().equals(item.modelId())) {
                    matched = result;
                    break;
                }
            }
        }
        scanDevices.stop();
        if (matched != null) {
            connectDevice.execute(matched.modelId(), matched.variantId(), matched.mac());
        } else {
            connectDevice.execute(item.modelId(), item.variantId(), item.mac());
        }
    }

    @Override
    protected void onCleared() {
        synchronized (lock) {
            scanToken++;
            scanning = false;
            cancelPendingPublishLocked();
        }
        scanDevices.stop();
        publishScheduler.shutdown();
        super.onCleared();
    }

    private void onScanBatch(long token, @Nullable List<ScanResult> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        ScanUiState immediateSnapshot = null;
        synchronized (lock) {
            if (token != scanToken || !scanning) {
                return;
            }
            boolean structuralChanged = false;
            boolean significantRssiChanged = false;
            for (ScanResult result : batch) {
                if (result == null) {
                    continue;
                }
                int existingIndex = findDeviceIndexLocked(result);
                if (existingIndex < 0) {
                    results.add(result);
                    structuralChanged = true;
                } else {
                    ScanResult existing = results.get(existingIndex);
                    ScanResult merged = merge(existing, result);
                    boolean nonRssiChanged = isNonRssiChanged(existing, merged);
                    if (nonRssiChanged || structuralChanged) {
                        results.set(existingIndex, merged);
                        structuralChanged = true;
                    } else if (Math.abs(merged.rssi() - existing.rssi()) >= RSSI_CHANGE_THRESHOLD_DBM) {
                        results.set(existingIndex, merged);
                        significantRssiChanged = true;
                    }
                }
            }
            if (structuralChanged) {
                cancelPendingPublishLocked();
                immediateSnapshot = buildStateLocked();
            } else if (significantRssiChanged) {
                pendingPublish = true;
                if (pendingPublishHandle == null) {
                    pendingPublishHandle = publishScheduler.schedule(
                            () -> flushPendingPublish(token),
                            PUBLISH_INTERVAL_MILLIS);
                }
            }
        }
        if (immediateSnapshot != null) {
            uiState.postValue(immediateSnapshot);
        }
    }

    private void flushPendingPublish(long token) {
        ScanUiState snapshot;
        synchronized (lock) {
            pendingPublishHandle = null;
            if (!pendingPublish || token != scanToken || !scanning) {
                pendingPublish = false;
                return;
            }
            pendingPublish = false;
            snapshot = buildStateLocked();
        }
        uiState.postValue(snapshot);
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
            cancelPendingPublishLocked();
            nextState = buildStateLocked();
        }
        uiState.postValue(nextState);
        events.postValue(mapFailureMessage(failure));
    }

    private void onScanFinished(long token) {
        ScanUiState nextState;
        synchronized (lock) {
            if (token != scanToken) {
                return;
            }
            scanning = false;
            cancelPendingPublishLocked();
            nextState = buildStateLocked();
        }
        uiState.postValue(nextState);
    }

    private void cancelPendingPublishLocked() {
        pendingPublish = false;
        PublishScheduler.Cancellable handle = pendingPublishHandle;
        pendingPublishHandle = null;
        if (handle != null) {
            handle.cancel();
        }
    }

    private static boolean isNonRssiChanged(@NonNull ScanResult existing, @NonNull ScanResult merged) {
        return !existing.name().equals(merged.name())
                || !existing.variantId().equals(merged.variantId())
                || !existing.mac().equals(merged.mac())
                || existing.paired() != merged.paired();
    }

    private int findDeviceIndexLocked(@NonNull ScanResult candidate) {
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).sameDevice(candidate)) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    private ScanUiState buildStateLocked() {
        return new ScanUiState(
                scanning,
                hasScanned,
                selectedFilterModelId,
                filterOptions,
                ScanUiMapper.map(results, selectedFilterModelId),
                results.size());
    }

    @NonNull
    private String normalizeFilter(@Nullable String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return ScanFilterOption.ALL_MODELS;
        }
        for (ScanFilterOption option : filterOptions) {
            if (option.modelId().equals(candidate)) {
                return candidate;
            }
        }
        return ScanFilterOption.ALL_MODELS;
    }

    @NonNull
    private static ScanResult merge(@NonNull ScanResult existing, @NonNull ScanResult incoming) {
        String name = !incoming.name().isEmpty()
                && (!incoming.name().equalsIgnoreCase(incoming.modelId()) || existing.name().isEmpty())
                ? incoming.name()
                : existing.name();
        String variantId = !incoming.variantId().isEmpty()
                ? incoming.variantId() : existing.variantId();
        String mac = !incoming.mac().isEmpty() ? incoming.mac() : existing.mac();
        return new ScanResult(
                incoming.modelId(),
                variantId,
                name,
                mac,
                incoming.rssi(),
                existing.paired() || incoming.paired());
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

    private static int mapFailureMessage(@NonNull DevicePort.ScanFailure failure) {
        return switch (failure) {
            case BLUETOOTH_DISABLED -> R.string.device_scan_error_bluetooth_disabled;
            case PERMISSION_DENIED -> R.string.device_scan_error_permission;
            case LOCATION_DISABLED -> R.string.device_scan_error_location_disabled;
            case BLE_UNSUPPORTED -> R.string.device_scan_error_ble_unsupported;
            case SCAN_THROTTLED -> R.string.device_scan_error_throttled;
            case FAILED -> R.string.device_scan_error_failed;
        };
    }

    private static final class DefaultPublishScheduler implements PublishScheduler {

        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "scan-vm-coalescer");
            thread.setDaemon(true);
            return thread;
        });

        @NonNull
        @Override
        public Cancellable schedule(@NonNull Runnable task, long delayMillis) {
            try {
                ScheduledFuture<?> future = executor.schedule(task, Math.max(1L, delayMillis), TimeUnit.MILLISECONDS);
                return () -> future.cancel(false);
            } catch (RejectedExecutionException closed) {
                return () -> {
                };
            }
        }

        @Override
        public void shutdown() {
            executor.shutdownNow();
        }
    }
}
