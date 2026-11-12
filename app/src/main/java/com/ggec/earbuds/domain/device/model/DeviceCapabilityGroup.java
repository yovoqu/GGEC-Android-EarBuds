package com.ggec.earbuds.domain.device.model;

/**
 * 能力分区：界面按该顺序生成 Section。
 *
 * <p>取值与 {@code biz_ggec_hs} 的描述符分组一一对应，映射单点在
 * {@code provider/biz_ggec_hs/HsValueMapper}。APP 不派生分组，也不按能力名推断。</p>
 */
public enum DeviceCapabilityGroup {
    MUSIC,
    CONTROL,
    FUNCTION,
    DUAL_CONNECTION,
    RECORD,
    ABOUT
}
