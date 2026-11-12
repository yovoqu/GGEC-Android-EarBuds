package com.ggec.earbuds.ui.device.scan;

/** 宿主权限请求的结论：是否属于本请求、是否授予、是否被永久拒绝。 */
public record ScanPermissionResult(boolean handled, boolean granted, boolean permanentlyDenied) {

    public static ScanPermissionResult notMine() {
        return new ScanPermissionResult(false, false, false);
    }

    public static ScanPermissionResult answered(boolean granted, boolean permanentlyDenied) {
        return new ScanPermissionResult(true, granted, permanentlyDenied);
    }
}
