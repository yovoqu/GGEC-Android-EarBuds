package com.yovo.lib_bluetooth.internal.ble.device;

import com.yovo.lib_bluetooth.model.ble.BleAdapterState;

/**
 * BLE 硬件能力、蓝牙开关、位置开关与运行时权限的环境检查器（依赖倒置接口）。
 *
 * <p>由 Android 实现 {@link YoBleAndroidCapabilityInspector} 提供；领域组件仅依赖本接口，
 * 便于单元测试注入可控能力环境（如 sdkInt/权限/开关）。</p>
 */
public interface YoBleCapabilityInspector {

    boolean isBleSupported();

    boolean isAdapterEnabled();

    BleAdapterState adapterState();

    boolean hasScanPermission();

    boolean hasConnectPermission();

    boolean hasAdvertisePermission();

    boolean hasLocationPermission();

    boolean isLocationServicesEnabled();

    int sdkInt();
}
