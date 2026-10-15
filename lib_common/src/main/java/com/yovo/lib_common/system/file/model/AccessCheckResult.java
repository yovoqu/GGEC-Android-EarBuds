package com.yovo.lib_common.system.file.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.permission.model.Permission;

import java.util.Collections;
import java.util.List;

/**
 * 访问门禁结果：只检查、不请求。
 */
public final class AccessCheckResult {

    private final boolean allowed;

    @Nullable
    private final AccessDenyReason reason;

    @NonNull
    private final List<Permission> missingPermissions;

    private AccessCheckResult(boolean allowed,
                              @Nullable AccessDenyReason reason,
                              @NonNull List<Permission> missingPermissions) {
        this.allowed = allowed;
        this.reason = reason;
        this.missingPermissions = Collections.unmodifiableList(missingPermissions);
    }

    @NonNull
    public static AccessCheckResult allowed() {
        return new AccessCheckResult(true, null, Collections.emptyList());
    }

    @NonNull
    public static AccessCheckResult denied(@NonNull AccessDenyReason reason) {
        return new AccessCheckResult(false, reason, Collections.emptyList());
    }

    @NonNull
    public static AccessCheckResult denied(@NonNull AccessDenyReason reason,
                                           @NonNull List<Permission> missingPermissions) {
        return new AccessCheckResult(false, reason, missingPermissions);
    }

    public boolean isAllowed() {
        return allowed;
    }

    @Nullable
    public AccessDenyReason reason() {
        return reason;
    }

    @NonNull
    public List<Permission> missingPermissions() {
        return missingPermissions;
    }
}
