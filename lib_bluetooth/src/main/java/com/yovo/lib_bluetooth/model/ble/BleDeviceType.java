package com.yovo.lib_bluetooth.model.ble;

/**
 * 协议栈在 BLE 设备对象上报告的射频模式，对应 {@code BluetoothDevice.getType()}。
 *
 * <p>这是射频能力，不是地址。{@link #CLASSIC} 只表示“栈认为该对象没有 LE 射频”，
 * 不提供、也不等于经典蓝牙 MAC。</p>
 */
public enum BleDeviceType {
    /** 单模 BLE。 */
    LE,
    /** 双模：同时具备 LE 与经典射频。BLE 地址仍单独保存。 */
    DUAL,
    /** 栈报告仅经典射频。扫描结果若出现该值，地址依然是本次 BLE 扫描看到的地址。 */
    CLASSIC,
    UNKNOWN
}
