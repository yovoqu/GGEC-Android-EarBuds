package com.yovo.lib_bluetooth.internal.common.concurrency;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 延迟任务取消句柄，用于取消尚未执行的定时任务。
 */
public interface YoTimeoutHandle {
    void cancel();
}
