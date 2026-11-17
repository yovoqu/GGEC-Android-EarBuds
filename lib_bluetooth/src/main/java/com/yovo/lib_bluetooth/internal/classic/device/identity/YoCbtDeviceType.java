package com.yovo.lib_bluetooth.internal.classic.device.identity;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 蓝牙设备类型枚举（经典、低功耗、双模等）。
 */

public enum YoCbtDeviceType {
    CLASSIC,
    LE,
    DUAL,
    UNKNOWN;

    /**
     * 已配对设备入库白名单的单点定义：经典与双模进入已配对快照，LE-only 不入库。
     *
     * <p>唯一的判定处在这里；loader 与 coordinator 只调用本方法，不再各自持有白名单集合。</p>
     */
    public boolean isBondedInventoryType() {
        return this == CLASSIC || this == DUAL;
    }
}
