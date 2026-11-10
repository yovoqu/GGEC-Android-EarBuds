package com.yovo.biz_ggec_hs.model.broadcast;

/**
 * HS 广播里的市场号。HS01 由特征码映射，HS02 由厂商数据 TLV 给出。
 */
public final class HsMarket {
    public static final int CN = 0;
    public static final int TVS = 1;
    public static final int AUSTRALIA = 2;
    public static final int CN2 = 3;
    public static final int UNKNOWN = -1;

    private HsMarket() {
    }
}
