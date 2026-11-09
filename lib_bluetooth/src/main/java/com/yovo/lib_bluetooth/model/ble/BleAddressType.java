package com.yovo.lib_bluetooth.model.ble;

/**
 * BLE 设备地址类型（区分 Public / Random Static / RPA / NRPA，不与 Classic 地址混淆）。
 */
public enum BleAddressType {
    PUBLIC,
    RANDOM_STATIC,
    RESOLVABLE_PRIVATE,
    NON_RESOLVABLE_PRIVATE,
    UNKNOWN
}
