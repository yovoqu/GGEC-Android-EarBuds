package com.yovo.lib_bluetooth.internal.classic.service.model;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SDP 解析结果的来源枚举。
 */
public enum YoCbtSdpSource {
    /** 新鲜的 {@code fetchUuidsWithSdp()} + {@code ACTION_UUID} 事务。 */
    LIVE_SDP,
    /** {@link android.bluetooth.BluetoothDevice#getUuids()} 绑定缓存。 */
    BONDED_CACHE,
    /** 先前存储的 {@link YoCbtServiceCache} 条目。 */
    CACHE_FALLBACK,
    /** 用户或厂商提供、未经 Android SDP 的服务记录。 */
    CONFIG_INJECTED
}
