package com.yovo.lib_common.system.permission.model;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 业务能力 → 权限集合的策略映射。
 *
 * <p>上层只声明“我要什么能力”，由本策略按系统版本输出所需权限，
 * 避免业务代码里散落 Build.VERSION 判断与权限清单。</p>
 */
public enum PermissionPolicy {

    /**
     * 发现设备 + 连接已配对设备。
     * Android 12 起为 BLUETOOTH_CONNECT 与 BLUETOOTH_SCAN；
     * Android 6–11 的运行时权限只有定位，且定位只服务于扫描，不服务于 SPP 连接。
     */
    BLUETOOTH_FULL {
        @NonNull
        @Override
        public List<Permission> requiredFor(int sdkInt) {
            if (sdkInt >= 31) {
                return Arrays.asList(
                        Permission.BLUETOOTH_CONNECT,
                        Permission.BLUETOOTH_SCAN);
            }
            if (sdkInt >= 23) {
                return Collections.singletonList(Permission.ACCESS_FINE_LOCATION);
            }
            return Collections.emptyList();
        }
    },

    /**
     * 打开 RFCOMM/SPP。
     *
     * <p>与已配对设备通信：Android 12+ 为运行时 {@code BLUETOOTH_CONNECT}，
     * 更早版本为安装时 {@code BLUETOOTH}。连接前要调用 {@code cancelDiscovery()}，
     * targetSdk 31+ 时该调用以及 {@code BluetoothSocket.connect()} 内部的 SDP
     * 需要 {@code BLUETOOTH_SCAN}；Android 11 及更低版本对应安装时 {@code BLUETOOTH_ADMIN}。
     * 定位权限只属于设备发现，连接已知设备时不申请。</p>
     */
    BLUETOOTH_SPP {
        @NonNull
        @Override
        public List<Permission> requiredFor(int sdkInt) {
            if (sdkInt >= 31) {
                return Arrays.asList(
                        Permission.BLUETOOTH_CONNECT,
                        Permission.BLUETOOTH_SCAN);
            }
            return Arrays.asList(Permission.BLUETOOTH, Permission.BLUETOOTH_ADMIN);
        }
    },

    /**
     * 经典/BLE 扫描。Android 11 及更低版本这里只包含安装时蓝牙权限；
     * 定位由发现流程单独检查，避免把定位算进 SPP 连接。
     */
    BLUETOOTH_SCAN {
        @NonNull
        @Override
        public List<Permission> requiredFor(int sdkInt) {
            if (sdkInt >= 31) {
                return Collections.singletonList(Permission.BLUETOOTH_SCAN);
            }
            return Arrays.asList(Permission.BLUETOOTH, Permission.BLUETOOTH_ADMIN);
        }
    },

    /** 连接 / 已配对查询 / GATT。 */
    BLUETOOTH_CONNECT {
        @NonNull
        @Override
        public List<Permission> requiredFor(int sdkInt) {
            if (sdkInt >= 31) {
                return Collections.singletonList(Permission.BLUETOOTH_CONNECT);
            }
            return Collections.singletonList(Permission.BLUETOOTH);
        }
    },

    /** BLE 广播。 */
    BLUETOOTH_ADVERTISE {
        @NonNull
        @Override
        public List<Permission> requiredFor(int sdkInt) {
            if (sdkInt >= 31) {
                return Collections.singletonList(Permission.BLUETOOTH_ADVERTISE);
            }
            return Collections.singletonList(Permission.BLUETOOTH_ADMIN);
        }
    },

    /** 通知推送（Android 13+ 运行时权限）。 */
    NOTIFICATION {
        @NonNull
        @Override
        public List<Permission> requiredFor(int sdkInt) {
            return sdkInt >= 33
                    ? Collections.singletonList(Permission.POST_NOTIFICATIONS)
                    : Collections.emptyList();
        }
    },

    /** 向共享存储写入（Android 10 运行时权限；Android 11+ 所有文件访问）。 */
    STORAGE_WRITE {
        @NonNull
        @Override
        public List<Permission> requiredFor(int sdkInt) {
            if (sdkInt >= 30) {
                return Collections.singletonList(Permission.MANAGE_EXTERNAL_STORAGE);
            }
            if (sdkInt >= 23) {
                return Collections.singletonList(Permission.WRITE_EXTERNAL_STORAGE);
            }
            return Collections.emptyList();
        }
    },

    /**
     * 直接写入宿主选择的绝对目录（非应用私有目录）：
     * Android 11+ 需要所有文件访问，Android 10 需要旧版写权限，更早版本无需权限。
     */
    STORAGE_DIRECT {
        @NonNull
        @Override
        public List<Permission> requiredFor(int sdkInt) {
            if (sdkInt >= 30) {
                return Collections.singletonList(Permission.MANAGE_EXTERNAL_STORAGE);
            }
            if (sdkInt == 29) {
                return Collections.singletonList(Permission.WRITE_EXTERNAL_STORAGE);
            }
            return Collections.emptyList();
        }
    },

    /** 安装未知来源应用（Android 8+ 特殊访问权限）。 */
    INSTALL_UNKNOWN_SOURCES {
        @NonNull
        @Override
        public List<Permission> requiredFor(int sdkInt) {
            return sdkInt >= 26
                    ? Collections.singletonList(Permission.REQUEST_INSTALL_PACKAGES)
                    : Collections.emptyList();
        }
    };

    @NonNull
    public abstract List<Permission> requiredFor(int sdkInt);
}
