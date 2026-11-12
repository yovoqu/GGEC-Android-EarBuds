package com.yovo.biz_ggec_hs.internal.center.port;

import java.util.List;
import java.util.UUID;

/**
 * 一帧原始广播：传输层（provider）交给协议层的唯一输入。
 *
 * <p>{@code rawAdvertisingView} 是只读视图，接收方不得就地写入；需要跨帧持有时自行复制。
 * 本类型不含任何蓝牙库类型，因此上层与中层都不需要认识 {@code lib_bluetooth}。</p>
 *
 * @param address            设备 BLE 地址
 * @param name               广播名（可能为空串）
 * @param rssi               本帧 RSSI
 * @param serviceUuids       广播声明的 128-bit Service UUID
 * @param rawAdvertisingView 原始广播字节（只读视图）
 * @param connectable        本帧是否声明可连接（用于「仅显示可连接」规则）
 * @param observedAtMillis   本帧的观测时刻（毫秒，与 {@link System#currentTimeMillis()} 同口径）
 */
public record HsRawAdvert(String address, String name, int rssi,
                          List<UUID> serviceUuids, byte[] rawAdvertisingView,
                          boolean connectable, long observedAtMillis) { }
