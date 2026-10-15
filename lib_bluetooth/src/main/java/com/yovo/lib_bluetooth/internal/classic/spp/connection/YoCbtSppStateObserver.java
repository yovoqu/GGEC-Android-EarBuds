package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP 连接阶段变化观察者。
 */

public interface YoCbtSppStateObserver {
    void onSppStateChanged(@NonNull YoCbtSppConnectionSnapshot snapshot);
}
