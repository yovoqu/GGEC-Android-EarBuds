package com.yovo.lib_bluetooth.internal.common.permission;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 蓝牙权限的必要程度（必需、推荐、可选）。
 */

public enum YoBluetoothPermissionRequirement {
    REQUIRED,
    PREFERRED,
    OPTIONAL;

    @NonNull
    public String describe() {
        switch (this) {
            case REQUIRED:
                return "This permission is strictly required. The operation cannot proceed without it.";
            case PREFERRED:
                return "This permission is strongly preferred. The operation may degrade or offer reduced functionality without it.";
            case OPTIONAL:
                return "This permission is optional. The operation will work with full functionality without it.";
            default:
                return name();
        }
    }
}
