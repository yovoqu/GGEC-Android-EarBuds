package com.ggec.earbuds.domain.device.model;

/**
 * 一条设备记录的状态来自哪里。
 *
 * <p>{@link #LIVE} 是活动会话的实时投影；{@link #ARCHIVED} 是落盘档案的最后已知值；
 * {@link #DISCOVERED} 是扫描到但还没进台账的设备。</p>
 */
public enum RecordOrigin {
    LIVE,
    ARCHIVED,
    DISCOVERED
}
