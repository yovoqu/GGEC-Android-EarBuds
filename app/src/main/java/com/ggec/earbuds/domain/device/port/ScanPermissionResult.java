package com.ggec.earbuds.domain.device.port;

/** 宿主权限请求的结论：是否属于本请求、是否授予、是否被永久拒绝。 */
public record ScanPermissionResult(boolean handled, boolean granted, boolean permanentlyDenied) {
}
