package com.yovo.biz_ggec_hs.internal.center.protocol;

import java.util.List;

/** 双连接页。设备列表是协议记录。 */
public record ProtocolDual(boolean dualEnabled, String priorityAddress, List<ProtocolPaired> devices) {
}
