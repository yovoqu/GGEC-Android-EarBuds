package com.yovo.lib_bluetooth.internal.common.concurrency;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 延迟任务调度接口，支持一次性定时执行并返回可取消句柄。
 */
public interface YoTaskScheduler {
    YoTimeoutHandle schedule(@NonNull Runnable task, long delayMillis);
}
