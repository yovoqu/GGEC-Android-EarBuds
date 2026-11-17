package com.ggec.earbuds.wiring.device;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.provider.biz_ggec_wy.WyDeviceProviders;

import java.util.Objects;
import java.util.Set;

/**
 * WY 系列耳机设备驱动（支持 WY01，经典蓝牙 SPP）。
 */
public final class WyDeviceDriver implements DeviceDriver {

    private static final Set<String> SUPPORTED_MODELS = Set.of("WY01");

    private final WyDeviceProviders providers;

    public WyDeviceDriver(@NonNull Context context) {
        this.providers = WyDeviceProviders.open(Objects.requireNonNull(context, "context"));
    }

    @Override
    public boolean supportsModel(@NonNull String modelId) {
        return SUPPORTED_MODELS.contains(modelId);
    }

    @NonNull
    @Override
    public Set<String> supportedModels() {
        return SUPPORTED_MODELS;
    }

    @NonNull
    @Override
    public DeviceCapabilityCatalog catalog() {
        return providers.deviceCatalog();
    }

    @NonNull
    @Override
    public DeviceLinkPort link() {
        return providers.deviceLink();
    }

    @NonNull
    @Override
    public RecordPort record() {
        return providers.recordPort();
    }

    @Override
    public void close() {
        providers.close();
    }
}
