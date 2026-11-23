package com.ggec.earbuds.record.wiring;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.port.ScanPermissionRequest;
import com.ggec.earbuds.domain.record.port.RecordControlPort;
import com.ggec.earbuds.domain.record.port.RecordScanPort;
import com.ggec.earbuds.provider.biz_ggec_record.RecordPortAdapter;
import com.ggec.earbuds.provider.biz_ggec_record.RecordProviders;
import com.ggec.earbuds.provider.lib_common.permission.BluetoothScanPermissionRequest;

import java.util.Objects;

/**
 * Record 独立入口装配根。不挂入主壳 AppContainer，由 RecordMainActivity 自行持有。
 *
 * <p>装配根是唯一可 import provider/** 的位置：它把 {@code provider/biz_ggec_record} 的适配器
 * 以领域端口（{@link RecordControlPort}/{@link RecordScanPort}）暴露给 UI，UI 不接触任何 biz 类型。
 * 扫描权限经既有 {@link ScanPermissionRequest} 领域端口（provider 实现 {@link BluetoothScanPermissionRequest}）。</p>
 */
public final class RecordGraph {

    private final RecordPortAdapter adapter;

    public RecordGraph(@NonNull Context appContext) {
        this.adapter = new RecordPortAdapter(
                RecordProviders.open(Objects.requireNonNull(appContext, "appContext")));
    }

    @NonNull
    public RecordControlPort control() {
        return adapter;
    }

    @NonNull
    public RecordScanPort scan() {
        return adapter;
    }

    @NonNull
    public ScanPermissionRequest scanPermissionRequest(@NonNull Activity host) {
        return new BluetoothScanPermissionRequest(host);
    }

    public void close() {
        adapter.close();
    }
}
