package com.ggec.earbuds.wiring.device;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.DeviceTransport;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.provider.biz_ggec_record.device.RecordDeviceProviders;

import java.util.Objects;

/**
 * Record（Ambiq 录播）端点：传输 {@link DeviceTransport#BLE_AV}，与 HS 的 {@code BLE} 分离。
 */
public final class RecordDeviceEndpoint implements DeviceEndpoint {

    private final RecordDeviceProviders providers;

    public RecordDeviceEndpoint(@NonNull Context context) {
        this.providers = RecordDeviceProviders.open(Objects.requireNonNull(context, "context"));
    }

    @NonNull
    @Override
    public DeviceTransport transport() {
        return DeviceTransport.BLE_AV;
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
