package com.ggec.earbuds.wiring.device;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.RecordPort;

import java.util.Set;

/**
 * 抽象设备驱动契约，严禁上层绑定具体设备型号。
 */
public interface DeviceDriver {

    /**
     * 该驱动是否支持此产品型号（如 HS01, HS02, WY01）。
     */
    boolean supportsModel(@NonNull String modelId);

    /**
     * 该驱动支持的全部产品型号集合。
     */
    @NonNull
    Set<String> supportedModels();

    @NonNull
    DeviceCapabilityCatalog catalog();

    @NonNull
    DeviceLinkPort link();

    @NonNull
    RecordPort record();

    void close();
}
