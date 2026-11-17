package com.ggec.earbuds.domain.device.model;

/**
 * 一条设备链路使用的无线传输。
 *
 * <p>一个 SDK 实例只服务一种传输，因此「当前连的是哪种传输」是连接路由的唯一状态：
 * 写入、动作、快照与录音能力都跟着它走。APP 侧只按传输区分，不出现厂商型号。</p>
 */
public enum DeviceTransport {

    /** BLE：连接、控制与随录随传的音频通道都走 GATT。 */
    BLE,

    /** 经典蓝牙：控制与音频走 SPP。 */
    CLASSIC
}
