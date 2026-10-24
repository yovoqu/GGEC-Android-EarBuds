package com.ggec.earbuds.ui.ai.common;

import java.util.Locale;

/**
 * 数字的显示格式。只出纯文本和数字，带单位的整句话由页面用字符串资源拼，这样可以单测也方便翻译。
 */
public final class AiFormat {

    private AiFormat() {
    }

    /**
     * 令牌数的紧凑写法。整千的按十进制写（128000 → "128K"），其余按二进制写（131072 → "128K"，8192 → "8K"），
     * 两种都是模型厂商标注上下文长度的习惯，这样显示出来的数和厂商文档里的一致。
     */
    public static String tokens(int count) {
        if (count >= 1_000_000) {
            return trim(count % 1_000_000 == 0 ? count / 1_000_000d : count / 1_048_576d) + "M";
        }
        if (count >= 1_000) {
            return trim(count % 1_000 == 0 ? count / 1_000d : count / 1_024d) + "K";
        }
        return String.valueOf(count);
    }

    /** 毫秒的紧凑写法：不到 1 秒写毫秒，之后写一位小数的秒。 */
    public static String duration(long millis) {
        if (millis < 1_000) {
            return Math.max(millis, 0) + " ms";
        }
        return String.format(Locale.ROOT, "%.1f s", millis / 1_000d);
    }

    /** 密钥只露出头尾，中间用圆点遮住。太短的密钥全部遮住，免得露出来的比遮住的还多。 */
    /**
     * 生成速率。只用能确证的口径：这段时间里出了多少 token。
     *
     * <p>没有首字时间，所以这是"整轮平均"，不是"生成阶段速率"。数据不足时返回 -1，
     * 界面据此不显示，而不是显示 0 或估算值。</p>
     */
    public static int tokensPerSecond(int outputTokens, long elapsedMillis) {
        if (outputTokens <= 0 || elapsedMillis < 300L) {
            return -1;
        }
        return (int) Math.round(outputTokens * 1000d / elapsedMillis);
    }

    public static String maskKey(String key) {
        if (key.length() <= 8) {
            return "••••••••";
        }
        return key.substring(0, 3) + "••••••••" + key.substring(key.length() - 4);
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
