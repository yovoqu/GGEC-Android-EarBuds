package com.ggec.earbuds.testing.device;

import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DevicePort;
import com.ggec.earbuds.domain.device.port.Subscription;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** 单测用可控设备端口：只在测试里模拟已连接/扫描回调，不进入正式包。 */
public final class FakeTestDevicePort implements DevicePort {

    private final CopyOnWriteArrayList<Consumer<DeviceSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private DeviceSnapshot snapshot = DeviceSnapshot.idle();
    private boolean scanning;
    private Consumer<ScanResult> activeResultConsumer;
    private Consumer<List<ScanResult>> activeBatchConsumer;
    private Consumer<ScanFailure> activeFailureConsumer;
    private Runnable activeFinishedCallback;
    public String lastConnectedModel;
    public String lastConnectedVariant;
    public String lastConnectedMac;

    @Override
    public DeviceSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public Subscription observe(Consumer<DeviceSnapshot> listener) {
        listeners.add(listener);
        listener.accept(snapshot);
        return () -> listeners.remove(listener);
    }

    public void emitSnapshot(DeviceSnapshot next) {
        this.snapshot = next;
        for (Consumer<DeviceSnapshot> listener : listeners) {
            listener.accept(next);
        }
    }

    @Override
    public void connect(String modelId, String variantId, String mac) {
        lastConnectedModel = modelId;
        lastConnectedVariant = variantId;
        lastConnectedMac = mac;
    }

    @Override
    public void disconnect() {
        emitSnapshot(DeviceSnapshot.idle());
    }

    @Override
    public void startScan(Consumer<ScanResult> onResult, Runnable onFinished) {
        startScan(onResult, null, onFinished);
    }

    @Override
    public void startScan(Consumer<ScanResult> onResult,
                          Consumer<ScanFailure> onFailed,
                          Runnable onFinished) {
        scanning = true;
        activeResultConsumer = onResult;
        activeBatchConsumer = null;
        activeFailureConsumer = onFailed;
        activeFinishedCallback = onFinished;
    }

    @Override
    public void startScanBatch(Consumer<List<ScanResult>> onResults,
                               Consumer<ScanFailure> onFailed,
                               Runnable onFinished) {
        scanning = true;
        activeResultConsumer = null;
        activeBatchConsumer = onResults;
        activeFailureConsumer = onFailed;
        activeFinishedCallback = onFinished;
    }

    public void emitScanResults(List<ScanResult> results) {
        if (!scanning) {
            return;
        }
        if (activeBatchConsumer != null) {
            activeBatchConsumer.accept(results);
            return;
        }
        if (activeResultConsumer != null) {
            for (ScanResult result : results) {
                activeResultConsumer.accept(result);
            }
        }
    }

    public void emitScanFailure(ScanFailure failure) {
        scanning = false;
        if (activeFailureConsumer != null) {
            Consumer<ScanFailure> failureConsumer = activeFailureConsumer;
            activeFailureConsumer = null;
            failureConsumer.accept(failure);
        }
        finishScan();
    }

    public void finishScan() {
        scanning = false;
        activeFailureConsumer = null;
        if (activeFinishedCallback != null) {
            Runnable done = activeFinishedCallback;
            activeFinishedCallback = null;
            done.run();
        }
    }

    @Override
    public void stopScan() {
        scanning = false;
        activeResultConsumer = null;
        activeBatchConsumer = null;
        activeFailureConsumer = null;
        activeFinishedCallback = null;
    }

    @Override
    public boolean isScanning() {
        return scanning;
    }
}
