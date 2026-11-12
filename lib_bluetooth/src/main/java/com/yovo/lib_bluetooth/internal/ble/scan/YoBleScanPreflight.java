package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;

/**
 * 扫描前置条件校验：BLE 能力、适配器开关、扫描权限，以及 Android 12 之前（或未声明位置豁免时）
 * 的位置权限与系统定位服务开关。
 *
 * <p>只做判定并返回分类失败，不读写扫描会话状态；是否据此迁移状态由控制器决定。</p>
 */
final class YoBleScanPreflight {

    private final YoBleCapabilityInspector capabilityInspector;

    YoBleScanPreflight(@NonNull YoBleCapabilityInspector capabilityInspector) {
        this.capabilityInspector = capabilityInspector;
    }

    /**
     * @return 首个不满足的前置条件对应的失败；全部满足时返回 {@code null}。
     */
    @Nullable
    BleFailure check(@NonNull BleScanConfig config, @NonNull String module) {
        if (!capabilityInspector.isBleSupported()) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.BLE_NOT_SUPPORTED, module, "Device does not support BLE hardware");
        }
        if (!capabilityInspector.isAdapterEnabled()) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.BLUETOOTH_DISABLED, module, "Bluetooth adapter is disabled");
        }
        if (!capabilityInspector.hasScanPermission()) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.PERMISSION_SCAN_DENIED, module, "Missing BLE scan permission");
        }
        // Android < 12 (API < 31) 或未声明位置豁免时需检查位置权限与系统位置服务开关
        boolean requiresLocation = capabilityInspector.sdkInt() < 31 || !config.locationExempt();
        if (requiresLocation) {
            if (!capabilityInspector.hasLocationPermission()) {
                return YoBleErrorMapper.failure(
                        BleErrorCode.PERMISSION_LOCATION_DENIED, module,
                        "Location permission required for BLE scan on Android < 12");
            }
            if (!capabilityInspector.isLocationServicesEnabled()) {
                return YoBleErrorMapper.failure(
                        BleErrorCode.LOCATION_SERVICES_DISABLED, module,
                        "System Location services must be enabled for BLE scan on Android < 12");
            }
        }
        return null;
    }
}
