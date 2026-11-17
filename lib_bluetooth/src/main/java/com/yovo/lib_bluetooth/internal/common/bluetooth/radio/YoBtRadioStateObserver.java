package com.yovo.lib_bluetooth.internal.common.bluetooth.radio;

import androidx.annotation.NonNull;

/**
 * 射频状态观察者接口。
 */
public interface YoBtRadioStateObserver {

    /**
     * 当射频仲裁状态发生流转时回调。
     *
     * @param oldState 流转前状态
     * @param newState 流转后新状态
     */
    default void onRadioStateChanged(@NonNull YoBtRadioState oldState, @NonNull YoBtRadioState newState) {}

    /**
     * 当某个射频租约成功被获取时回调。
     *
     * @param lease 获得的租约
     */
    default void onLeaseAcquired(@NonNull YoBtRadioLease lease) {}

    /**
     * 当某个射频租约被释放时回调。
     *
     * @param lease 被释放的租约
     */
    default void onLeaseReleased(@NonNull YoBtRadioLease lease) {}

    /**
     * 当发现（Inquiry）被抢占或强制取消时回调。
     *
     * @param reason 抢占原因说明
     */
    default void onDiscoveryPreempted(@NonNull String reason) {}
}
