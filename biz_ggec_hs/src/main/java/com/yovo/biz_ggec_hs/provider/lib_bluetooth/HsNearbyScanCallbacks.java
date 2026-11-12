package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.YoHsBroadcast;
import com.yovo.biz_ggec_hs.api.HsNearbyObserver;
import com.yovo.biz_ggec_hs.api.YoHsBroadcastSession;
import com.yovo.biz_ggec_hs.model.HsNearbyDevice;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 把 lib 的扫描回调翻译成 {@link HsNearbyObserver} 回调：先按会话代次放行，
 * 再把帧交给 {@link HsNearbyDevices} 识别、把已上报的设备交给 {@link HsNearbyScanLedger} 记账、
 * 把失败码交给 {@link HsScanFailures} 映射。识别规则、失败分类与会话生命周期都不在这里实现。
 *
 * <p>广播跨帧组装的后半段状态随本对象创建、随会话丢弃，因此一个实例只服务一次 {@code start()}。</p>
 */
final class HsNearbyScanCallbacks implements BleScanObserver {

    private final HsNearbyScanSession scanSession;
    private final long generation;
    private final HsNearbyObserver observer;
    private final YoHsBroadcastSession broadcastSession = YoHsBroadcast.openSession();
    private final HsNearbyScanLedger ledger = new HsNearbyScanLedger();

    HsNearbyScanCallbacks(@NonNull HsNearbyScanSession scanSession,
                          long generation,
                          @NonNull HsNearbyObserver observer) {
        this.scanSession = scanSession;
        this.generation = generation;
        this.observer = observer;
    }

    @Override
    public void onDeviceFound(@NonNull BleScanResult result) {
        publish(Collections.singletonList(result));
    }

    @Override
    public void onBatchScanResults(@NonNull List<BleScanResult> results) {
        publish(results);
    }

    @Override
    public void onScanResultLost(@NonNull BleScanResult lostResult) {
        if (lostResult == null || !scanSession.isCurrent(generation)) {
            return;
        }
        HsNearbyDevice device = ledger.forget(lostResult.identity().deviceId().value());
        if (device != null) {
            observer.onLost(device);
        }
    }

    @Override
    public void onScanFailed(@NonNull BleFailure failure) {
        if (!scanSession.end(generation)) {
            return;
        }
        observer.onFailed(HsScanFailures.map(failure.errorCode()));
        observer.onFinished();
    }

    @Override
    public void onScanStopped() {
        if (scanSession.end(generation)) {
            observer.onFinished();
        }
    }

    private void publish(@NonNull List<BleScanResult> rawResults) {
        if (!scanSession.isCurrent(generation)) {
            return;
        }
        List<HsNearbyDevice> batch = null;
        for (BleScanResult raw : rawResults) {
            if (raw == null) {
                continue;
            }
            HsNearbyDevice device = HsNearbyDevices.map(raw, broadcastSession);
            if (device == null) {
                continue;
            }
            ledger.remember(raw.identity().deviceId().value(), device);
            if (batch == null) {
                batch = new ArrayList<>();
            }
            batch.add(device);
        }
        if (batch != null && !batch.isEmpty()) {
            observer.onUpdated(Collections.unmodifiableList(batch));
        }
    }
}
