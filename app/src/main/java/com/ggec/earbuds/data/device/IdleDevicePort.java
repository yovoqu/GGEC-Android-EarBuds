package com.ggec.earbuds.data.device;

import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DevicePort;
import com.ggec.earbuds.domain.device.port.Subscription;

import java.util.function.Consumer;

/**
 * 闲置设备端口：尚未接入真实蓝牙 SDK 时的空实现，不产生任何模拟设备、扫描结果或假电量数据。
 */
public final class IdleDevicePort implements DevicePort {

    private static final Subscription NO_OP_SUBSCRIPTION = () -> {
    };

    @Override
    public DeviceSnapshot snapshot() {
        return DeviceSnapshot.idle();
    }

    @Override
    public Subscription observe(Consumer<DeviceSnapshot> listener) {
        if (listener != null) {
            listener.accept(DeviceSnapshot.idle());
        }
        return NO_OP_SUBSCRIPTION;
    }

    @Override
    public void connect(String modelId, String variantId, String mac) {
        // 未接入真实蓝牙 SDK 前保持未连接空态，不伪造连接成功。
    }

    @Override
    public void disconnect() {
        // 闲置实现始终处于未连接空态。
    }

    @Override
    public void startScan(Consumer<ScanResult> onResult, Runnable onFinished) {
        if (onFinished != null) {
            onFinished.run();
        }
    }

    @Override
    public void stopScan() {
        // 闲置实现不会启动后台扫描。
    }

    @Override
    public boolean isScanning() {
        return false;
    }
}
