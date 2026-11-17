package com.ggec.earbuds.wiring.device;

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
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.domain.device.port.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 聚合设备链路适配器：根据产品型号动态路由到对应硬件驱动（HS 或 WY），严禁上层绑定具体设备类型。
 */
public final class CompositeDeviceLinkAdapter implements DeviceLinkPort {

    private final List<DeviceDriver> drivers;
    private final CopyOnWriteArrayList<Consumer<DeviceSnapshot>> listeners =
            new CopyOnWriteArrayList<>();
    private final Object scanLock = new Object();
    private final Object linkLock = new Object();

    private volatile DeviceDriver activeScanDriver;
    private volatile DeviceDriver activeLinkDriver;
    private volatile String currentFilterModelId = "HS02";
    private volatile boolean isScanning;

    private Consumer<List<ScanResult>> scanOnResults;
    private Consumer<DeviceFailure> scanOnFailed;
    private Runnable scanOnFinished;
    private Consumer<ScanResult> scanOnRemoved;

    public CompositeDeviceLinkAdapter(@NonNull List<DeviceDriver> drivers) {
        if (drivers == null || drivers.isEmpty()) {
            throw new IllegalArgumentException("drivers must not be empty");
        }
        this.drivers = List.copyOf(drivers);
        this.activeScanDriver = findDriverForModel(currentFilterModelId);
        if (this.activeScanDriver == null) {
            this.activeScanDriver = this.drivers.get(0);
        }
        this.activeLinkDriver = this.activeScanDriver;

        // 监听所有驱动的设备快照变化
        for (DeviceDriver driver : this.drivers) {
            driver.link().observe(snapshot -> onDriverSnapshotChanged(driver, snapshot));
        }
    }

    @Nullable
    private DeviceDriver findDriverForModel(@NonNull String modelId) {
        for (DeviceDriver driver : drivers) {
            if (driver.supportsModel(modelId)) {
                return driver;
            }
        }
        return null;
    }

    @NonNull
    public RecordPort activeRecordPort() {
        DeviceDriver driver = activeLinkDriver;
        return driver != null ? driver.record() : drivers.get(0).record();
    }

    private void onDriverSnapshotChanged(@NonNull DeviceDriver driver, @NonNull DeviceSnapshot snapshot) {
        synchronized (linkLock) {
            // 如果是当前正在连接或已连上的驱动，或者当前没有任何特定活跃驱动且该驱动非空闲
            if (driver == activeLinkDriver || activeLinkDriver == null) {
                for (Consumer<DeviceSnapshot> listener : listeners) {
                    listener.accept(snapshot);
                }
            }
        }
    }

    @NonNull
    @Override
    public DeviceSnapshot snapshot() {
        DeviceDriver driver = activeLinkDriver;
        return driver != null ? driver.link().snapshot() : DeviceSnapshot.idle();
    }

    @NonNull
    @Override
    public Subscription observe(Consumer<DeviceSnapshot> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("observe requires a listener");
        }
        listeners.add(listener);
        listener.accept(snapshot());
        return () -> listeners.remove(listener);
    }

    @Override
    public void connect(DeviceTarget target, Consumer<ConnectSettlement> onSettled) {
        Objects.requireNonNull(target, "target");
        String modelId = target.id().modelId();
        DeviceDriver targetDriver = findDriverForModel(modelId);
        if (targetDriver == null) {
            targetDriver = activeScanDriver != null ? activeScanDriver : drivers.get(0);
        }

        synchronized (linkLock) {
            if (activeLinkDriver != null && activeLinkDriver != targetDriver) {
                activeLinkDriver.link().disconnect();
            }
            activeLinkDriver = targetDriver;
        }
        targetDriver.link().connect(target, onSettled);
    }

    @Override
    public void disconnect() {
        DeviceDriver driver = activeLinkDriver;
        if (driver != null) {
            driver.link().disconnect();
        }
    }

    @Override
    public void filterModels(Set<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return;
        }
        String modelId = modelIds.iterator().next();
        synchronized (scanLock) {
            currentFilterModelId = modelId;
            DeviceDriver targetDriver = findDriverForModel(modelId);
            if (targetDriver == null) {
                targetDriver = drivers.get(0);
            }

            if (isScanning) {
                if (targetDriver != activeScanDriver) {
                    // 切换不同硬件形态的扫描器（如 BLE 与 经典蓝牙）
                    if (activeScanDriver != null) {
                        activeScanDriver.link().stopScan();
                    }
                    if (scanOnResults != null) {
                        scanOnResults.accept(List.of());
                    }
                    activeScanDriver = targetDriver;
                    targetDriver.link().filterModels(Set.of(modelId));
                    if (scanOnResults != null && scanOnFailed != null
                            && scanOnFinished != null && scanOnRemoved != null) {
                        targetDriver.link().startScanBatch(
                                scanOnResults, scanOnFailed, scanOnFinished, scanOnRemoved);
                    }
                } else {
                    targetDriver.link().filterModels(Set.of(modelId));
                }
            } else {
                activeScanDriver = targetDriver;
                targetDriver.link().filterModels(Set.of(modelId));
            }
        }
    }

    @Override
    public void startScanBatch(Consumer<List<ScanResult>> onResults,
                               Consumer<DeviceFailure> onFailed,
                               Runnable onFinished,
                               Consumer<ScanResult> onRemoved) {
        synchronized (scanLock) {
            this.scanOnResults = onResults;
            this.scanOnFailed = onFailed;
            this.scanOnFinished = onFinished;
            this.scanOnRemoved = onRemoved;
            this.isScanning = true;

            DeviceDriver driver = activeScanDriver;
            if (driver == null) {
                driver = findDriverForModel(currentFilterModelId);
                if (driver == null) {
                    driver = drivers.get(0);
                }
                activeScanDriver = driver;
            }

            // 确保其他驱动停止扫描，防止射频冲突
            for (DeviceDriver other : drivers) {
                if (other != driver && other.link().isScanning()) {
                    other.link().stopScan();
                }
            }

            driver.link().filterModels(Set.of(currentFilterModelId));
            driver.link().startScanBatch(onResults, onFailed, onFinished, onRemoved);
        }
    }

    @Override
    public void stopScan() {
        synchronized (scanLock) {
            isScanning = false;
            for (DeviceDriver driver : drivers) {
                if (driver.link().isScanning()) {
                    driver.link().stopScan();
                }
            }
        }
    }

    @Override
    public boolean isScanning() {
        synchronized (scanLock) {
            return isScanning;
        }
    }

    @Override
    public void write(String capabilityId, ControlValue value, Consumer<DeviceWriteResult> callback) {
        DeviceDriver driver = activeLinkDriver;
        if (driver != null) {
            driver.link().write(capabilityId, value, callback);
        } else {
            callback.accept(new DeviceWriteResult.Failed(DeviceFailure.NOT_READY));
        }
    }

    @Override
    public void perform(DeviceAction action, Consumer<DeviceWriteResult> callback) {
        DeviceDriver driver = activeLinkDriver;
        if (driver != null) {
            driver.link().perform(action, callback);
        } else {
            callback.accept(new DeviceWriteResult.Failed(DeviceFailure.NOT_READY));
        }
    }

    @Override
    public void close() {
        stopScan();
        for (DeviceDriver driver : drivers) {
            driver.close();
        }
        listeners.clear();
    }
}
