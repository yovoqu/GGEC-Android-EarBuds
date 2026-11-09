package com.yovo.lib_bluetooth.model.ble;

/**
 * GATT Server 特征/描述符值隔离作用域（对标 Nordic BleServerManager）。
 */
public enum BleCharacteristicScope {
    /** 所有中心客户端共享同一份服务端特征值。 */
    SHARED,
    /** 为每个已连接的中心客户端维护独立特征值实例（默认隔离）。 */
    PER_CLIENT
}
