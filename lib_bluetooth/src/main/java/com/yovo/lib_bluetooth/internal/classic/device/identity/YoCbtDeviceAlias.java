package com.yovo.lib_bluetooth.internal.classic.device.identity;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 设备显示名称与用户别名。
 */

public final class YoCbtDeviceAlias {
    private final String displayName;
    private final String alias;

    public YoCbtDeviceAlias(@NonNull String displayName, @NonNull String alias) {
        this.displayName = displayName;
        this.alias = alias;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    public String getAlias() {
        return alias;
    }

    @NonNull
    public static YoCbtDeviceAlias empty() {
        return new YoCbtDeviceAlias("", "");
    }

    @NonNull
    public YoCbtDeviceAlias merge(@NonNull YoCbtDeviceAlias incoming) {
        String mergedDisplayName = displayName.isEmpty() ? incoming.displayName : displayName;
        String mergedAlias = alias.isEmpty() ? incoming.alias : alias;
        return new YoCbtDeviceAlias(mergedDisplayName, mergedAlias);
    }
}
