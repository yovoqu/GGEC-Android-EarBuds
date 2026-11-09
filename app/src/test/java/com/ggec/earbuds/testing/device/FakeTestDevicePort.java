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
        scanning = true;
        activeResultConsumer = onResult;
        activeFinishedCallback = onFinished;
    }

    public void emitScanResults(List<ScanResult> results) {
        if (!scanning || activeResultConsumer == null) {
            return;
        }
        for (ScanResult result : results) {
            activeResultConsumer.accept(result);
        }
    }

    public void finishScan() {
        scanning = false;
        if (activeFinishedCallback != null) {
            Runnable done = activeFinishedCallback;
            activeFinishedCallback = null;
            done.run();
        }
    }

    @Override
    public void stopScan() {
        scanning = false;
    }

    @Override
    public boolean isScanning() {
        return scanning;
    }
}
