package com.yovo.biz_ggec_hs.internal.center.protocol;

/**
 * 固件、耳机颜色、在仓字节。未上报的字段是 null，不在这里填对外的未知占位。
 */
public record ProtocolInfo(String firmware, Integer earColor, Integer earInCase) {
}
