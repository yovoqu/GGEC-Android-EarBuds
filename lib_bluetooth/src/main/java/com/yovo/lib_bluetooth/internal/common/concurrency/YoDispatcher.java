package com.yovo.lib_bluetooth.internal.common.concurrency;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 任务分发接口，将 Runnable 投递到目标线程执行。
 */
public interface YoDispatcher {
    void dispatch(@NonNull Runnable task);
}
