package com.yovo.lib_bluetooth.internal.classic.protocol.command;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 命令生命周期观察者（提交、完成、超时等）。
 */

public interface YoCbtCommandObserver {
    void onCommandResult(@NonNull YoCbtSppConnectionId connectionId,
                         @NonNull YoCbtCommandResult result);
}
