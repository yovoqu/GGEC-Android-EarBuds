package com.ggec.earbuds.domain.device.port;

/**
 * 扫描所需的蓝牙权限状态。平台侧实现负责查询系统权限；
 * 领域与界面只读这一布尔事实，不接触 Android 权限 API。
 */
public interface ScanPermissionPort {

    /** 当前是否已获得蓝牙扫描所需的全部权限。 */
    boolean isGranted();
}
