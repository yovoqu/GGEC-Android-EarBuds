package com.yovo.lib_bluetooth.internal.common.bluetooth.radio;

/**
 * 蓝牙射频仲裁状态。
 */
public enum YoBtRadioState {
    /** 射频空闲，无活跃扫描、发现或连接抢占。 */
    IDLE,
    /** BLE 扫描占用射频。 */
    BLE_SCANNING,
    /** 经典蓝牙发现（Inquiry）占用射频。 */
    CLASSIC_DISCOVERING,
    /** 正在建立连接（GATT 或 SPP 前置抢占），射频保持最高优先级保护。 */
    CONNECTING
}
