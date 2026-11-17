package com.ggec.earbuds.provider.biz_ggec_wy;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.yovo.biz_ggec_wy.YoWySdk;

import java.util.Objects;

/**
 * {@code YoWySdk} 实例的生命周期持有者，装配为 APP 对应的端口。
 */
public final class WyDeviceProviders {

    private final YoWySdk sdk;
    private final WyDeviceLinkAdapter link;
    private final WyCapabilityCatalogAdapter catalog;
    private final WyRecordAdapter record;

    private WyDeviceProviders(@NonNull YoWySdk sdk) {
        this.sdk = Objects.requireNonNull(sdk, "sdk");
        this.link = new WyDeviceLinkAdapter(sdk);
        this.catalog = new WyCapabilityCatalogAdapter(sdk);
        this.record = new WyRecordAdapter(sdk);
    }

    @NonNull
    public static WyDeviceProviders open(@NonNull Context context) {
        return new WyDeviceProviders(YoWySdk.open(context));
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
        sdk.close();
    }
}
