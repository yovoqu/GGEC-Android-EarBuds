package com.yovo.lib_common.system.permission.model;

import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时权限请求结果，按请求顺序映射到各权限状态。
 *
 * <p>未被本服务处理的回调不再是「无信息忽略」：{@link #ignoreReason()} 给出
 * {@link PermissionIgnoreReason} 分类，便于上层区分正常忽略与调用点配置错误。</p>
 */
public final class PermissionResult {

    private final boolean handled;
    private final Map<Permission, PermissionStatus> statuses;
    private final Map<Permission, Boolean> permanentlyDenied;
    @Nullable private final PermissionIgnoreReason ignoreReason;

    private PermissionResult(boolean handled,
                             @NonNull Map<Permission, PermissionStatus> statuses,
                             @NonNull Map<Permission, Boolean> permanentlyDenied,
                             @Nullable PermissionIgnoreReason ignoreReason) {
        this.handled = handled;
        this.statuses = statuses;
        this.permanentlyDenied = permanentlyDenied;
        this.ignoreReason = ignoreReason;
    }

    /**
     * 未被处理的结果，必须带上原因分类。
     */
    @NonNull
    public static PermissionResult ignored(@NonNull PermissionIgnoreReason reason) {
        return new PermissionResult(
                false, Collections.emptyMap(), Collections.emptyMap(), reason);
    }

    @NonNull
    public static PermissionResult of(@NonNull List<Permission> pending,
                                      @Nullable int[] grantResults) {
        return of(pending, grantResults, Collections.emptyMap());
    }

    /**
     * 映射请求结果，并标记被“不再询问”的权限。
     *
     * @param permanentlyDenied 权限 → 是否永久拒绝（用户勾选“不再询问”或系统不再弹窗）。
     */
    @NonNull
    public static PermissionResult of(@NonNull List<Permission> pending,
                                      @Nullable int[] grantResults,
                                      @NonNull Map<Permission, Boolean> permanentlyDenied) {
        Map<Permission, PermissionStatus> statuses = new EnumMap<>(Permission.class);
        int size = Math.min(pending.size(),
                grantResults == null ? 0 : grantResults.length);
        for (int i = 0; i < size; i++) {
            statuses.put(pending.get(i),
                    grantResults[i] == PackageManager.PERMISSION_GRANTED
                            ? PermissionStatus.GRANTED
                            : PermissionStatus.DENIED);
        }
        return new PermissionResult(!pending.isEmpty(), statuses, permanentlyDenied, null);
    }

    /** 是否为本次权限管理服务处理的请求。 */
    public boolean handled() {
        return handled;
    }

    /**
     * 未被处理时的原因分类；已处理的结果返回 {@code null}。
     */
    @Nullable
    public PermissionIgnoreReason ignoreReason() {
        return ignoreReason;
    }

    public boolean contains(@NonNull Permission permission) {
        return statuses.containsKey(permission);
    }

    @NonNull
    public PermissionStatus statusOf(@NonNull Permission permission) {
        PermissionStatus status = statuses.get(permission);
        return status != null ? status : PermissionStatus.DENIED;
    }

    public boolean allGranted() {
        return !statuses.isEmpty() && !statuses.containsValue(PermissionStatus.DENIED);
    }

    /**
     * 该权限是否被用户“不再询问”式拒绝（仅运行时权限有意义）。
     */
    public boolean isPermanentlyDenied(@NonNull Permission permission) {
        Boolean value = permanentlyDenied.get(permission);
        return Boolean.TRUE.equals(value);
    }

    public boolean hasPermanentlyDenied() {
        return permanentlyDenied.containsValue(Boolean.TRUE);
    }
}
