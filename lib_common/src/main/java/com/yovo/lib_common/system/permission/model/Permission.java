package com.yovo.lib_common.system.permission.model;

/**
 * 统一权限目录：定义权限的 manifest 名称、类型、适用 SDK 区间与请求码。
 *
 * <p>上层（app / 业务 SDK）只依赖该枚举与 {@link PermissionCore}，
 * 不再各自散落权限检查逻辑。</p>
 */
public enum Permission {

    BLUETOOTH(
            "android.permission.BLUETOOTH",
            PermissionKind.INSTALL_TIME,
            0x0000,
            0,
            30),
    BLUETOOTH_ADMIN(
            "android.permission.BLUETOOTH_ADMIN",
            PermissionKind.INSTALL_TIME,
            0x0000,
            0,
            30),
    BLUETOOTH_CONNECT(
            "android.permission.BLUETOOTH_CONNECT",
            PermissionKind.RUNTIME,
            0x0001,
            31,
            0),
    BLUETOOTH_SCAN(
            "android.permission.BLUETOOTH_SCAN",
            PermissionKind.RUNTIME,
            0x0002,
            31,
            0),
    ACCESS_FINE_LOCATION(
            "android.permission.ACCESS_FINE_LOCATION",
            PermissionKind.RUNTIME,
            0x0003,
            23,
            30),
    ACCESS_COARSE_LOCATION(
            "android.permission.ACCESS_COARSE_LOCATION",
            PermissionKind.RUNTIME,
            0x0003,
            23,
            30),
    BLUETOOTH_ADVERTISE(
            "android.permission.BLUETOOTH_ADVERTISE",
            PermissionKind.RUNTIME,
            0x0007,
            31,
            0),
    POST_NOTIFICATIONS(
            "android.permission.POST_NOTIFICATIONS",
            PermissionKind.RUNTIME,
            0x0004,
            33,
            0),
    WRITE_EXTERNAL_STORAGE(
            "android.permission.WRITE_EXTERNAL_STORAGE",
            PermissionKind.RUNTIME,
            0x0005,
            23,
            29),
    READ_EXTERNAL_STORAGE(
            "android.permission.READ_EXTERNAL_STORAGE",
            PermissionKind.RUNTIME,
            0x0005,
            23,
            29),
    MANAGE_EXTERNAL_STORAGE(
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            PermissionKind.SPECIAL_ACCESS,
            0x0006,
            30,
            0),
    REQUEST_INSTALL_PACKAGES(
            "android.permission.REQUEST_INSTALL_PACKAGES",
            PermissionKind.SPECIAL_ACCESS,
            0x0008,
            26,
            0),
    SYSTEM_ALERT_WINDOW(
            "android.permission.SYSTEM_ALERT_WINDOW",
            PermissionKind.SPECIAL_ACCESS,
            0x0009,
            23,
            0),
    WRITE_SETTINGS(
            "android.permission.WRITE_SETTINGS",
            PermissionKind.SPECIAL_ACCESS,
            0x000A,
            23,
            0),
    BIND_NOTIFICATION_LISTENER_SERVICE(
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
            PermissionKind.SPECIAL_ACCESS,
            0x000B,
            24,
            0);

    private final String androidPermission;
    private final PermissionKind kind;
    private final int requestCode;
    private final int minSdk;
    private final int maxSdk;

    Permission(String androidPermission,
               PermissionKind kind,
               int requestCode,
               int minSdk,
               int maxSdk) {
        this.androidPermission = androidPermission;
        this.kind = kind;
        this.requestCode = requestCode;
        this.minSdk = minSdk;
        this.maxSdk = maxSdk;
    }

    public String androidPermission() {
        return androidPermission;
    }

    public PermissionKind kind() {
        return kind;
    }

    public int requestCode() {
        return requestCode;
    }

    /**
     * 当前系统版本是否需要该权限（minSdk <= sdkInt <= maxSdk，maxSdk=0 表示无上限）。
     */
    public boolean isRequired(int sdkInt) {
        return sdkInt >= minSdk && (maxSdk == 0 || sdkInt <= maxSdk);
    }

    public boolean isRuntime() {
        return kind == PermissionKind.RUNTIME;
    }

    public boolean isInstallTime() {
        return kind == PermissionKind.INSTALL_TIME;
    }

    public boolean isSpecialAccess() {
        return kind == PermissionKind.SPECIAL_ACCESS;
    }
}
