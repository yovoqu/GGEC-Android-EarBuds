package com.yovo.lib_bluetooth.internal.classic.profiles.state;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Profile 状态变化观察者。
 */

public interface YoCbtProfileStateObserver {
    void onProfileStateChanged(@NonNull YoCbtDeviceProfileState state);
}
