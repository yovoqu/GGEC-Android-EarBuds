package com.yovo.biz_ggec_hs.internal.center.port;

/**
 * 经典蓝牙事实和两个动作。不决定下一步，也不计时。
 */
public interface HsClassicLink {

    enum Bond {
        NONE, BONDING, BONDED
    }

    enum Audio {
        /** 音频 Profile 代理还没给出结论。 */
        UNKNOWN, DOWN, BUSY, UP
    }

    record Snapshot(Bond bond, Audio audio) {
    }

    void watch(String classicAddress, Listener listener);

    void unwatch();

    Snapshot read(String classicAddress);

    /** 栈拒绝这次请求时返回 false。已经接受、还在等结果时返回 true。 */
    boolean createBond(String classicAddress);

    /** 栈拒绝这次请求时返回 false。已经交给代理、还在等 Profile 时返回 true。 */
    boolean connectAudio(String classicAddress);

    void release();

    interface Listener {
        void onChanged();
    }
}
