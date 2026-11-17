package com.yovo.lib_bluetooth.spi.permission;

/**
 * 蓝牙运行时所需权限类型枚举（扫描、连接、广播、定位）。
 *
 * <p>本枚举属于 <b>SPI</b> 而不是实现层：{@code provider/**} 的权限实现只依赖 SPI，
 * 内部实现同样只引用 SPI。放在这里才能保证提供方与消费方共用同一份契约而不互相依赖。</p>
 */
public enum YoBluetoothPermission {
    SCAN,
    CONNECT,
    ADVERTISE,
    LOCATION
}
