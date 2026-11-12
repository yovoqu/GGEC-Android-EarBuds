package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.api.HsNearbyObserver;
import com.yovo.biz_ggec_hs.api.YoHsNearby;
import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;

import java.util.Objects;

/**
 * 附近耳机扫描门面：订阅 lib 的原始 BLE 广播，只把产品目录里的耳机交给上层。
 *
 * <p>本类只编排四处组件——会话状态机 {@link HsNearbyScanSession}、广播识别
 * {@link HsNearbyDevices}、lost 记账 {@link HsNearbyScanLedger}、失败映射
 * {@link HsScanFailures}（后三者由 {@link HsNearbyScanCallbacks} 在回调路径上驱动），
 * 自身不持有扫描状态、不做识别。不设置扫描参数，也不把绑定状态写进扫描结果。</p>
 */
public final class HsNearbyScanner implements YoHsNearby {

    private final YoBleClient bleClient;
    private final HsNearbyScanSession scanSession = new HsNearbyScanSession();

    public HsNearbyScanner(@NonNull YoBleClient bleClient) {
        this.bleClient = Objects.requireNonNull(bleClient, "bleClient");
    }

    @Override
    public void start(@NonNull HsNearbyObserver observer) {
        Objects.requireNonNull(observer, "observer");
        stop();
        long generation = scanSession.begin();
        // 订阅在会话锁外发起：lib 侧的同步失败回调会就地结束本次会话，持锁调用会与状态机自锁。
        HsNearbyScanCallbacks callbacks =
                new HsNearbyScanCallbacks(scanSession, generation, observer);
        BleOperationHandle handle = bleClient.subscribeScan(callbacks);
        // 订阅期间会话可能已被同步回调结束，此时由状态机在锁外取消刚拿到的句柄。
        scanSession.attach(generation, handle);
    }

    @Override
    public void stop() {
        scanSession.stop();
    }

    @Override
    public boolean isScanning() {
        return scanSession.isScanning();
    }
}
