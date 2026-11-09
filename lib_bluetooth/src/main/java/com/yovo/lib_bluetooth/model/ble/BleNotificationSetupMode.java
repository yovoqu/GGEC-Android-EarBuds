package com.yovo.lib_bluetooth.model.ble;

/**
 * Notification / Indication 订阅建立模式（对标 RxAndroidBle NotificationSetupMode）。
 */
public enum BleNotificationSetupMode {
    /** 标准流程：先调用本地 setCharacteristicNotification(true)，再写入 0x2902 CCCD 并等待写入完成。 */
    DEFAULT,
    /** 兼容模式：若特征缺失 0x2902 CCCD，仅执行本地 setCharacteristicNotification 并视为就绪。 */
    COMPAT,
    /** 快速模式：本地开启后立即回调就绪并异步写入 CCCD，不阻塞首帧到达。 */
    QUICK_SETUP
}
