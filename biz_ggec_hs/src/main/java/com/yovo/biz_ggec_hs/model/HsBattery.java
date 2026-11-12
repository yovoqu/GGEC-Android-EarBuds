package com.yovo.biz_ggec_hs.model;

import androidx.annotation.NonNull;

/**
 * 耳机电量快照。三个电平是结构化 {@code int}，未知只用 {@link #UNKNOWN_LEVEL} 表达，
 * 模型里不再保留 {@code --} 这类协议文本形态。
 *
 * <p>协议上报的电平只有两种形态：十进制 {@code 1..100}，以及表示「设备没报」的 {@code --}。
 * 这两种形态在 {@link #fromProtocolText} 里一次转换成 {@code int}；第三种形态说明上游解析写错了，
 * 转换处直接抛 {@link IllegalArgumentException}，不在模型里猜一个默认值。</p>
 */
public final class HsBattery {

    /** 协议回 {@code --}（电平未知）。界面据此显示占位，不显示 0%。 */
    public static final int UNKNOWN_LEVEL = -1;

    /** 协议电平的文本形态：表示未知。 */
    private static final String UNKNOWN_LEVEL_TEXT = "--";

    private static final int MIN_LEVEL = 1;
    private static final int MAX_LEVEL = 100;

    /** 十进制电平最长三位（{@link #MAX_LEVEL} 是三位）。 */
    private static final int MAX_LEVEL_TEXT_LENGTH = 3;

    private final int leftLevel;
    private final int rightLevel;
    private final int caseLevel;
    private final boolean caseCharging;
    private final int earInCaseStatus;

    /**
     * 结构化构造：调用方已持有 {@code int} 电平（例如设备主动上报的十六进制字节），
     * 不需要再经过协议文本。
     *
     * @throws IllegalArgumentException 电平既不是 {@link #UNKNOWN_LEVEL}，也不在 {@code 1..100}
     */
    public HsBattery(int leftLevel,
                     int rightLevel,
                     int caseLevel,
                     boolean caseCharging,
                     int earInCaseStatus) {
        this.leftLevel = requireLevel(leftLevel, "左耳");
        this.rightLevel = requireLevel(rightLevel, "右耳");
        this.caseLevel = requireLevel(caseLevel, "充电盒");
        this.caseCharging = caseCharging;
        this.earInCaseStatus = earInCaseStatus;
    }

    /**
     * 协议文本的唯一解析入口：{@code --} → {@link #UNKNOWN_LEVEL}，十进制 → 值，
     * 其余（空串、非数字、越界、{@code null}）抛 {@link IllegalArgumentException}。
     *
     * <p>这是「解析只发生一次」的位置：缓存电量的域模型仍按协议原文保存，投影成
     * {@link HsBattery} 的这一刻把它变成结构值，下游只读 {@code int}。</p>
     */
    @NonNull
    public static HsBattery fromProtocolText(@NonNull String leftLevel,
                                            @NonNull String rightLevel,
                                            @NonNull String caseLevel,
                                            boolean caseCharging,
                                            int earInCaseStatus) {
        return new HsBattery(
                parseProtocolLevel(leftLevel, "左耳"),
                parseProtocolLevel(rightLevel, "右耳"),
                parseProtocolLevel(caseLevel, "充电盒"),
                caseCharging,
                earInCaseStatus);
    }

    /** 左耳电量，或 {@link #UNKNOWN_LEVEL}。 */
    public int leftLevel() {
        return leftLevel;
    }

    /** 右耳电量，或 {@link #UNKNOWN_LEVEL}。 */
    public int rightLevel() {
        return rightLevel;
    }

    /** 充电盒电量，或 {@link #UNKNOWN_LEVEL}。 */
    public int caseLevel() {
        return caseLevel;
    }

    public boolean caseCharging() {
        return caseCharging;
    }

    /**
     * 0 都不在盒内，1 仅左耳，2 仅右耳，3 都在盒内；{@code -1} 为未上报。
     */
    public int earInCaseStatus() {
        return earInCaseStatus;
    }

    private static int parseProtocolLevel(String text, String field) {
        if (text == null) {
            throw new IllegalArgumentException(field + "电平缺失");
        }
        if (UNKNOWN_LEVEL_TEXT.equals(text)) {
            return UNKNOWN_LEVEL;
        }
        if (!isDecimalLevelText(text)) {
            throw new IllegalArgumentException(
                    field + "电平不是协议形态（十进制 1..100 或 --）: " + text);
        }
        return Integer.parseInt(text);
    }

    private static boolean isDecimalLevelText(String text) {
        if (text.isEmpty() || text.length() > MAX_LEVEL_TEXT_LENGTH) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            char digit = text.charAt(index);
            if (digit < '0' || digit > '9') {
                return false;
            }
        }
        return true;
    }

    private static int requireLevel(int level, String field) {
        if (level != UNKNOWN_LEVEL && (level < MIN_LEVEL || level > MAX_LEVEL)) {
            throw new IllegalArgumentException(
                    field + "电平超出协议范围 " + MIN_LEVEL + ".." + MAX_LEVEL + ": " + level);
        }
        return level;
    }
}
