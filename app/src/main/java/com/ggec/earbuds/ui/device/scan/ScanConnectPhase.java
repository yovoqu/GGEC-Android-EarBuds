package com.ggec.earbuds.ui.device.scan;

/** 扫描页上的连接尝试。成功和失败都回到 {@link #IDLE}，成功由一次性事件离开本页。 */
public enum ScanConnectPhase {
    IDLE,
    CONNECTING
}
