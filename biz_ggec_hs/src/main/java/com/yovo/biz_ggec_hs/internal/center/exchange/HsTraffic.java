package com.yovo.biz_ggec_hs.internal.center.exchange;

/**
 * 一条协议帧在链路上的收发角色。
 *
 * <p>命令定义类默认是 {@link #REQUEST_REPLY}：本端先写一帧，对端回一帧。
 * {@link #DEVICE_REPORT} 是设备主动上报，本端不回。判定只在
 * {@link HsFrameRoute}，调度器不另写一套前缀表。</p>
 */
public enum HsTraffic {
    /** 一发一收。对接链路的写入，应答从同一条通知里拆出来交给当前命令。 */
    REQUEST_REPLY,
    /** 设备主动上报。只进事件通道，不占用在途命令。 */
    DEVICE_REPORT
}
