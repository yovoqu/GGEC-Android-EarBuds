package com.yovo.biz_ggec_hs.model;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Map;

/**
 * 设备主动上报。未关心的事件保持空实现。
 * 回调在主线程。
 */
public interface HsDeviceObserver {
    default void onBatteryStateChanged(boolean leftUpdated,
                                       boolean rightUpdated,
                                       boolean caseUpdated) {
    }

    default void onEqMode(int mode) {
    }

    default void onGameMode(boolean enabled) {
    }

    default void onFindAlert(int code, int state) {
    }

    default void onVoiceAssistant(int code, int status) {
    }

    default void onBeginnerMode(int earType, int actionType) {
    }

    default void onEarExchange(int earType) {
    }

    default void onServiceStatus(Map<String, String> status) {
    }

    default void onPairedDevices(List<Map<String, String>> devices) {
    }

    /**
     * 固件版本、耳机颜色或在仓状态有新的已知值时投递；三项都未知时不投递。
     */
    default void onDeviceInfo(@NonNull HsDeviceInfo info) {
    }
}
