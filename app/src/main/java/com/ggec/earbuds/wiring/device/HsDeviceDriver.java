package com.ggec.earbuds.wiring.device;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.provider.biz_ggec_hs.HsDeviceProviders;

import java.util.Objects;
import java.util.Set;

/**
 * HS 系列耳机设备驱动（支持 HS01, HS02）。
 */
public final class HsDeviceDriver implements DeviceDriver {

    private static final Set<String> SUPPORTED_MODELS = Set.of("HS01", "HS02");

    private final HsDeviceProviders providers;

    public HsDeviceDriver(@NonNull Context context) {
        this.providers = HsDeviceProviders.open(Objects.requireNonNull(context, "context"));
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
