package com.ggec.earbuds.provider.biz_ggec_record.device;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.provider.biz_ggec_record.record.RecordAvRecordPort;
import com.yovo.biz_ggec_record.YoRecordSdk;
import com.yovo.biz_ggec_record.api.RecordRuntime;

import java.util.Objects;

/**
 * 一次 {@link YoRecordSdk} 实例的生命周期持有者。
 * 扫描与（未来）会话共用同一运行时；装配根只拿本类，不接触 SDK 类型。
 */
public final class RecordDeviceProviders {

    private final RecordRuntime runtime;
    private final RecordDeviceLinkAdapter link;
    private final RecordCapabilityCatalogAdapter catalog;
    private final RecordAvRecordPort record;

    private RecordDeviceProviders(@NonNull RecordRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.link = new RecordDeviceLinkAdapter(runtime);
        this.catalog = new RecordCapabilityCatalogAdapter();
        this.record = new RecordAvRecordPort();
    }

    @NonNull
    public static RecordDeviceProviders open(@NonNull Context context) {
        return new RecordDeviceProviders(YoRecordSdk.open(context));
    }

    @NonNull
    public DeviceLinkPort deviceLink() {
        return link;
    }

    @NonNull
    public DeviceCapabilityCatalog deviceCatalog() {
        return catalog;
    }

    @NonNull
    public RecordPort recordPort() {
        return record;
    }

    public void close() {
        link.close();
    }
}
