package com.yovo.biz_ggec_hs.internal.center.catalog;

/**
 * 目录选项 id。目录用来生成可选项，能力路由按同一套 id 解码写入。
 */
public final class HsOptionIds {

    public static final String SOUND_QUALITY_HIGH = "high_quality";
    public static final String SOUND_QUALITY_ENDURANCE = "high_endurance";

    private HsOptionIds() {
    }

    /** {@code <耳类型>:<操作类型>}。 */
    public static String press(int earType, int actionType) {
        return earType + ":" + actionType;
    }

    /** {@code <耳侧>:<触击类型>:<功能码>}。 */
    public static String tap(int earSide, int tapType, int functionType) {
        return earSide + ":" + tapType + ":" + functionType;
    }
}
