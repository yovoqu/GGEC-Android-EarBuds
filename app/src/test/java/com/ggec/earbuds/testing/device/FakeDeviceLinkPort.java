package com.ggec.earbuds.testing.device;

import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** 测试用链路端口：记录调用、由测试决定什么时候结算。 */
public final class FakeDeviceLinkPort implements DeviceLinkPort {

    public final List<DeviceTarget> connects = new ArrayList<>();
    public final List<String> writes = new ArrayList<>();
    public final List<DeviceAction> actions = new ArrayList<>();
    public int disconnects;
    public boolean scanning;

    private final List<Consumer<DeviceSnapshot>> observers = new ArrayList<>();
    private DeviceSnapshot current = DeviceSnapshot.idle();
    private Consumer<ConnectSettlement> pendingSettlement;
    private Consumer<DeviceWriteResult> writeCallback;

    @Override
    public DeviceSnapshot snapshot() {
        return current;
    }

    @Override
    public Subscription observe(Consumer<DeviceSnapshot> listener) {
        observers.add(listener);
        listener.accept(current);
        return () -> observers.remove(listener);
    }

    @Override
    public void connect(DeviceTarget target, Consumer<ConnectSettlement> onSettled) {
        connects.add(target);
        pendingSettlement = onSettled;
    }

    @Override
    public void disconnect() {
        disconnects++;
    }

    @Override
    public void filterModels(Set<String> modelIds) {
    }

    @Override
    public void startScanBatch(Consumer<List<ScanResult>> onResults,
                               Consumer<DeviceFailure> onFailed,
                               Runnable onFinished,
                               Consumer<ScanResult> onRemoved) {
        scanning = true;
    }

    @Override
    public void stopScan() {
        scanning = false;
    }

    @Override
    public boolean isScanning() {
        return scanning;
    }

    @Override
    public void write(String capabilityId, ControlValue value,
                      Consumer<DeviceWriteResult> callback) {
        writes.add(capabilityId);
        writeCallback = callback;
    }

    @Override
    public void perform(DeviceAction action, Consumer<DeviceWriteResult> callback) {
        actions.add(action);
        writeCallback = callback;
    }

    @Override
    public void close() {
        observers.clear();
    }

    /** 让最近一次连接以就绪结算。 */
    public void settleReady() {
        Consumer<ConnectSettlement> settlement = pendingSettlement;
        if (settlement != null) {
            settlement.accept(new ConnectSettlement.Ready());
        }
    }

    /** 让最近一次连接以失败结算。 */
    public void settleFailed(DeviceFailure failure) {
        Consumer<ConnectSettlement> settlement = pendingSettlement;
        if (settlement != null) {
            settlement.accept(new ConnectSettlement.Failed(failure));
        }
    }

    public void settleWrite(DeviceWriteResult result) {
        Consumer<DeviceWriteResult> callback = writeCallback;
        if (callback != null) {
            callback.accept(result);
        }
    }

    /** 从端口发出一次快照（模拟 biz 上报）。 */
    public void emit(DeviceSnapshot snapshot) {
        current = snapshot;
        for (Consumer<DeviceSnapshot> observer : List.copyOf(observers)) {
            observer.accept(snapshot);
        }
    }

    public int observerCount() {
        return observers.size();
    }
}
