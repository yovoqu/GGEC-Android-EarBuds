package com.ggec.earbuds.wiring.device;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.DeviceTransport;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.provider.biz_ggec_wy.device.WyDeviceProviders;

import java.util.Objects;

/**
 * WY 系列耳机端点：一个 {@code YoWySdk} 实例，走经典蓝牙 SPP。
 *
 * <p>机型不在本类里列举——目录来自 SDK 自己的产品表，端点只负责生命周期与端口暴露。</p>
 */
public final class WyDeviceEndpoint implements DeviceEndpoint {

    private final WyDeviceProviders providers;

    public WyDeviceEndpoint(@NonNull Context context) {
        this.providers = WyDeviceProviders.open(Objects.requireNonNull(context, "context"));
    }

    @NonNull
    @Override
    public DeviceTransport transport() {
        return DeviceTransport.CLASSIC;
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
