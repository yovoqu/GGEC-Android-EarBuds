package com.yovo.biz_ggec_hs.internal.center.protocol;

/**
 * 一帧配对列表里的一台设备。字节已经按协议解成枚举，还不是对外契约。
 */
public record ProtocolPaired(String name, String address, Kind kind, boolean connected,
                             Activity activity, boolean currentDevice, boolean priority) {

    public enum Kind {
        OTHER, PHONE, COMPUTER, PAD, TV, WEARABLE
    }

    public enum Activity {
        IDLE, MUSIC, CALL
    }
}
